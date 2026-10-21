package br.com.ctcore.benefit;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.plan.Plans;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-BEN-02: conceder benefício a uma modalidade da matrícula e o resumo prévio. Organização, perfil e alcance vêm da sessão; o lançador é
 * a conta da sessão. A modalidade fica travada durante a concessão (D8) e substituir o ativo exige confirmação (D3). Não altera cobrança (Sprint 08).
 */
@Service
public class BenefitWrite {
    static final long MAX_AMOUNT = 99_999_999L;
    /** RN-COB-02 (D6): a geração cria as cobranças que vencem em até 10 dias. */
    static final int CHARGE_LEAD_DAYS = 10;
    static final String REPLACED = "Substituído por novo benefício";

    public record Input(UUID studentId, UUID modalityRowId, String type, BigDecimal value, String reason, String decidedBy, LocalDate validUntil,
            Boolean replaceExisting, UUID teacherId) {}

    public record EndInput(String reason) {}

    public record PreviewInput(UUID studentId, UUID modalityRowId, String type, BigDecimal value) {}

    public record PlanRef(UUID id, String name, long valueCents) {}

    public record Preview(PlanRef plan, Benefits.Row current, long newMonthlyValueCents, String effectiveFrom, boolean replaces, boolean high) {}

    private record Target(UUID unitId, UUID planId, String planName, long planCents, int dueDay, ZoneId zone) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Plans plans;
    private final Benefits benefits;
    private final AuditService audit;
    private final Clock clock;

    public BenefitWrite(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Plans plans, Benefits benefits, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.plans = plans;
        this.benefits = benefits;
        this.audit = audit;
        this.clock = clock;
    }

    /** RN-BEN-04 (D6): mês do primeiro vencimento do dia {@code dueDay} depois de hoje + 10 dias. */
    static YearMonth effectiveFrom(LocalDate today, int dueDay) {
        LocalDate limit = today.plusDays(CHARGE_LEAD_DAYS);
        YearMonth month = YearMonth.from(limit);
        return dueDay > limit.getDayOfMonth() ? month : month.plusMonths(1);
    }

    static boolean high(String type, long value, long planCents) {
        return "PERCENT".equals(type) ? value > 50 : value * 2 > planCents;
    }

    public static String type(String raw) {
        String t = raw == null ? "" : raw.strip().toUpperCase(java.util.Locale.ROOT);
        if (!t.equals("PERCENT") && !t.equals("AMOUNT")) throw new ApiValidationException("type", "PERCENT ou AMOUNT");
        return t;
    }

    public static long value(String type, BigDecimal raw) {
        long max = type.equals("PERCENT") ? 100 : MAX_AMOUNT;
        String message = type.equals("PERCENT") ? "inteiro de 1 a 100" : "inteiro de 1 a 99999999 centavos";
        if (raw == null) throw new ApiValidationException("value", "obrigatório");
        if (raw.stripTrailingZeros().scale() > 0 || raw.compareTo(BigDecimal.ONE) < 0 || raw.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new ApiValidationException("value", message);
        }
        return raw.longValueExact();
    }

    static String text(String field, String raw, int min, int max) {
        String t = raw == null ? "" : raw.strip();
        if (t.length() < min || t.length() > max) throw new ApiValidationException(field, "de " + min + " a " + max + " caracteres");
        return t;
    }

