package br.com.ctcore.billing;

import br.com.ctcore.benefit.Benefits;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-PLA-05: reajuste de plano sobre as cobranças já geradas. "Ainda não pagas" são as mensalidades OPEN com vencimento de hoje em diante;
 * "pagas futuras", as PAID com mês de referência depois do mês de hoje (no fuso da unidade). Vencidas e pagas do mês corrente nunca mudam.
 * Roda dentro da transação da organização, do mesmo pedido que grava o valor novo (tudo ou nada).
 */
@Service
public class Repricing {
    private static final String[] MONTHS = {"jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"};
    private static final int DIFF_DUE_DAYS = 7;

    public record Counts(int unpaid, int paidFuture) {}

    public record Result(List<UUID> adjusted, List<UUID> created) {}

    private record Paid(UUID id, UUID student, UUID payer, UUID modality, UUID benefit, LocalDate ref, long paid, long already, String type, long value) {}

    private final JdbcClient jdbc;
    private final Clock clock;

    public Repricing(@Lazy JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private LocalDate today(UUID unit) {
        String zone = jdbc.sql("select timezone from unit where id = :unit").param("unit", unit).query(String.class).single();
        return LocalDate.now(clock.withZone(ZoneId.of(zone)));
    }

    /** Alunos com modalidade ativa no plano. */
    public int students(UUID planId) {
        return jdbc.sql("select count(distinct student_id) from enrollment_modality where plan_id = :plan and status = 'ACTIVE'").param("plan", planId)
                .query(Integer.class).single();
    }

    public Counts counts(UUID unit, UUID planId) {
        LocalDate today = today(unit);
        return jdbc.sql("select count(*) filter (where status = 'OPEN' and due_on >= :today), "
                + "count(*) filter (where status = 'PAID' and reference_month > :month) from charge where plan_id = :plan and type = 'MONTHLY'")
                .param("today", today).param("month", today.withDayOfMonth(1)).param("plan", planId)
                .query((rs, n) -> new Counts(rs.getInt(1), rs.getInt(2))).single();
    }

    public Result apply(UUID org, UUID unit, UUID planId, long newCents, boolean adjustUnpaid, boolean chargeDifference, UUID by) {
        LocalDate today = today(unit);
        List<UUID> adjusted = new ArrayList<>();
        List<UUID> created = new ArrayList<>();
        if (adjustUnpaid) {
            var open = jdbc.sql("select c.id, coalesce(b.type, ''), coalesce(b.value, 0) from charge c left join benefit b on b.id = c.benefit_id "
                    + "where c.plan_id = :plan and c.type = 'MONTHLY' and c.status = 'OPEN' and c.due_on >= :today order by c.id for update of c")
                    .param("plan", planId).param("today", today).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)}).list();
            for (Object[] c : open) {
                long amount = value((String) c[1], (long) c[2], newCents);
                jdbc.sql("update charge set plan_cents = :plan, benefit_cents = :discount, amount_cents = :amount where id = :id")
                        .param("plan", newCents).param("discount", newCents - amount).param("amount", amount).param("id", c[0]).update();
                adjusted.add((UUID) c[0]);
            }
        }
        if (chargeDifference) {
            var paid = jdbc.sql("select c.id, c.student_id, c.payer_guardian_id, c.enrollment_modality_id, c.benefit_id, c.reference_month, c.amount_cents, "
                    + "coalesce((select sum(d.amount_cents) from charge d where d.origin_charge_id = c.id and d.type = 'READJUSTMENT_DIFF' and d.status <> 'CANCELED'), 0), "
                    + "coalesce(b.type, ''), coalesce(b.value, 0) from charge c left join benefit b on b.id = c.benefit_id "
                    + "where c.plan_id = :plan and c.type = 'MONTHLY' and c.status = 'PAID' and c.reference_month > :month order by c.id for update of c")
                    .param("plan", planId).param("month", today.withDayOfMonth(1))
                    .query((rs, n) -> new Paid(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                            rs.getObject(5, UUID.class), rs.getObject(6, LocalDate.class), rs.getLong(7), rs.getLong(8), rs.getString(9), rs.getLong(10)))
                    .list();
            for (Paid p : paid) {
                long missing = value(p.type(), p.value(), newCents) - p.paid() - p.already();
                if (missing <= 0) continue;
                UUID id = UUID.randomUUID();
                jdbc.sql("insert into charge (id, organization_id, unit_id, student_id, payer_guardian_id, type, enrollment_modality_id, plan_id, benefit_id, reference_month, "
                        + "description, due_on, plan_cents, amount_cents, origin_charge_id, origin, created_by_account_id) values (:id, :org, :unit, :student, :payer, "
                        + "'READJUSTMENT_DIFF', :row, :plan, :benefit, :ref, :description, :due, :amount, :amount, :origin, 'READJUSTMENT', :by)")
                        .param("id", id).param("org", org).param("unit", unit).param("student", p.student()).param("payer", p.payer()).param("row", p.modality())
                        .param("plan", planId).param("benefit", p.benefit()).param("ref", p.ref())
                        .param("description", "Diferença de reajuste, referência " + MONTHS[p.ref().getMonthValue() - 1] + "/" + p.ref().getYear())
                        .param("due", today.plusDays(DIFF_DUE_DAYS)).param("amount", missing).param("origin", p.id()).param("by", by).update();
                created.add(id);
            }
        }
        return new Result(adjusted, created);
    }

    /** Valor da mensalidade para o plano novo, com o benefício que a cobrança já tem (sem benefício, o plano inteiro). */
    private static long value(String benefitType, long benefitValue, long planCents) {
        return benefitType.isEmpty() ? planCents : Benefits.monthlyValueCents(benefitType, benefitValue, planCents);
    }
}
