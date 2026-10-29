package br.com.ctcore.student;

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
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.springframework.stereotype.Service;

/**
 * RF-MAT-04 (RN-MAT-16, RN-MAT-18): pedidos do portal (troca de turma, nova modalidade, troca de plano e encerramento) e a resposta da gestão. Um pedido
 * nunca altera a matrícula sozinho. Quem pede é quem aceita o termo ({@link PortalTerms#acceptable}); a mensagem livre nunca vai para auditoria nem notificação.
 */
@Service
public class PortalRequests {
    private static final Logger LOG = LoggerFactory.getLogger(PortalRequests.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");
    static final Set<String> KINDS = Set.of("CLASS_CHANGE", "NEW_MODALITY", "PLAN_CHANGE", "CLOSURE");
    private static final Set<String> STATUSES = Set.of("OPEN", "APPROVED", "REFUSED", "EXPIRED", "CLOSED");
    private static final Set<String> REASONS = Set.of("CITY_CHANGE", "FINANCIAL", "SCHEDULE", "INJURY", "DISSATISFACTION", "OTHER");

    public record Input(String kind, UUID modalityRowId, UUID targetClassId, UUID targetPlanId, UUID targetUnitId, UUID targetModalityId, String reason, String note) {}

    public record Request(UUID id, String kind, String status, UUID studentId, String studentName, UUID unitId, String unit, UUID modalityRowId, String modality,
            UUID targetClassId, String targetClass, UUID targetPlanId, String targetPlan, UUID targetModalityId, UUID targetUnitId, String targetUnit, String reason,
            String note, String response, Instant createdAt, Instant decidedAt) {}

    public record Choice(UUID id, String name) {}

    public record PlanChoice(UUID id, String name, long valueCents) {}

    public record ModalityOption(UUID modalityRowId, UUID modalityId, String modality, UUID unitId, String unit, Choice currentClass, Choice currentPlan,
            Instant closureRequestedAt, List<Choice> classes, List<PlanChoice> plans) {}

    public record Offer(UUID unitId, String unit, UUID modalityId, String modality) {}

    public record Options(List<ModalityOption> modalities, List<Offer> offers) {}

    public record RequestList(List<Request> items, int page, int size, long total) {}

    /** O pedido gravado e o e-mail da conta que pediu, lidos dentro da transação para o aviso depois do commit. */
    record Saved(Request request, String requesterEmail) {}

    private static final String SELECT = "select r.id, r.kind, r.status, r.student_id, s.name, r.unit_id, u.name, r.enrollment_modality_id, md.name, r.target_class_id, tc.name, "
            + "r.target_plan_id, tp.name, r.target_modality_id, r.target_unit_id, tu.name, r.reason, r.note, r.response, r.created_at, r.decided_at "
            + "from portal_request r join student s on s.organization_id = r.organization_id and s.id = r.student_id "
            + "join unit u on u.organization_id = r.organization_id and u.id = r.unit_id "
            + "left join enrollment_modality em on em.organization_id = r.organization_id and em.id = r.enrollment_modality_id "
            + "left join modality md on md.id = coalesce(em.modality_id, r.target_modality_id) "
            + "left join training_class tc on tc.organization_id = r.organization_id and tc.id = r.target_class_id "
            + "left join plan tp on tp.organization_id = r.organization_id and tp.id = r.target_plan_id "
            + "left join unit tu on tu.organization_id = r.organization_id and tu.id = r.target_unit_id ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalTerms terms;
    private final Students students;
    private final AuditService audit;
    private final Notifications notifications;
    private final EmailService email;
    private final MessageSource messages;
    private final Clock clock;

    public PortalRequests(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalTerms terms, Students students, AuditService audit,
            Notifications notifications, EmailService email, MessageSource messages, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.terms = terms;
        this.students = students;
        this.audit = audit;
        this.notifications = notifications;
        this.email = email;
        this.messages = messages;
        this.clock = clock;
    }

    // ---- portal (aluno adulto e responsável financeiro) ----

    /** Pedidos do aluno, do mais novo ao mais antigo. Qualquer responsável ligado lê; aluno não ligado à conta, 404. */
    public List<Request> list(AccountPrincipal who, UUID studentId) {
        terms.requireAccepted(who);
        return transactions.execute(() -> {
            linked(who, studentId);
            return jdbc.sql(SELECT + "where r.organization_id = :org and r.student_id = :s order by r.created_at desc, r.id")
                    .param("org", who.organizationId()).param("s", studentId).query((rs, n) -> map(rs)).list();
        });
    }

    /** O que o aluno pode pedir: modalidades ativas com turmas e planos válidos, e as ofertas (unidade e modalidade) que ele ainda não tem. */
    public Options options(AccountPrincipal who, UUID studentId) {
        terms.requireAccepted(who);
        return transactions.execute(() -> {
            linked(who, studentId);
            UUID org = who.organizationId();
            var rows = jdbc.sql("select em.id, em.modality_id, md.name, em.unit_id, u.name, c.id, c.name, p.id, p.name, "
                    + "(select min(r.created_at) from portal_request r where r.organization_id = em.organization_id and r.enrollment_modality_id = em.id "
                    + "and r.kind = 'CLOSURE' and r.status = 'OPEN') from enrollment_modality em "
                    + "join modality md on md.id = em.modality_id join unit u on u.organization_id = em.organization_id and u.id = em.unit_id "
                    + "join training_class c on c.organization_id = em.organization_id and c.id = em.primary_class_id "
                    + "join plan p on p.organization_id = em.organization_id and p.id = em.plan_id "
                    + "where em.organization_id = :org and em.student_id = :s and em.status = 'ACTIVE' order by md.name, em.id")
                    .param("org", org).param("s", studentId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                            rs.getObject(4, UUID.class), rs.getString(5), new Choice(rs.getObject(6, UUID.class), rs.getString(7)),
                            new Choice(rs.getObject(8, UUID.class), rs.getString(9)), rs.getObject(10, OffsetDateTime.class)}).list();
            List<ModalityOption> modalities = new ArrayList<>();
            for (Object[] r : rows) {
                UUID unit = (UUID) r[3], modality = (UUID) r[1];
                Choice klass = (Choice) r[5], plan = (Choice) r[6];
                OffsetDateTime closure = (OffsetDateTime) r[7];
                List<Choice> classes = jdbc.sql("select id, name from training_class where organization_id = :org and unit_id = :u and modality_id = :m and status = 'ACTIVE' "
                        + "and id <> :cur order by name, id").param("org", org).param("u", unit).param("m", modality).param("cur", klass.id())
                        .query((rs, n) -> new Choice(rs.getObject(1, UUID.class), rs.getString(2))).list();
                List<PlanChoice> plans = jdbc.sql("select id, name, value_cents from plan where organization_id = :org and unit_id = :u and modality_id = :m and status = 'ACTIVE' "
                        + "and id <> :cur order by name, id").param("org", org).param("u", unit).param("m", modality).param("cur", plan.id())
                        .query((rs, n) -> new PlanChoice(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3))).list();
                modalities.add(new ModalityOption((UUID) r[0], modality, (String) r[2], unit, (String) r[4], klass, plan, closure == null ? null : closure.toInstant(), classes, plans));
            }
            List<Offer> offers = jdbc.sql("select u.id, u.name, md.id, md.name from unit u join unit_modality um on um.organization_id = u.organization_id and um.unit_id = u.id and um.offered "
                    + "join organization_modality om on om.organization_id = u.organization_id and om.modality_id = um.modality_id and om.active "
                    + "join modality md on md.id = um.modality_id where u.organization_id = :org and u.active "
                    + "and not exists (select 1 from enrollment_modality em where em.organization_id = u.organization_id and em.student_id = :s and em.status = 'ACTIVE' "
                    + "and em.modality_id = md.id) order by u.name, md.name, u.id")
                    .param("org", org).param("s", studentId)
                    .query((rs, n) -> new Offer(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4))).list();
            return new Options(modalities, offers);
        });
    }

    /** Cria o pedido (201). Pedido igual ainda aberto: 409 {@code REQUEST_ALREADY_OPEN}, sem gravar. */
    public Request create(AccountPrincipal who, UUID studentId, Input in, String ip) {
        terms.requireAccepted(who);
        if (in == null || in.kind() == null || !KINDS.contains(in.kind())) throw new ApiValidationException("kind", "inválido");
        String note = in.note() == null || in.note().isBlank() ? null : in.note().strip();
        if (note != null && note.length() > 500) throw new ApiValidationException("note", "no máximo 500 caracteres");
        Request created = transactions.execute(() -> {
            UUID org = who.organizationId();
            terms.acceptable(who, studentId, true);
            UUID id = UUID.randomUUID();
            UUID unitId, rowId = null, classId = null, planId = null, targetUnit = null, targetModality = null;
            String reason = null;
            switch (in.kind()) {
                case "NEW_MODALITY" -> {
                    if (in.targetUnitId() == null) throw new ApiValidationException("targetUnitId", "obrigatório");
                    if (in.targetModalityId() == null) throw new ApiValidationException("targetModalityId", "obrigatório");
                    boolean offered = jdbc.sql("select count(*) from unit u join unit_modality um on um.organization_id = u.organization_id and um.unit_id = u.id and um.offered "
                            + "join organization_modality om on om.organization_id = u.organization_id and om.modality_id = um.modality_id and om.active "
                            + "where u.organization_id = :org and u.id = :u and u.active and um.modality_id = :m").param("org", org).param("u", in.targetUnitId())
                            .param("m", in.targetModalityId()).query(Long.class).single() > 0;
                    if (!offered) throw new ApiValidationException("targetModalityId", "modalidade inativa ou não oferecida pela unidade");
                    boolean has = jdbc.sql("select count(*) from enrollment_modality where organization_id = :org and student_id = :s and status = 'ACTIVE' and modality_id = :m")
                            .param("org", org).param("s", studentId).param("m", in.targetModalityId()).query(Long.class).single() > 0;
                    if (has) throw new ApiValidationException("targetModalityId", "o aluno já tem esta modalidade ativa");
                    unitId = targetUnit = in.targetUnitId();
                    targetModality = in.targetModalityId();
                }
                default -> {
                    if (in.modalityRowId() == null) throw new ApiValidationException("modalityRowId", "obrigatório");
                    var row = jdbc.sql("select unit_id, modality_id, primary_class_id, plan_id from enrollment_modality "
                            + "where organization_id = :org and id = :id and student_id = :s and status = 'ACTIVE'").param("org", org).param("id", in.modalityRowId())
                            .param("s", studentId).query((rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                                    rs.getObject(4, UUID.class)}).optional()
                            .orElseThrow(() -> new ApiValidationException("modalityRowId", "modalidade ativa do aluno não encontrada"));
                    unitId = row[0];
                    rowId = in.modalityRowId();
                    switch (in.kind()) {
                        case "CLASS_CHANGE" -> {
                            if (in.targetClassId() == null) throw new ApiValidationException("targetClassId", "obrigatório");
                            if (in.targetClassId().equals(row[2])) throw new ApiValidationException("targetClassId", "já é a turma atual");
                            requireSame(org, "training_class", in.targetClassId(), row[0], row[1], "targetClassId", "turma inativa ou de outra unidade ou modalidade");
                            classId = in.targetClassId();
                            requireNoClosure(rowId);
                        }
                        case "PLAN_CHANGE" -> {
                            if (in.targetPlanId() == null) throw new ApiValidationException("targetPlanId", "obrigatório");
                            if (in.targetPlanId().equals(row[3])) throw new ApiValidationException("targetPlanId", "já é o plano atual");
                            requireSame(org, "plan", in.targetPlanId(), row[0], row[1], "targetPlanId", "plano inativo ou de outra unidade ou modalidade");
                            planId = in.targetPlanId();
                            requireNoClosure(rowId);
                        }
                        default -> {
                            if (in.reason() == null || !REASONS.contains(in.reason())) throw new ApiValidationException("reason", "inválido");
                            reason = in.reason();
                        }
                    }
                }
            }
            int inserted = jdbc.sql("insert into portal_request (id, organization_id, student_id, unit_id, kind, enrollment_modality_id, target_class_id, target_plan_id, "
                    + "target_modality_id, target_unit_id, reason, note, requested_by_account_id, ip) "
                    + "select cast(:id as uuid), cast(:org as uuid), cast(:s as uuid), cast(:unit as uuid), cast(:kind as text), cast(:row as uuid), cast(:class as uuid), cast(:plan as uuid), cast(:tm as uuid), cast(:tu as uuid), cast(:reason as text), cast(:note as text), cast(:acc as uuid), cast(:ip as text) "
                    + "where not exists (select 1 from portal_request where status = 'OPEN' and kind = :kind and (enrollment_modality_id = :row "
                    + "or (kind = 'NEW_MODALITY' and student_id = :s and target_unit_id = :tu and target_modality_id = :tm))) on conflict do nothing")
                    .param("id", id).param("org", org).param("s", studentId).param("unit", unitId).param("kind", in.kind()).param("row", rowId, java.sql.Types.OTHER)
                    .param("class", classId, java.sql.Types.OTHER).param("plan", planId, java.sql.Types.OTHER).param("tm", targetModality, java.sql.Types.OTHER)
                    .param("tu", targetUnit, java.sql.Types.OTHER).param("reason", reason).param("note", note).param("acc", who.accountId()).param("ip", ip).update();
            if (inserted == 0) throw new ApiStatusException(ErrorCode.REQUEST_ALREADY_OPEN);
            Request saved = one(org, id);
            record(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "portal_request.created", saved, ip);
            return saved;
        });
        publish(who.organizationId(), created);
        return created;
    }

    // ---- gestão ----

    /** Lista da gestão: por padrão os abertos; do mais antigo ao mais novo; Administrador só vê as unidades do seu alcance (RN-ALU-07). */
    public RequestList list(AccountPrincipal who, String status, String kind, UUID unitId, PageRequest page) {
        students.requireAccess(who);
        if (status != null && !status.equals("ALL") && !STATUSES.contains(status)) throw new ApiValidationException("status", "inválido");
        if (kind != null && !KINDS.contains(kind)) throw new ApiValidationException("kind", "inválido");
        Set<UUID> reach = reach(who);
        if (reach != null && (reach.isEmpty() || unitId != null && !reach.contains(unitId))) return new RequestList(List.of(), page.page(), page.size(), 0);
        return transactions.execute(() -> {
            StringBuilder where = new StringBuilder("where r.organization_id = :org");
            Map<String, Object> params = new HashMap<>();
            params.put("org", who.organizationId());
            if (!"ALL".equals(status)) {
                where.append(" and r.status = :status");
                params.put("status", status == null ? "OPEN" : status);
            }
            if (kind != null) {
                where.append(" and r.kind = :kind");
                params.put("kind", kind);
            }
            if (unitId != null) {
                where.append(" and r.unit_id = :unit");
                params.put("unit", unitId);
            }
            if (reach != null) {
                where.append(" and r.unit_id in (:reach)");
                params.put("reach", reach);
            }
            long total = jdbc.sql("select count(*) from portal_request r " + where).params(params).query(Long.class).single();
            List<Request> items = jdbc.sql(SELECT + where + " order by r.created_at, r.id limit :limit offset :offset").params(params)
                    .param("limit", page.size()).param("offset", page.offset()).query((rs, n) -> map(rs)).list();
            return new RequestList(items, page.page(), page.size(), total);
        });
    }

    public Request get(AccountPrincipal who, UUID id) {
        students.requireAccess(who);
        return transactions.execute(() -> visible(who, id));
    }

    /** Aprovar (troca de turma, nova modalidade e troca de plano): só marca o pedido; a matrícula muda quando a gestão conclui o fluxo (D4). */
    public Request approve(AccountPrincipal who, UUID id, String response, String ip) {
        return decide(who, id, "APPROVED", optional(response), ip);
    }

    /** Recusar exige a mensagem ao aluno (3 a 500 caracteres); no encerramento a modalidade volta a Ativa. */
    public Request refuse(AccountPrincipal who, UUID id, String response, String ip) {
        String text = response == null ? "" : response.strip();
        if (text.length() < 3 || text.length() > 500) throw new ApiValidationException("response", "de 3 a 500 caracteres");
        return decide(who, id, "REFUSED", text, ip);
    }

    private static String optional(String response) {
        String text = response == null ? "" : response.strip();
        if (text.length() > 500) throw new ApiValidationException("response", "no máximo 500 caracteres");
        return text.isEmpty() ? null : text;
    }

    private Request decide(AccountPrincipal who, UUID id, String status, String response, String ip) {
        students.requireAccess(who);
        Saved saved = transactions.execute(() -> {
            Request found = visible(who, id);
            jdbc.sql("select 1 from portal_request where organization_id = :org and id = :id for update").param("org", who.organizationId()).param("id", id)
                    .query(Integer.class).single();
            if (!"OPEN".equals(jdbc.sql("select status from portal_request where organization_id = :org and id = :id").param("org", who.organizationId()).param("id", id)
                    .query(String.class).single())) {
                throw new ApiStatusException(ErrorCode.REQUEST_NOT_OPEN);
            }
            if ("APPROVED".equals(status) && "CLOSURE".equals(found.kind())) {
                throw new ApiValidationException("kind", "o pedido de encerramento não tem aprovação; encerre a modalidade ou recuse");
            }
            jdbc.sql("update portal_request set status = :status, response = :response, decided_at = :now, decided_by_account_id = :acc where organization_id = :org and id = :id")
                    .param("status", status).param("response", response).param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                    .param("acc", who.accountId()).param("org", who.organizationId()).param("id", id).update();
            Request after = one(who.organizationId(), id);
            record(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "APPROVED".equals(status) ? "portal_request.approved" : "portal_request.refused", after, ip);
            return new Saved(after, requesterEmail(who.organizationId(), id));
        });
        mail(who.organizationId(), saved);
        return saved.request();
    }

    // ---- compartilhado com a tarefa agendada ----

    /** Mensagem ao aluno com o desfecho do pedido, sem desfazer nada se falhar. Chamar depois do commit. */
    void mail(UUID org, Saved saved) {
        Request r = saved.request();
        if (saved.requesterEmail() == null) return;
        try {
            String prefix = "mail.portal-request." + r.status();
            String body = messages.getMessage(prefix + ".body", new Object[] {messages.getMessage("portal-request.kind." + r.kind(), null, PT_BR)}, PT_BR);
            if (r.response() != null) body += "\n\n" + messages.getMessage("mail.portal-request.response", new Object[] {r.response()}, PT_BR);
            email.sendText(org, saved.requesterEmail(), EmailTemplate.PORTAL_REQUEST, messages.getMessage(prefix + ".subject", null, PT_BR), body);
        } catch (RuntimeException e) {
            LOG.warn("portal request e-mail not sent status={} cause={}", r.status(), e.getClass().getSimpleName());
        }
    }

    /** Dentro da transação da organização: auditoria sem o texto livre (ação, aluno, tipo e situação). */
    void record(AuditActor actor, String action, Request r, String ip) {
        var after = new LinkedHashMap<String, Object>();
        after.put("studentId", r.studentId());
        after.put("kind", r.kind());
        after.put("status", r.status());
        audit.record(new AuditEntry(actor, action, "portal_request", r.id().toString(), r.unitId(), Map.of(), after, null, ip));
    }

    /** Dentro da transação: o pedido de encerramento aberto da modalidade, travado, ou {@code null}. */
    UUID openClosure(UUID org, UUID rowId) {
        return jdbc.sql("select id from portal_request where organization_id = :org and enrollment_modality_id = :row and kind = 'CLOSURE' and status = 'OPEN' for update")
                .param("org", org).param("row", rowId).query(UUID.class).optional().orElse(null);
    }

    /** Dentro da transação: fecha o pedido aberto com a situação dada (CLOSED ou EXPIRED), grava a auditoria e devolve o que o e-mail precisa. */
    Saved finish(UUID org, UUID id, String status, AuditActor actor, String action, String ip) {
        jdbc.sql("update portal_request set status = :status, decided_at = :now, decided_by_account_id = :acc where organization_id = :org and id = :id and status = 'OPEN'")
                .param("status", status).param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).param("acc", actor.accountId()).param("org", org)
                .param("id", id).update();
        Saved saved = saved(org, id);
        record(actor, action, saved.request(), ip);
        return saved;
    }

    Saved saved(UUID org, UUID id) {
        return new Saved(one(org, id), requesterEmail(org, id));
    }

    // ---- internos ----

    private void publish(UUID org, Request r) {
        Map<String, Object> params = Map.of("kind", r.kind());
        notifications.publish(org, r.unitId(), NotificationType.PORTAL_REQUEST, "portal_request", r.id(), params);
        if ("CLOSURE".equals(r.kind())) notifications.publish(org, r.unitId(), NotificationType.CLOSURE_REQUEST, "portal_request", r.id(), params);
    }

    private PortalTerms.Linked linked(AccountPrincipal who, UUID studentId) {
        return terms.candidates(who, studentId, false).stream().findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private void requireSame(UUID org, String table, UUID id, UUID unit, UUID modality, String field, String message) {
        var c = jdbc.sql("select unit_id, modality_id, status from " + table + " where organization_id = :org and id = :id").param("org", org).param("id", id)
                .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)}).optional()
                .orElseThrow(() -> new ApiValidationException(field, message));
        if (!unit.equals(c[0]) || !modality.equals(c[1]) || !"ACTIVE".equals(c[2])) throw new ApiValidationException(field, message);
    }

    /** RN-MAT-18 (D3): com encerramento aberto a modalidade não aceita troca de turma nem de plano. */
    private void requireNoClosure(UUID rowId) {
        boolean open = jdbc.sql("select count(*) from portal_request where enrollment_modality_id = :row and kind = 'CLOSURE' and status = 'OPEN'").param("row", rowId)
                .query(Long.class).single() > 0;
        if (open) throw new ApiStatusException(ErrorCode.REQUEST_ALREADY_OPEN);
    }

    /** Alcance de unidade da gestão: {@code null} para quem vê todas (Coordenador), senão as do Administrador. */
    private static Set<UUID> reach(AccountPrincipal who) {
        return who.role() == Role.ADMINISTRATOR ? (who.units() == null ? Set.of() : who.units()) : null;
    }

    private Request visible(AccountPrincipal who, UUID id) {
        Set<UUID> reach = reach(who);
        Request r = jdbc.sql(SELECT + "where r.organization_id = :org and r.id = :id").param("org", who.organizationId()).param("id", id)
                .query((rs, n) -> map(rs)).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        if (reach != null && !reach.contains(r.unitId())) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return r;
    }

    private Request one(UUID org, UUID id) {
        return jdbc.sql(SELECT + "where r.organization_id = :org and r.id = :id").param("org", org).param("id", id).query((rs, n) -> map(rs)).single();
    }

    private String requesterEmail(UUID org, UUID id) {
        return jdbc.sql("select a.email from portal_request r join account a on a.id = r.requested_by_account_id where r.organization_id = :org and r.id = :id")
                .param("org", org).param("id", id).query(String.class).optional().orElse(null);
    }

    private static Request map(java.sql.ResultSet rs) throws java.sql.SQLException {
        OffsetDateTime decided = rs.getObject(21, OffsetDateTime.class);
        return new Request(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getObject(4, UUID.class), rs.getString(5), rs.getObject(6, UUID.class),
                rs.getString(7), rs.getObject(8, UUID.class), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11), rs.getObject(12, UUID.class),
                rs.getString(13), rs.getObject(14, UUID.class), rs.getObject(15, UUID.class), rs.getString(16), rs.getString(17), rs.getString(18), rs.getString(19),
                rs.getObject(20, OffsetDateTime.class).toInstant(), decided == null ? null : decided.toInstant());
    }
}