    private static ApiStatusException missing() {
        return new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    /** Modalidade ativa do aluno informado, na organização; alcance do Administrador (403 fora). */
    private Target target(AccountPrincipal who, UUID studentId, UUID rowId, boolean lock) {
        if (studentId == null) throw new ApiValidationException("studentId", "obrigatório");
        if (rowId == null) throw new ApiValidationException("modalityRowId", "obrigatório");
        Target t = jdbc.sql("select em.unit_id, p.id, p.name, p.value_cents, em.due_day, u.timezone from enrollment_modality em "
                + "join plan p on p.organization_id = em.organization_id and p.id = em.plan_id join unit u on u.organization_id = em.organization_id and u.id = em.unit_id "
                + "where em.organization_id = :org and em.id = :row and em.student_id = :student and em.status = 'ACTIVE'" + (lock ? " for update of em" : ""))
                .param("org", who.organizationId()).param("row", rowId).param("student", studentId)
                .query((rs, n) -> new Target(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getLong(4), rs.getInt(5),
                        ZoneId.of(rs.getString(6))))
                .optional().orElseThrow(BenefitWrite::missing);
        if (who.role() == Role.ADMINISTRATOR && (who.units() == null || !who.units().contains(t.unitId()))) {
            throw new AccessDeniedException("Unit out of reach");
        }
        return t;
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    /** RF-BEN-02: o que a janela mostra antes de confirmar. Não grava nem audita. */
    public Preview preview(AccountPrincipal who, PreviewInput in) {
        plans.requireAccess(who);
        if (in == null) throw new ApiValidationException("type", "obrigatório");
        String type = type(in.type());
        long value = value(type, in.value());
        return transactions.execute(() -> {
            Target t = target(who, in.studentId(), in.modalityRowId(), false);
            var current = benefits.findActive(who.organizationId(), in.modalityRowId());
            return new Preview(new PlanRef(t.planId(), t.planName(), t.planCents()), current.orElse(null),
                    Benefits.monthlyValueCents(type, value, t.planCents()), effectiveFrom(today(t.zone()), t.dueDay()).toString(), current.isPresent(),
                    high(type, value, t.planCents()));
        });
    }

    /** RF-BEN-02: concede; havendo ativo, só com {@code replaceExisting} (encerra o atual na mesma transação). */
    public Benefits.Row grant(AccountPrincipal who, Input in, String ip) {
        plans.requireAccess(who);
        if (in == null) throw new ApiValidationException("type", "obrigatório");
        String type = type(in.type());
        long value = value(type, in.value());
        String reason = text("reason", in.reason(), 2, 200);
        String decidedBy = text("decidedBy", in.decidedBy(), 2, 80);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            Target t = target(who, in.studentId(), in.modalityRowId(), true);
            if (in.validUntil() != null && in.validUntil().isBefore(today(t.zone()))) {
                throw new ApiValidationException("validUntil", "de hoje em diante");
            }
            if (in.teacherId() != null && jdbc.sql("select count(*) from teacher where organization_id = :org and id = :id and status = 'ACTIVE'")
                    .param("org", org).param("id", in.teacherId()).query(Integer.class).single() == 0) {
                throw missing();
            }
            UUID id = UUID.randomUUID();
            var current = benefits.findActive(org, in.modalityRowId());
            if (current.isPresent()) {
                if (!Boolean.TRUE.equals(in.replaceExisting())) {
                    throw new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("replaceExisting", "a modalidade já tem um benefício ativo")));
                }
                Benefits.Row old = current.get();
                jdbc.sql("update benefit set status = 'ENDED', ended_at = :now, end_reason = :why where organization_id = :org and id = :id")
                        .param("now", OffsetDateTime.now(clock)).param("why", REPLACED).param("org", org).param("id", old.id()).update();
                Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
                before.put("status", "ACTIVE");
                after.put("status", "ENDED");
                after.put("endReason", REPLACED);
                after.put("replacedBy", id.toString());
                audit.record(new AuditEntry(actor(who), "benefit.ended", "benefit", old.id().toString(), t.unitId(), before, after, null, ip));
            }
            jdbc.sql("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, "
                    + "granted_by_account_id, valid_until, status, teacher_id) values (:id, :org, :row, :student, :unit, :type, :value, :reason, :by, :granted, :until, 'ACTIVE', :teacher)")
                    .param("id", id).param("org", org).param("row", in.modalityRowId()).param("student", in.studentId()).param("unit", t.unitId())
                    .param("type", type).param("value", value).param("reason", reason).param("by", decidedBy).param("granted", who.accountId())
                    .param("until", in.validUntil()).param("teacher", in.teacherId()).update();
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("studentId", in.studentId().toString());
            after.put("modalityRowId", in.modalityRowId().toString());
            after.put("type", type);
            after.put("value", value);
            after.put("validUntil", in.validUntil() == null ? null : in.validUntil().toString());
            after.put("reason", reason);
            after.put("decidedBy", decidedBy);
            audit.record(new AuditEntry(actor(who), "benefit.granted", "benefit", id.toString(), t.unitId(), null, after, null, ip));
            return benefits.find(org, id).orElseThrow();
        });
    }

    /** RF-BEN-03: encerra o benefício (vencido também); vale a partir da próxima cobrança gerada (Sprint 08). */
    public Benefits.Row end(AccountPrincipal who, UUID id, EndInput in, String ip) {
        plans.requireAccess(who);
        String reason = text("reason", in == null ? null : in.reason(), 2, 200);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            UUID unit = jdbc.sql("select unit_id from benefit where organization_id = :org and id = :id for update").param("org", org).param("id", id)
                    .query(UUID.class).optional().orElseThrow(BenefitWrite::missing);
            if (who.role() == Role.ADMINISTRATOR && (who.units() == null || !who.units().contains(unit))) throw new AccessDeniedException("Unit out of reach");
            endLocked(who, id, unit, reason, ip);
            return benefits.find(org, id).orElseThrow();
        });
    }

    /** Dentro de uma transação da organização: encerra os benefícios ativos do professor (RN-PRO-08). */
    public void endTeacherBenefits(AccountPrincipal who, UUID teacherId, String ip) {
        for (var b : jdbc.sql("select id, unit_id from benefit where organization_id = :org and teacher_id = :t and status = 'ACTIVE' order by id for update")
                .param("org", who.organizationId()).param("t", teacherId).query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()) {
            endLocked(who, b.getKey(), b.getValue(), "Professor inativado", ip);
        }
    }

    /** Dentro de uma transação da organização: encerra o benefício ativo da modalidade encerrada (RN-MAT-17, RN-BEN-01). */
    public void endModalityBenefits(AccountPrincipal who, UUID modalityRowId, String ip) {
        for (var b : jdbc.sql("select id, unit_id from benefit where organization_id = :org and enrollment_modality_id = :row and status = 'ACTIVE' order by id for update")
                .param("org", who.organizationId()).param("row", modalityRowId).query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()) {
            endLocked(who, b.getKey(), b.getValue(), "Modalidade encerrada", ip);
        }
    }

    private void endLocked(AccountPrincipal who, UUID id, UUID unit, String reason, String ip) {
        int n = jdbc.sql("update benefit set status = 'ENDED', ended_at = :now, end_reason = :why where organization_id = :org and id = :id and status = 'ACTIVE'")
                .param("now", OffsetDateTime.now(clock)).param("why", reason).param("org", who.organizationId()).param("id", id).update();
        if (n == 0) throw new ApiStatusException(ErrorCode.CONFLICT);
        audit.record(new AuditEntry(actor(who), "benefit.ended", "benefit", id.toString(), unit, Map.of("status", "ACTIVE"),
                Map.of("status", "ENDED", "endReason", reason), null, ip));
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }
}
