package br.com.ctcore.billing;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-COB-02: painel da cobrança, cancelar (RN-COB-10) e renegociar (RN-COB-09). Só COLLECTIONS FULL na unidade da cobrança; as linhas envolvidas são travadas
 * ({@code for update}) e cada pedido é tudo ou nada. Cobrança paga, estornada, renegociada ou em conferência nunca muda (RN-COB-08).
 */
@Service
public class ChargeActions {
    static final int MAX_REASON = 200;
    static final int MAX_INSTALLMENTS = 6;

    public record Ref(UUID id, String type, LocalDate referenceMonth, LocalDate dueOn, long amountCents, String status) {}

    public record Named(UUID id, String name) {}

    public record Composition(Named plan, long planCents, String benefitType, long benefitCents, long amountCents) {}

    public record Payer(UUID id, String name, String kind) {}

    public record Receipt(int number, String code) {}

    public record PaymentView(UUID id, long amountCents, String method, String origin, OffsetDateTime paidAt, Named registeredBy, String observation, String status,
            Receipt receipt) {}

    public record Actions(boolean cancel, boolean renegotiate, boolean registerPayment, boolean sendReminder, boolean refund) {}

    /** D1: {@code reminders} fica vazio até a #79. */
    public record Detail(Charges.Row charge, int toleranceDays, Composition composition, Payer payer, Ref origin, List<Ref> replacedBy, String reason, OffsetDateTime canceledAt,
            Named canceledBy, List<PaymentView> payments, List<Object> reminders, Actions actions) {}

    public record RenegotiateInput(List<UUID> chargeIds, Long amountCents, String firstDueOn, Integer installments, String reason) {}

    private record Locked(UUID id, UUID unit, UUID student, UUID payer, UUID modalityRow, LocalDate reference, LocalDate dueOn, long amount, String status) {}

    private record Extra(int tolerance, String planName, UUID planId, long planCents, String benefitType, long benefitCents, UUID payerGuardian, String payerName,
            UUID origin, String reason, OffsetDateTime canceledAt, UUID canceledBy, String canceledByName, String status, UUID renegotiation) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final Charges charges;
    private final Clock clock;

    public ChargeActions(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, Charges charges, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.charges = charges;
        this.clock = clock;
    }

