package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.notification.NotificationType;
import br.com.ctcore.notification.Notifications;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileKind;
import br.com.ctcore.storage.FileStorage;
import br.com.ctcore.student.PortalTerms;
import br.com.ctcore.student.Students;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COB-05 (RN-COB-13, RN-COB-19): comprovante de Pix na chave da unidade. Quem paga ({@link PortalTerms#requirePayer}) envia o arquivo e a cobrança vai a
 * "Em conferência"; a gestão confirma (vira pagamento com recibo, pelo mesmo miolo do balcão) ou recusa com motivo (a cobrança volta a aberta e os dias em
 * conferência adiam o bloqueio, D5). A chave do arquivo nunca vai para auditoria nem para o portal.
 */
@Service
public class PaymentProofs {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentProofs.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    static final String PREFIX = "payment-proof-";
    private static final int MAX_BATCH = 20;

    public record Instructions(UUID chargeId, String pixKey, long amountCents, LocalDate dueOn, String unit) {}

    public record BatchItem(UUID chargeId, String studentName, long amountCents, LocalDate dueOn) {}

    public record BatchInstructions(String pixKey, long totalCents, String unit, List<BatchItem> charges) {}

    public record Sent(UUID id, UUID chargeId, String status, Instant sentAt) {}

    public record Proof(UUID id, UUID chargeId, UUID studentId, String studentName, String studentPhotoUrl, UUID unitId, String unit, String reference, long amountCents,
            LocalDate dueOn, Instant sentAt, String status, String contentType, String imageUrl, String refusalReason, Instant decidedAt) {}

    public record ProofList(List<Proof> items, int page, int size, long total) {}

    public record Confirmed(Proof proof, Payments.Registered payment) {}

    private static final String SELECT = "select p.id, p.charge_id, s.id, s.name, s.photo, u.id, u.name, coalesce(c.description, to_char(c.reference_month, 'MM/YYYY'), 'cobrança'), "
            + "c.amount_cents, c.due_on, p.sent_at, p.status, p.content_type, p.file_key, p.refusal_reason, p.decided_at "
            + "from payment_proof p join charge c on c.organization_id = p.organization_id and c.id = p.charge_id "
            + "join student s on s.organization_id = p.organization_id and s.id = p.student_id "
            + "join unit u on u.organization_id = p.organization_id and u.id = p.unit_id ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final PortalTerms terms;
    private final Payments payments;
    private final Students students;
    private final FileStorage storage;
    private final AuditService audit;
    private final Notifications notifications;
    private final EmailService email;
    private final MessageSource messages;
    private final Clock clock;

    public PaymentProofs(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, PortalTerms terms, Payments payments,
            Students students, FileStorage storage, AuditService audit, Notifications notifications, EmailService email, MessageSource messages, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.terms = terms;
        this.payments = payments;
        this.students = students;
        this.storage = storage;
        this.audit = audit;
        this.notifications = notifications;
        this.email = email;
        this.messages = messages;
        this.clock = clock;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static ApiStatusException rule(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    private static ApiStatusException rule(String message) {
        return rule("file", message);
    }

    // ---- portal (aluno adulto e responsável financeiro) ----

    private record Charge(UUID id, UUID unitId, UUID studentId, long amountCents, LocalDate dueOn, String status, String studentName, String unit, String pixKey) {}

    /** Cobrança travada (se {@code lock}), com a chave Pix da unidade; 404 se não existe na organização. Dentro da transação. */
    private Charge charge(UUID org, UUID chargeId, boolean lock) {
        return jdbc.sql("select c.id, c.unit_id, c.student_id, c.amount_cents, c.due_on, c.status, s.name, u.name, us.pix_key from charge c "
                + "join student s on s.organization_id = c.organization_id and s.id = c.student_id "
                + "join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
                + "left join unit_settings us on us.organization_id = c.organization_id and us.unit_id = c.unit_id "
                + "where c.id = :id and c.organization_id = :org" + (lock ? " for update of c" : ""))
                .param("id", chargeId).param("org", org)
                .query((rs, n) -> new Charge(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getLong(4), rs.getObject(5, LocalDate.class),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    /** Dentro da transação: 404 fora do vínculo, 403 sem direito de pagar, 409 fora de aberta, 422 sem chave Pix. */
    private Charge payable(AccountPrincipal who, UUID chargeId, boolean lock) {
        Charge c = charge(who.organizationId(), chargeId, lock);
        terms.requirePayer(who, c.studentId());
        if (!"OPEN".equals(c.status())) throw new ApiStatusException(ErrorCode.CHARGE_NOT_PAYABLE);
        if (c.pixKey() == null) throw new ApiStatusException(ErrorCode.PIX_KEY_MISSING);
        return c;
    }

    /** RN-COB-13: 1 a 20 cobranças sem repetição, em ordem de id (a trava sempre na mesma ordem não trava em cruz). */
    private static List<UUID> selection(List<UUID> ids, String field) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_BATCH || new HashSet<>(ids).size() != ids.size()) throw rule(field, "de 1 a " + MAX_BATCH + " cobranças, sem repetir");
        return ids.stream().sorted().toList();
    }

    /** Dentro da transação: cada cobrança como em {@link #payable}, todas da mesma unidade (senão 422 em {@code field}). */
    private List<Charge> payableAll(AccountPrincipal who, List<UUID> sorted, boolean lock, String field) {
        List<Charge> all = sorted.stream().map(id -> payable(who, id, lock)).toList();
        if (all.stream().map(Charge::unitId).distinct().count() > 1) throw rule(field, "cobranças de unidades diferentes");
        return all;
    }

    public Instructions instructions(AccountPrincipal who, UUID chargeId) {
        terms.requireAccepted(who);
        return transactions.execute(() -> {
            Charge c = payable(who, chargeId, false);
            return new Instructions(chargeId, c.pixKey(), c.amountCents(), c.dueOn(), c.unit());
        });
    }

    public BatchInstructions instructions(AccountPrincipal who, List<UUID> ids) {
        terms.requireAccepted(who);
        List<UUID> sorted = selection(ids, "ids");
        return transactions.execute(() -> {
            List<Charge> all = payableAll(who, sorted, false, "ids");
            return new BatchInstructions(all.get(0).pixKey(), all.stream().mapToLong(Charge::amountCents).sum(), all.get(0).unit(),
                    all.stream().map(c -> new BatchItem(c.id(), c.studentName(), c.amountCents(), c.dueOn())).toList());
        });
    }

    private record Saved(Sent sent, UUID unitId, String studentName, long amountCents) {}

    public Sent send(AccountPrincipal who, UUID chargeId, byte[] content, String ip) {
        return saveAll(who, List.of(chargeId), content, ip, "chargeIds").get(0).sent();
    }

    /** Um arquivo, um comprovante por cobrança, todas em conferência ou nenhuma; auditoria e notificação de cada um. */
    public List<Sent> send(AccountPrincipal who, List<UUID> chargeIds, byte[] content, String ip) {
        return saveAll(who, selection(chargeIds, "chargeIds"), content, ip, "chargeIds").stream().map(Saved::sent).toList();
    }

    private List<Saved> saveAll(AccountPrincipal who, List<UUID> sorted, byte[] content, String ip, String field) {
        terms.requireAccepted(who);
        if (content == null || content.length == 0) throw rule("obrigatório");
        if (content.length > FileStorage.MAX_BYTES) throw rule("arquivo acima de 5 MB");
        FileKind kind = FileKind.of(content);
        if (kind == null) throw rule("use JPG, PNG, WebP ou PDF");
        List<Saved> saved = transactions.execute(() -> {
            // A trava das cobranças faz a segunda chamada esperar e ver "Em conferência" (R2).
            List<Charge> all = payableAll(who, sorted, true, field);
            String key = PREFIX + all.get(0).studentId() + "-" + UUID.randomUUID() + "." + kind.extension();
            storage.put(key, content, kind.contentType());
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            List<Saved> out = new ArrayList<>();
            for (Charge c : all) {
                UUID id = UUID.randomUUID();
                jdbc.sql("insert into payment_proof (id, organization_id, charge_id, student_id, unit_id, file_key, content_type, sent_by_account_id, sent_at) "
                        + "values (:id, :org, :charge, :student, :unit, :key, :type, :by, :at)")
                        .param("id", id).param("org", who.organizationId()).param("charge", c.id()).param("student", c.studentId()).param("unit", c.unitId())
                        .param("key", key).param("type", kind.contentType()).param("by", who.accountId()).param("at", now).update();
                jdbc.sql("update charge set status = 'IN_REVIEW' where id = :id").param("id", c.id()).update();
                // A chave do arquivo não vai para a auditoria (RN-LGP): só o fato do envio.
                audit.record(new AuditEntry(actor(who), "payment_proof.sent", "payment_proof", id.toString(), c.unitId(), Map.of("chargeStatus", "OPEN"),
                        Map.of("chargeId", c.id().toString(), "chargeStatus", "IN_REVIEW", "status", "PENDING", "type", kind.contentType()), null, ip));
                out.add(new Saved(new Sent(id, c.id(), "PENDING", now.toInstant()), c.unitId(), c.studentName(), c.amountCents()));
            }
            return out;
        });
        // RN-NOT-04: depois do commit; a notificação nunca desfaz o envio (falha engolida em publish).
        for (Saved s : saved) {
            notifications.publish(who.organizationId(), s.unitId(), NotificationType.PAYMENT_PROOF, "payment_proof", s.sent().id(),
                    Map.of("studentName", s.studentName(), "amountCents", s.amountCents()));
        }
        return saved;
    }

    // ---- gestão (cobranças completo na unidade) ----

    private void requireManager(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    private Proof map(ResultSet rs) throws SQLException {
        String image = null;
        try {
            image = storage.readUrl(rs.getString(14));
        } catch (FileStorage.NotFound e) {
            LOG.warn("Payment proof file is missing from storage");
        }
        OffsetDateTime decided = rs.getObject(16, OffsetDateTime.class);
        return new Proof(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4), students.photoUrl(rs.getString(5)),
                rs.getObject(6, UUID.class), rs.getString(7), rs.getString(8), rs.getLong(9), rs.getObject(10, LocalDate.class), rs.getObject(11, OffsetDateTime.class).toInstant(),
                rs.getString(12), rs.getString(13), image, rs.getString(15), decided == null ? null : decided.toInstant());
    }

    /** Pendentes, do mais antigo ao mais novo; Administrador vê o alcance dele e Coordenador todas as unidades. */
    public ProofList list(AccountPrincipal who, UUID unitId, PageRequest page) {
        requireManager(who);
        return transactions.execute(() -> {
            boolean admin = who.role() == Role.ADMINISTRATOR;
            Set<UUID> reach = who.units() == null ? Set.of() : who.units();
            if (admin && (reach.isEmpty() || unitId != null && !reach.contains(unitId))) return new ProofList(List.of(), page.page(), page.size(), 0);
            Map<String, Object> params = new HashMap<>();
            params.put("org", who.organizationId());
            StringBuilder where = new StringBuilder("where p.organization_id = :org and p.status = 'PENDING'");
            if (admin) {
                where.append(" and p.unit_id in (:reach)");
                params.put("reach", reach);
            }
            if (unitId != null) {
                where.append(" and p.unit_id = :unit");
                params.put("unit", unitId);
            }
            long total = jdbc.sql("select count(*) from payment_proof p " + where).params(params).query(Long.class).single();
            params.put("limit", page.size());
            params.put("offset", (page.page() - 1) * page.size());
            List<Proof> items = jdbc.sql(SELECT + where + " order by p.sent_at, p.id limit :limit offset :offset").params(params).query((rs, n) -> map(rs)).list();
            return new ProofList(items, page.page(), page.size(), total);
        });
    }

    /** Dentro da transação: 404 fora do alcance ou da organização. */
    private Proof one(AccountPrincipal who, UUID id) {
        Proof proof = jdbc.sql(SELECT + "where p.organization_id = :org and p.id = :id").param("org", who.organizationId()).param("id", id)
                .query((rs, n) -> map(rs)).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        if (permissions.grant(who, Permission.COLLECTIONS, proof.unitId()) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return proof;
    }

    public Proof get(AccountPrincipal who, UUID id) {
        requireManager(who);
        return transactions.execute(() -> one(who, id));
    }

    /** Trava o comprovante; 404 se fora do alcance, 409 se já decidido. Dentro da transação. */
    private Proof pending(AccountPrincipal who, UUID id) {
        jdbc.sql("select 1 from payment_proof where organization_id = :org and id = :id for update").param("org", who.organizationId()).param("id", id)
                .query(Integer.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        Proof proof = one(who, id);
        if (!"PENDING".equals(proof.status())) throw new ApiStatusException(ErrorCode.PROOF_NOT_PENDING);
        return proof;
    }

    private record Confirmation(Payments.Done payment, Proof proof) {}

    public Confirmed confirm(AccountPrincipal who, UUID id, boolean sendReceipt, String ip) {
        requireManager(who);
        Confirmation done = transactions.execute(() -> {
            Proof proof = pending(who, id);
            // D4: o pagamento vale na data do envio do comprovante; o contador de recibo e a auditoria do pagamento são os do balcão.
            Payments.Done paid = payments.settle(who, proof.chargeId(), "IN_REVIEW", "PIX_KEY", "PORTAL", OffsetDateTime.ofInstant(proof.sentAt(), ZoneOffset.UTC), null, null,
                    sendReceipt, ip);
            jdbc.sql("update payment_proof set status = 'CONFIRMED', decided_at = :now, decided_by_account_id = :by, payment_id = :payment where id = :id")
                    .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).param("by", who.accountId()).param("payment", paid.registered().id())
                    .param("id", id).update();
            audit.record(new AuditEntry(actor(who), "payment_proof.confirmed", "payment_proof", id.toString(), proof.unitId(), Map.of("status", "PENDING"),
                    Map.of("chargeId", proof.chargeId().toString(), "status", "CONFIRMED", "paymentId", paid.registered().id().toString()), null, ip));
            return new Confirmation(paid, one(who, id));
        });
        return new Confirmed(done.proof(), payments.finish(who, done.payment(), sendReceipt));
    }

    private record Refusal(Proof proof, String to) {}

    public Proof refuse(AccountPrincipal who, UUID id, String reason, String ip) {
        requireManager(who);
        String text = reason == null ? "" : reason.strip();
        if (text.length() < 3 || text.length() > 200) throw new ApiValidationException("reason", "de 3 a 200 caracteres");
        Refusal done = transactions.execute(() -> {
            Proof proof = pending(who, id);
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            // D5: dias pela data local da unidade, do envio à recusa; a cobrança volta a aberta e o bloqueio anda esses dias.
            int days = jdbc.sql("select (cast(:now as timestamptz) at time zone u.timezone)::date - (p.sent_at at time zone u.timezone)::date "
                    + "from payment_proof p join unit u on u.organization_id = p.organization_id and u.id = p.unit_id where p.id = :id")
                    .param("now", now).param("id", id).query(Integer.class).single();
            days = Math.max(days, 0);
            jdbc.sql("select 1 from charge where id = :id and organization_id = :org for update").param("id", proof.chargeId()).param("org", who.organizationId())
                    .query(Integer.class).single();
            jdbc.sql("update charge set status = 'OPEN', block_pause_days = block_pause_days + :days where id = :id").param("days", days)
                    .param("id", proof.chargeId()).update();
            jdbc.sql("update payment_proof set status = 'REFUSED', decided_at = :now, decided_by_account_id = :by, refusal_reason = :reason where id = :id")
                    .param("now", now).param("by", who.accountId()).param("reason", text).param("id", id).update();
            audit.record(new AuditEntry(actor(who), "payment_proof.refused", "payment_proof", id.toString(), proof.unitId(), Map.of("status", "PENDING"),
                    Map.of("chargeId", proof.chargeId().toString(), "chargeStatus", "OPEN", "status", "REFUSED", "pausedDays", days), text, ip));
            String to = jdbc.sql("select a.email from payment_proof p join account a on a.id = p.sent_by_account_id where p.id = :id").param("id", id)
                    .query(String.class).optional().orElse(null);
            return new Refusal(one(who, id), to);
        });
        mailRefused(who.organizationId(), done.to(), done.proof().reference(), text);
        return done.proof();
    }

    /** RN-COM-11: aviso ao aluno com o motivo; falha do envio nunca desfaz a recusa. Depois do commit. */
    private void mailRefused(UUID org, String to, String reference, String reason) {
        if (to == null) return;
        try {
            email.sendText(org, to, EmailTemplate.PROOF_REFUSED, messages.getMessage("mail.proof-refused.subject", null, PT_BR),
                    messages.getMessage("mail.proof-refused.body", new Object[] {reference, reason}, PT_BR));
        } catch (RuntimeException e) {
            LOG.warn("payment proof refusal e-mail not sent cause={}", e.getClass().getSimpleName());
        }
    }
}
