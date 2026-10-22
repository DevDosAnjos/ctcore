package br.com.ctcore.billing;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RN-COB-01 a 06: a mensalidade de cada modalidade ativa nasce pelo ponteiro da modalidade ({@code next_charge_ref}, {@code next_charge_due_on}).
 * A mesma conta serve à tarefa diária, à prévia (sem gravar) e à geração adiantada; o "hoje" é o do fuso da unidade.
 */
@Service
public class MonthlyGeneration {
    static final int HORIZON_DAYS = 10;
    static final int MAX_PREVIEW_DAYS = 60;
    static final int MAX_ADVANCE = 12;
    private static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);
    private static final String FROM = "from enrollment_modality em join student s on s.id = em.student_id and s.organization_id = em.organization_id "
            + "join modality m on m.id = em.modality_id join plan p on p.id = em.plan_id left join plan np on np.id = em.next_plan_id "
            + "left join benefit b on b.enrollment_modality_id = em.id and b.status = 'ACTIVE' "
            + "left join student_guardian sg on sg.student_id = em.student_id and sg.financial "
            + "where em.organization_id = :org and em.unit_id = :unit and em.status = 'ACTIVE'";

    /** Resultado de uma execução; {@code failedUnits} só a tarefa preenche. */
    public record Run(int created, int skippedZero, int skippedExisting, int failedUnits) {
        Run plus(Run other) {
            return new Run(created + other.created, skippedZero + other.skippedZero, skippedExisting + other.skippedExisting, failedUnits + other.failedUnits);
        }
    }

    public record Created(UUID id, UUID modalityRowId, String type, UUID unitId, LocalDate referenceMonth, LocalDate dueOn, int toleranceDays, long amountCents) {}

    public record Line(UUID studentId, String student, UUID modalityRowId, String modality, String plan, long discountCents, long amountCents, LocalDate referenceMonth) {}

    public record Day(LocalDate dueOn, int count, long totalCents, List<Line> lines) {}

    public record Skipped(UUID studentId, String student, String modality, LocalDate referenceMonth, String reason) {}

    public record Preview(LocalDate until, List<Day> days, int count, long totalCents, long previousMonthTotalCents, List<Skipped> skipped) {}

    public record Advance(Integer count) {}

    private record Row(UUID id, UUID studentId, String student, String modality, UUID planId, String planName, long planCents, UUID nextPlanId, String nextPlanName,
            long nextPlanCents, int dueDay, LocalDate ref, LocalDate dueOn, UUID benefitId, String benefitType, long benefitValue, LocalDate benefitUntil,
            UUID guardianId, boolean minor) {}

    private record Planned(Row row, ChargePricing.Composition value, String planName, UUID payer, LocalDate ref, LocalDate dueOn) {}

    private record Pointer(UUID row, LocalDate ref, LocalDate dueOn, boolean promote) {}

    private record Plan(List<Planned> items, List<Pointer> pointers) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final ScopedAudit scopedAudit;
    private final Clock clock;

    public MonthlyGeneration(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, ScopedAudit scopedAudit,
            Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.scopedAudit = scopedAudit;
        this.clock = clock;
    }

    private LocalDate today(UUID unit) {
        String zone = jdbc.sql("select timezone from unit where id = :unit").param("unit", unit).query(String.class).single();
        return LocalDate.now(clock.withZone(ZoneId.of(zone)));
    }

    /** Candidatas pelo ponteiro: enquanto a data cair até {@code until} e não passar de {@code count} por modalidade. Com {@code lock}, trava as linhas (duas execuções não repetem). */
    private Plan plan(UUID org, UUID unit, LocalDate today, LocalDate until, int count, UUID onlyRow, boolean lock) {
        String sql = "select em.id, em.student_id, s.name, m.name, em.plan_id, p.name, p.value_cents, em.next_plan_id, np.name, coalesce(np.value_cents, 0), em.due_day, "
                + "em.next_charge_ref, em.next_charge_due_on, b.id, b.type, coalesce(b.value, 0), b.valid_until, sg.guardian_id, "
                + "(s.birth_date + interval '18 years')::date > cast(:today as date) " + FROM + (onlyRow == null ? "" : " and em.id = :row") + " order by em.id"
                + (lock ? " for update of em" : "");
        var query = jdbc.sql(sql).param("org", org).param("unit", unit).param("today", today);
        if (onlyRow != null) query = query.param("row", onlyRow);
        List<Row> rows = query.query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4),
                rs.getObject(5, UUID.class), rs.getString(6), rs.getLong(7), rs.getObject(8, UUID.class), rs.getString(9), rs.getLong(10), rs.getInt(11),
                rs.getObject(12, LocalDate.class), rs.getObject(13, LocalDate.class), rs.getObject(14, UUID.class), rs.getString(15), rs.getLong(16),
                rs.getObject(17, LocalDate.class), rs.getObject(18, UUID.class), rs.getBoolean(19))).list();
        List<Planned> items = new ArrayList<>();
        List<Pointer> pointers = new ArrayList<>();
        for (Row r : rows) {
            LocalDate due = r.dueOn();
            LocalDate ref = r.ref();
            if (ref == null) {
                due = today.withDayOfMonth(r.dueDay());
                if (due.isBefore(today)) due = due.plusMonths(1);
                ref = due.withDayOfMonth(1);
            }
            // RN-MAT-15: a troca de plano agendada vale a partir da primeira mensalidade gerada.
            boolean promote = r.nextPlanId() != null && !due.isAfter(until);
            UUID planId = promote ? r.nextPlanId() : r.planId();
            String planName = promote ? r.nextPlanName() : r.planName();
            long planCents = promote ? r.nextPlanCents() : r.planCents();
            var benefit = r.benefitId() == null ? null : new ChargePricing.BenefitTerms(r.benefitId(), r.benefitType(), r.benefitValue(), r.benefitUntil(), false);
            int made = 0;
            while (made < count && !due.isAfter(until)) {
                if (r.minor() && r.guardianId() == null) throw new IllegalStateException("minor student without a financial guardian: " + r.studentId());
                items.add(new Planned(r, ChargePricing.compose(planId, planCents, benefit, today), planName, r.minor() ? r.guardianId() : null, ref, due));
                made++;
                ref = ref.plusMonths(1);
                due = ref.withDayOfMonth(r.dueDay());
            }
            if (made > 0) pointers.add(new Pointer(r.id(), ref, due, promote));
        }
        return new Plan(items, pointers);
    }

    /** Grava as candidatas de valor maior que zero (idempotente: o que já existe é pulado) e avança os ponteiros. */
    private Run apply(Plan plan, UUID org, UUID unit, UUID by, String origin, List<Created> out) {
        int created = 0, zero = 0, existing = 0;
        for (Planned p : plan.items()) {
            if (p.value().amountCents() == 0) {
                zero++;
                continue;
            }
            UUID id = UUID.randomUUID();
            int inserted = jdbc.sql("insert into charge (id, organization_id, unit_id, student_id, payer_guardian_id, type, enrollment_modality_id, plan_id, benefit_id, "
                    + "reference_month, due_on, plan_cents, benefit_cents, amount_cents, origin, created_by_account_id) "
                    + "values (:id, :org, :unit, :student, :payer, 'MONTHLY', :row, :plan, :benefit, :ref, :due, :planCents, :benefitCents, :amount, :origin, :by) "
                    + "on conflict do nothing")
                    .param("id", id).param("org", org).param("unit", unit).param("student", p.row().studentId()).param("payer", p.payer()).param("row", p.row().id())
                    .param("plan", p.value().planId()).param("benefit", p.value().benefitId()).param("ref", p.ref()).param("due", p.dueOn())
                    .param("planCents", p.value().planCents()).param("benefitCents", p.value().benefitCents()).param("amount", p.value().amountCents())
                    .param("origin", origin).param("by", by).update();
            if (inserted == 0) {
                existing++;
            } else {
                created++;
                out.add(new Created(id, p.row().id(), "MONTHLY", unit, p.ref(), p.dueOn(), 0, p.value().amountCents()));
            }
        }
        for (Pointer p : plan.pointers()) {
            jdbc.sql("update enrollment_modality set next_charge_ref = :ref, next_charge_due_on = :due, "
                    + "plan_id = case when cast(:promote as boolean) then next_plan_id else plan_id end, "
                    + "next_plan_id = case when cast(:promote as boolean) then null else next_plan_id end where id = :id")
                    .param("ref", p.ref()).param("due", p.dueOn()).param("promote", p.promote()).param("id", p.row()).update();
        }
        return new Run(created, zero, existing, 0);
    }

    /** RN-COB-02: a rodada diária de uma unidade (chamada dentro da transação da organização); audita como sistema. */
    public Run runUnit(UUID org, UUID unit) {
        LocalDate today = today(unit);
        Run run = apply(plan(org, unit, today, today.plusDays(HORIZON_DAYS), Integer.MAX_VALUE, null, true), org, unit, null, "GENERATION", new ArrayList<>());
        scopedAudit.record(org, new AuditEntry(SYSTEM, "charge.generation_run", "unit", unit.toString(), unit, Map.of(),
                Map.of("date", today.toString(), "created", run.created(), "skippedZero", run.skippedZero(), "skippedExisting", run.skippedExisting()), null, null));
        return run;
    }

    /** RF-COB-03: o que a rodada criaria até hoje mais {@code days}, sem gravar nada. */
    public Preview preview(AccountPrincipal who, UUID unitId, Integer days) {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        int span = days == null ? HORIZON_DAYS : days;
        if (span < 1 || span > MAX_PREVIEW_DAYS) throw new ApiValidationException("days", "de 1 a " + MAX_PREVIEW_DAYS);
        if (permissions.grant(who, Permission.COLLECTIONS, unitId) == Access.NONE) {
            return new Preview(LocalDate.now(clock).plusDays(span), List.of(), 0, 0, 0, List.of());
        }
        return transactions.execute(() -> {
            LocalDate today = today(unitId);
            LocalDate until = today.plusDays(span);
            Plan plan = plan(who.organizationId(), unitId, today, until, Integer.MAX_VALUE, null, false);
            TreeMap<LocalDate, List<Line>> byDay = new TreeMap<>();
            List<Skipped> skipped = new ArrayList<>();
            LocalDate first = null;
            long total = 0;
            for (Planned p : plan.items()) {
                var v = p.value();
                if (v.amountCents() == 0) {
                    skipped.add(new Skipped(p.row().studentId(), p.row().student(), p.row().modality(), p.ref(), "FULL_BENEFIT"));
                    continue;
                }
                byDay.computeIfAbsent(p.dueOn(), k -> new ArrayList<>()).add(new Line(p.row().studentId(), p.row().student(), p.row().id(), p.row().modality(),
                        p.planName(), v.benefitCents(), v.amountCents(), p.ref()));
                if (first == null || p.ref().isBefore(first)) first = p.ref();
                total += v.amountCents();
            }
            List<Day> daysList = new ArrayList<>();
            int count = 0;
            for (var e : byDay.entrySet()) {
                daysList.add(new Day(e.getKey(), e.getValue().size(), e.getValue().stream().mapToLong(Line::amountCents).sum(), e.getValue()));
                count += e.getValue().size();
            }
            long previous = first == null ? 0 : jdbc.sql("select coalesce(sum(amount_cents), 0) from charge where organization_id = :org and unit_id = :unit "
                    + "and type = 'MONTHLY' and reference_month = :ref and status <> 'CANCELED'")
                    .param("org", who.organizationId()).param("unit", unitId).param("ref", first.minusMonths(1)).query(Long.class).single();
            return new Preview(until, daysList, count, total, previous, skipped);
        });
    }

    /** RN-COB-06: as próximas {@code count} mensalidades da modalidade a partir do ponteiro; tudo ou nada. */
    public List<Created> advance(AccountPrincipal who, UUID studentId, UUID rowId, Advance in, String ip) {
        if (permissions.effective(who).get(Permission.COLLECTIONS) == Access.NONE) throw new AccessDeniedException("Permission denied");
        if (in == null || in.count() == null || in.count() < 1 || in.count() > MAX_ADVANCE) throw new ApiValidationException("count", "de 1 a " + MAX_ADVANCE);
        return transactions.execute(() -> {
            var row = jdbc.sql("select unit_id, status from enrollment_modality where id = :id and student_id = :student and organization_id = :org")
                    .param("id", rowId).param("student", studentId).param("org", who.organizationId())
                    .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2)}).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            UUID unit = (UUID) row[0];
            if (permissions.grant(who, Permission.COLLECTIONS, unit) != Access.FULL) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            if (!"ACTIVE".equals(row[1])) throw new ApiStatusException(ErrorCode.UNPROCESSABLE);
            Plan plan = plan(who.organizationId(), unit, today(unit), LocalDate.MAX, in.count(), rowId, true);
            if (plan.items().get(0).value().amountCents() == 0) throw new ApiStatusException(ErrorCode.UNPROCESSABLE);
            List<Created> created = new ArrayList<>();
            apply(plan, who.organizationId(), unit, who.accountId(), "ADVANCE", created);
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "charge.advance_generated", "enrollment_modality",
                    rowId.toString(), unit, Map.of(), Map.of("count", created.size(), "chargeIds", created.stream().map(c -> c.id().toString()).toList()), null, ip));
            return created;
        });
    }
}