    private void requireCollections(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    private void requireFull(AccountPrincipal who, UUID unit) {
        if (permissions.grant(who, Permission.COLLECTIONS, unit) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    /** R5: motivo obrigatório, sem espaços nas pontas, até 200 caracteres. */
    public static String reason(String raw) {
        String reason = raw == null ? "" : raw.strip();
        if (reason.isEmpty()) throw new ApiValidationException("reason", "obrigatório");
        if (reason.length() > MAX_REASON) throw new ApiValidationException("reason", "até " + MAX_REASON + " caracteres");
        return reason;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    /** R1: o painel; 404 para cobrança inexistente, de outra organização ou de unidade fora do alcance. */
    public Detail detail(AccountPrincipal who, UUID id) {
        requireCollections(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            UUID unit = jdbc.sql("select unit_id from charge where organization_id = :org and id = :id").param("org", org).param("id", id).query(UUID.class).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            requireFull(who, unit);
            return read(org, id);
        });
    }

    private Detail read(UUID org, UUID id) {
        Charges.Row row = charges.one(org, id);
        Extra x = jdbc.sql("select c.tolerance_days, pl.name, c.plan_id, c.plan_cents, b.type, c.benefit_cents, c.payer_guardian_id, g.name, c.origin_charge_id, c.reason, "
                + "c.canceled_at, c.canceled_by_account_id, a.name, c.status, c.renegotiation_id from charge c left join plan pl on pl.id = c.plan_id "
                + "left join benefit b on b.id = c.benefit_id left join guardian g on g.id = c.payer_guardian_id left join account a on a.id = c.canceled_by_account_id "
                + "where c.organization_id = :org and c.id = :id").param("org", org).param("id", id)
                .query((rs, n) -> new Extra(rs.getInt(1), rs.getString(2), rs.getObject(3, UUID.class), rs.getLong(4), rs.getString(5), rs.getLong(6),
                        rs.getObject(7, UUID.class), rs.getString(8), rs.getObject(9, UUID.class), rs.getString(10), rs.getObject(11, OffsetDateTime.class),
                        rs.getObject(12, UUID.class), rs.getString(13), rs.getString(14), rs.getObject(15, UUID.class))).single();
        Payer payer = x.payerGuardian() == null ? new Payer(row.student().id(), row.student().name(), "STUDENT") : new Payer(x.payerGuardian(), x.payerName(), "GUARDIAN");
        String refSql = "select id, type, reference_month, due_on, amount_cents, status from charge where organization_id = :org and ";
        Ref origin = x.origin() == null ? null : jdbc.sql(refSql + "id = :id").param("org", org).param("id", x.origin()).query(ChargeActions::ref).optional().orElse(null);
        // ponytail: uma parcela renegociada de novo perde o vínculo do grupo anterior (um só renegotiation_id); a origem segue pela cobrança de origem.
        List<Ref> replaced = !"RENEGOTIATED".equals(x.status()) || x.renegotiation() == null ? List.of()
                : jdbc.sql(refSql + "renegotiation_id = :r and id <> :id and origin = 'RENEGOTIATION' and status <> 'RENEGOTIATED' order by due_on, id")
                        .param("org", org).param("r", x.renegotiation()).param("id", id).query(ChargeActions::ref).list();
        List<PaymentView> payments = jdbc.sql("select p.id, p.amount_cents, p.method, p.origin, p.paid_at, p.registered_by_account_id, a.name, p.observation, p.status, r.number, r.code "
                + "from payment p left join receipt r on r.payment_id = p.id left join account a on a.id = p.registered_by_account_id "
                + "where p.organization_id = :org and p.charge_id = :id order by p.paid_at, p.id").param("org", org).param("id", id)
                .query((rs, n) -> new PaymentView(rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class),
                        rs.getObject(6, UUID.class) == null ? null : new Named(rs.getObject(6, UUID.class), rs.getString(7)), rs.getString(8), rs.getString(9),
                        rs.getObject(10) == null ? null : new Receipt(rs.getInt(10), rs.getString(11)))).list();
        boolean open = "OPEN".equals(x.status());
        return new Detail(row, x.tolerance(), new Composition(x.planId() == null ? null : new Named(x.planId(), x.planName()), x.planCents(), x.benefitType(), x.benefitCents(),
                row.amountCents()), payer, origin, replaced, x.reason(), x.canceledAt(),
                x.canceledBy() == null ? null : new Named(x.canceledBy(), x.canceledByName()), payments, List.of(), new Actions(open, open, open, false, false));
    }

    private static Ref ref(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Ref(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getLong(5), rs.getString(6));
    }

    /** R4 a R6, R9, R10. */
    public Detail cancel(AccountPrincipal who, UUID id, String reason, String ip) {
        requireCollections(who);
        String why = reason(reason);
        return transactions.execute(() -> {
            cancelLocked(who, id, why, ip);
            return read(who.organizationId(), id);
        });
    }

    /** Cancela dentro da transação em curso (também usada ao encerrar a modalidade). Devolve falso quando já estava cancelada (sem efeito, sem auditoria). */
    public boolean cancelLocked(AccountPrincipal who, UUID id, String reason, String ip) {
        Locked c = lock(who.organizationId(), List.of(id)).stream().findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        requireFull(who, c.unit());
        if ("CANCELED".equals(c.status())) return false;
        if (!"OPEN".equals(c.status())) throw new ApiStatusException(ErrorCode.CONFLICT);
        jdbc.sql("update charge set status = 'CANCELED', reason = :reason, canceled_at = :now, canceled_by_account_id = :by where id = :id")
                .param("reason", reason).param("now", OffsetDateTime.now(clock)).param("by", who.accountId()).param("id", id).update();
        audit.record(new AuditEntry(actor(who), "charge.canceled", "charge", id.toString(), c.unit(), snapshot(c),
                Map.of("status", "CANCELED", "amountCents", c.amount(), "dueOn", c.dueOn().toString()), reason, ip));
        return true;
    }

    private static Map<String, Object> snapshot(Locked c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", c.status());
        m.put("amountCents", c.amount());
        m.put("dueOn", c.dueOn().toString());
        return m;
    }

    private List<Locked> lock(UUID org, List<UUID> ids) {
        return jdbc.sql("select id, unit_id, student_id, payer_guardian_id, enrollment_modality_id, reference_month, due_on, amount_cents, status from charge "
                + "where organization_id = :org and id in (:ids) order by id for update").param("org", org).param("ids", ids)
                .query((rs, n) -> new Locked(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getObject(5, UUID.class), rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class), rs.getLong(8), rs.getString(9))).list();
    }

    /** R7 a R10 (D4): as originais viram Renegociadas e nascem as parcelas; devolve as linhas novas, no formato da lista. */
    public List<Charges.Row> renegotiate(AccountPrincipal who, RenegotiateInput in, String ip) {
        requireCollections(who);
        String why = reason(in == null ? null : in.reason());
        if (in.chargeIds() == null || in.chargeIds().isEmpty() || in.chargeIds().contains(null) || new HashSet<>(in.chargeIds()).size() != in.chargeIds().size()) {
            throw new ApiValidationException("chargeIds", "uma ou mais cobranças, sem repetir");
        }
        int n = in.installments() == null ? 0 : in.installments();
        if (n < 1 || n > MAX_INSTALLMENTS) throw new ApiValidationException("installments", "de 1 a " + MAX_INSTALLMENTS);
        LocalDate first;
        try {
            first = LocalDate.parse(in.firstDueOn());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ApiValidationException("firstDueOn", "data inválida");
        }
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            List<Locked> found = lock(org, in.chargeIds());
            if (found.size() != in.chargeIds().size()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            for (Locked c : found) requireFull(who, c.unit());
            Locked head = found.stream().min(Comparator.comparing(Locked::dueOn).thenComparing(Locked::id)).orElseThrow();
            if (found.stream().anyMatch(c -> !c.unit().equals(head.unit()) || !c.student().equals(head.student()))) {
                throw new ApiValidationException("chargeIds", "do mesmo aluno e da mesma unidade");
            }
            long sum = found.stream().mapToLong(Locked::amount).sum();
            if (in.amountCents() != null && found.size() > 1 && in.amountCents() != sum) throw new ApiValidationException("amountCents", "só muda com uma cobrança");
            long total = in.amountCents() == null ? sum : in.amountCents();
            if (total <= 0) throw new ApiValidationException("amountCents", "maior que zero");
            if (total < n) throw new ApiValidationException("installments", "ao menos 1 centavo por parcela");
            String zone = jdbc.sql("select timezone from unit where id = :id").param("id", head.unit()).query(String.class).single();
            if (first.isBefore(LocalDate.now(clock.withZone(ZoneId.of(zone))))) throw new ApiValidationException("firstDueOn", "não pode ser anterior a hoje");
            if (found.stream().anyMatch(c -> !"OPEN".equals(c.status()))) throw new ApiStatusException(ErrorCode.CONFLICT);

            UUID group = UUID.randomUUID();
            for (Locked c : found) {
                jdbc.sql("update charge set status = 'RENEGOTIATED', reason = :reason, renegotiation_id = :g where id = :id").param("reason", why).param("g", group)
                        .param("id", c.id()).update();
            }
            long base = total / n, rest = total % n;
            List<UUID> created = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                UUID id = UUID.randomUUID();
                created.add(id);
                jdbc.sql("insert into charge (id, organization_id, unit_id, student_id, payer_guardian_id, type, enrollment_modality_id, reference_month, due_on, amount_cents, "
                        + "origin, origin_charge_id, renegotiation_id, created_by_account_id) values (:id, :org, :unit, :student, :payer, 'INSTALLMENT', :row, :ref, :due, "
                        + ":amount, 'RENEGOTIATION', :origin, :g, :by)").param("id", id).param("org", org).param("unit", head.unit()).param("student", head.student())
                        .param("payer", head.payer()).param("row", head.modalityRow()).param("ref", head.reference()).param("due", first.plusMonths(i))
                        .param("amount", i == 0 ? base + rest : base).param("origin", head.id()).param("g", group).param("by", who.accountId()).update();
            }
            audit.record(new AuditEntry(actor(who), "charge.renegotiated", "charge", head.id().toString(), head.unit(), snapshot(head),
                    Map.of("status", "RENEGOTIATED", "amountCents", total, "dueOn", first.toString(), "originalIds", found.stream().map(c -> c.id().toString()).toList(),
                            "newIds", created.stream().map(UUID::toString).toList(), "installments", n), why, ip));
            return created.stream().map(id -> charges.one(org, id)).toList();
        });
    }
}
