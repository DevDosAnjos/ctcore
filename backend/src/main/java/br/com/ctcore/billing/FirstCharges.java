package br.com.ctcore.billing;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-MAT-05, RN-MAT-07, RN-MAT-19: as cobranças que a conclusão da matrícula cria, na transação dela. Uma mensalidade cheia do mês por modalidade
 * adicionada (vence na data da matrícula, com tolerância), a taxa da unidade quando houver, e o ponteiro da segunda cobrança, que a geração diária cria.
 */
@Service
public class FirstCharges {
    /** O que a conclusão decidiu para uma unidade: o início da matrícula, a data de "hoje" no fuso dela, a tolerância, a taxa a cobrar e a data da segunda cobrança. */
    public record UnitTerms(UUID unitId, UUID startId, LocalDate today, int toleranceDays, long feeCents, LocalDate secondDueOn) {}

    public record Charge(UUID id, String type, UUID unitId, LocalDate referenceMonth, LocalDate dueOn, int toleranceDays, long amountCents) {}

    private record Row(UUID id, UUID unitId, UUID planId, long planCents, UUID benefitId, String benefitType, long benefitValue, LocalDate benefitUntil, UUID guardianId,
            LocalDate birthDate) {}

    private final JdbcClient jdbc;
    private final AuditService audit;

    public FirstCharges(@Lazy JdbcClient jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<Charge> create(AccountPrincipal who, UUID studentId, List<UUID> rowIds, Map<UUID, UnitTerms> units, String ip) {
        UUID org = who.organizationId();
        List<Charge> out = new ArrayList<>();
        Map<UUID, UUID> payers = new HashMap<>();
        for (UUID rowId : rowIds) {
            Row r = jdbc.sql("select em.id, em.unit_id, em.plan_id, p.value_cents, b.id, b.type, coalesce(b.value, 0), b.valid_until, sg.guardian_id, s.birth_date "
                    + "from enrollment_modality em join plan p on p.id = em.plan_id and p.organization_id = em.organization_id "
                    + "join student s on s.id = em.student_id and s.organization_id = em.organization_id "
                    + "left join benefit b on b.enrollment_modality_id = em.id and b.status = 'ACTIVE' "
                    + "left join student_guardian sg on sg.student_id = em.student_id and sg.financial where em.organization_id = :org and em.id = :id")
                    .param("org", org).param("id", rowId)
                    .query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getLong(4),
                            rs.getObject(5, UUID.class), rs.getString(6), rs.getLong(7), rs.getObject(8, LocalDate.class), rs.getObject(9, UUID.class),
                            rs.getObject(10, LocalDate.class)))
                    .single();
            UnitTerms u = units.get(r.unitId());
            UUID payer = null;
            if (r.birthDate().plusYears(18).isAfter(u.today())) {
                payer = r.guardianId();
                if (payer == null) throw new IllegalStateException("minor student without a financial guardian: " + studentId);
            }
            payers.put(u.unitId(), payer);
            LocalDate firstRef = u.today().withDayOfMonth(1);
            var benefit = r.benefitId() == null ? null : new ChargePricing.BenefitTerms(r.benefitId(), r.benefitType(), r.benefitValue(), r.benefitUntil(), false);
            var value = ChargePricing.compose(r.planId(), r.planCents(), benefit, u.today());
            if (value.amountCents() > 0) {
                out.add(insert(who, studentId, u, payer, "MONTHLY", rowId, null, value, firstRef, null, ip));
            }
            // D12: a mensalidade seguinte vence na data escolhida; no mesmo mês da primeira, a referência vai para o mês seguinte (RN-COB-05).
            LocalDate ref = u.secondDueOn().withDayOfMonth(1);
            if (!ref.isAfter(firstRef)) ref = firstRef.plusMonths(1);
            jdbc.sql("update enrollment_modality set next_charge_ref = :ref, next_charge_due_on = :due where id = :id").param("ref", ref).param("due", u.secondDueOn())
                    .param("id", rowId).update();
        }
        for (UnitTerms u : units.values()) {
            if (u.feeCents() > 0) {
                var fee = new ChargePricing.Composition(null, 0, null, 0, u.feeCents());
                out.add(insert(who, studentId, u, payers.get(u.unitId()), "ENROLLMENT_FEE", null, u.startId(), fee, null, "Taxa de matrícula", ip));
            }
        }
        return out;
    }

    private Charge insert(AccountPrincipal who, UUID studentId, UnitTerms u, UUID payer, String type, UUID rowId, UUID startId, ChargePricing.Composition v,
            LocalDate ref, String description, String ip) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into charge (id, organization_id, unit_id, student_id, payer_guardian_id, type, enrollment_modality_id, enrollment_start_id, plan_id, benefit_id, "
                + "reference_month, description, due_on, tolerance_days, plan_cents, benefit_cents, amount_cents, origin, created_by_account_id) "
                + "values (:id, :org, :unit, :student, :payer, :type, :row, :start, :plan, :benefit, :ref, :description, :due, :tolerance, :planCents, :benefitCents, "
                + ":amount, 'ENROLLMENT', :by)")
                .param("id", id).param("org", who.organizationId()).param("unit", u.unitId()).param("student", studentId).param("payer", payer).param("type", type)
                .param("row", rowId).param("start", startId).param("plan", v.planId()).param("benefit", v.benefitId()).param("ref", ref)
                .param("description", description).param("due", u.today()).param("tolerance", u.toleranceDays()).param("planCents", v.planCents())
                .param("benefitCents", v.benefitCents()).param("amount", v.amountCents()).param("by", who.accountId()).update();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("type", type);
        after.put("origin", "ENROLLMENT");
        after.put("amountCents", v.amountCents());
        after.put("dueOn", u.today().toString());
        if (ref != null) after.put("referenceMonth", ref.toString());
        audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "charge.created", "charge", id.toString(), u.unitId(),
                null, after, null, ip));
        return new Charge(id, type, u.unitId(), ref, u.today(), u.toleranceDays(), v.amountCents());
    }
}
