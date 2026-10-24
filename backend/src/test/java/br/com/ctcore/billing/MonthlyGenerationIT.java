package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-COB-01 a 05, RN-MAT-15: geração pelo ponteiro, com relógio fixo, fuso da unidade, valor zero, plano agendado e pagador. */
class MonthlyGenerationIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired AuditService audit;
    @Autowired ScopedAudit scopedAudit;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private MonthlyGeneration at(String instant) {
        return new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    private MonthlyGeneration.Run run(MonthlyGeneration generation, Tenant t) {
        return transactions.provision(t.orgId(), () -> generation.runUnit(t.orgId(), t.unitId()));
    }

    private UUID row(Tenant t, int dueDay, long plan) {
        return BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01"), dueDay, plan);
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private List<Map<String, Object>> charges(UUID row) {
        return adminJdbc().queryForList("select * from charge where enrollment_modality_id = ? order by reference_month", row);
    }

    @Test
    @DisplayName("CA10: dia 5 com vencimento 15 gera na unidade, com referência do vencimento, composição gravada e ponteiro no mês seguinte; dia 4 não gera")
    void generatesWithinTenDays() {
        var t = tenant();
        UUID row = row(t, 15, 20000);
        assertThat(run(at("2026-10-04T12:00:00Z"), t).created()).isZero();
        assertThat(charges(row)).isEmpty();

        assertThat(run(at("2026-10-05T12:00:00Z"), t).created()).isEqualTo(1);
        var c = charges(row).get(0);
        assertThat(c.get("due_on").toString()).isEqualTo("2026-10-15");
        assertThat(c.get("reference_month").toString()).isEqualTo("2026-10-01");
        assertThat(c.get("unit_id")).isEqualTo(t.unitId());
        assertThat(c.get("type")).isEqualTo("MONTHLY");
        assertThat(c.get("status")).isEqualTo("OPEN");
        assertThat(c.get("plan_cents")).isEqualTo(20000L);
        assertThat(c.get("benefit_cents")).isEqualTo(0L);
        assertThat(c.get("amount_cents")).isEqualTo(20000L);
        var pointer = adminJdbc().queryForMap("select next_charge_ref, next_charge_due_on from enrollment_modality where id = ?", row);
        assertThat(pointer.get("next_charge_ref").toString()).isEqualTo("2026-11-01");
        assertThat(pointer.get("next_charge_due_on").toString()).isEqualTo("2026-11-15");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'charge.generation_run' and actor_type = 'SYSTEM' and organization_id = ?",
                Long.class, t.orgId())).isEqualTo(2);
    }

    @Test
    @DisplayName("CA10: dia de vencimento já passado nasce no mês seguinte, sem mês passado; execução atrasada gera os meses que ficaram para trás")
    void pointerAndCatchUp() {
        var t = tenant();
        UUID late = row(t, 4, 20000);
        assertThat(run(at("2026-10-05T12:00:00Z"), t).created()).isZero();
        assertThat(charges(late)).isEmpty();

        UUID behind = row(t, 15, 20000);
        adminJdbc().update("update enrollment_modality set next_charge_ref = date '2026-08-01', next_charge_due_on = date '2026-08-15' where id = ?", behind);
        assertThat(run(at("2026-10-05T12:00:00Z"), t).created()).isEqualTo(3);
        assertThat(charges(behind)).extracting(c -> c.get("reference_month").toString()).containsExactly("2026-08-01", "2026-09-01", "2026-10-01");
    }

    @Test
    @DisplayName("CA10: o hoje é o do fuso da unidade: a unidade adiantada gera um dia antes da outra")
    void unitTimezone() {
        var west = tenant();
        var east = tenant();
        adminJdbc().update("update unit set timezone = 'Pacific/Auckland' where id = ?", east.unitId());
        UUID a = row(west, 16, 20000), b = row(east, 16, 20000);
        var generation = at("2026-10-05T12:00:00Z");
        assertThat(run(generation, west).created()).isZero();
        assertThat(run(generation, east).created()).isEqualTo(1);
        assertThat(charges(a)).isEmpty();
        assertThat(charges(b)).hasSize(1);
    }

    @Test
    @DisplayName("CA11: rodar duas vezes cria uma só; mensalidade cancelada do mês não é recriada; o banco recusa duas do mesmo mês")
    void idempotent() {
        var t = tenant();
        UUID row = row(t, 15, 20000);
        var generation = at("2026-10-05T12:00:00Z");
        assertThat(run(generation, t).created()).isEqualTo(1);
        assertThat(run(generation, t).created()).isZero();
        assertThat(charges(row)).hasSize(1);

        adminJdbc().update("update charge set status = 'CANCELED' where enrollment_modality_id = ?", row);
        adminJdbc().update("update enrollment_modality set next_charge_ref = date '2026-10-01', next_charge_due_on = date '2026-10-15' where id = ?", row);
        var again = run(generation, t);
        assertThat(again.created()).isZero();
        assertThat(again.skippedExisting()).isEqualTo(1);
        assertThat(charges(row)).hasSize(1);

        UUID student = student(row);
        assertThatThrownBy(() -> adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, reference_month, due_on, amount_cents) "
                + "values (?, ?, ?, ?, 'MONTHLY', ?, date '2026-10-01', date '2026-10-20', 1)", UUID.randomUUID(), t.orgId(), t.unitId(), student, row))
                .hasMessageContaining("charge_monthly_once");
    }

    @Test
    @DisplayName("CA12: bolsa de 100%, desconto fixo maior que o plano e modalidade encerrada não geram, o ponteiro avança; benefício vencido ou encerrado não desconta")
    void zeroValueAndEnded() {
        var t = tenant();
        UUID full = row(t, 15, 20000), fixed = row(t, 15, 20000), ended = row(t, 15, 20000), expired = row(t, 15, 20000), over = row(t, 15, 20000);
        var org = t.orgId();
        BillingFixtures.benefit(adminJdbc(), org, t.unitId(), student(full), full, "PERCENT", 100, null, "ACTIVE");
        BillingFixtures.benefit(adminJdbc(), org, t.unitId(), student(fixed), fixed, "AMOUNT", 25000, null, "ACTIVE");
        BillingFixtures.benefit(adminJdbc(), org, t.unitId(), student(expired), expired, "PERCENT", 50, "2026-10-01", "ACTIVE");
        BillingFixtures.benefit(adminJdbc(), org, t.unitId(), student(over), over, "PERCENT", 50, "2026-12-05", "ENDED");
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id = ?", ended);

        var result = run(at("2026-10-05T12:00:00Z"), t);
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.skippedZero()).isEqualTo(2);
        assertThat(charges(full)).isEmpty();
        assertThat(adminJdbc().queryForObject("select status from enrollment_modality where id = ?", String.class, full)).isEqualTo("ACTIVE");
        assertThat(adminJdbc().queryForObject("select count(*) from enrollment_modality where student_id = ? and status = 'ACTIVE'", Long.class, student(full))).isPositive();
        assertThat(charges(fixed)).isEmpty();
        assertThat(charges(ended)).isEmpty();
        assertThat(charges(expired).get(0).get("amount_cents")).isEqualTo(20000L);
        assertThat(charges(over).get(0).get("amount_cents")).isEqualTo(20000L);
        assertThat(adminJdbc().queryForObject("select next_charge_due_on from enrollment_modality where id = ?", LocalDate.class, full)).isEqualTo(LocalDate.parse("2026-11-15"));
    }

    @Test
    @DisplayName("CA2, CA12: benefício de 25% válido desconta e grava a composição")
    void benefitComposition() {
        var t = tenant();
        UUID row = row(t, 15, 20000);
        UUID benefit = BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(row), row, "PERCENT", 25, "2026-12-31", "ACTIVE");
        run(at("2026-10-05T12:00:00Z"), t);
        var c = charges(row).get(0);
        assertThat(c.get("plan_cents")).isEqualTo(20000L);
        assertThat(c.get("benefit_cents")).isEqualTo(5000L);
        assertThat(c.get("amount_cents")).isEqualTo(15000L);
        assertThat(c.get("benefit_id")).isEqualTo(benefit);
    }

    @Test
    @DisplayName("CA13: troca de plano agendada vale na primeira mensalidade, vira o plano da modalidade e a seguinte usa o novo")
    void scheduledPlan() {
        var t = tenant();
        UUID row = row(t, 15, 20000);
        UUID next = BillingFixtures.otherPlan(adminJdbc(), t.orgId(), t.unitId(), row, 30000);
        adminJdbc().update("update enrollment_modality set next_plan_id = ? where id = ?", next, row);
        run(at("2026-10-05T12:00:00Z"), t);
        var c = charges(row).get(0);
        assertThat(c.get("amount_cents")).isEqualTo(30000L);
        assertThat(c.get("plan_id")).isEqualTo(next);
        var m = adminJdbc().queryForMap("select plan_id, next_plan_id from enrollment_modality where id = ?", row);
        assertThat(m.get("plan_id")).isEqualTo(next);
        assertThat(m.get("next_plan_id")).isNull();

        run(at("2026-11-05T12:00:00Z"), t);
        assertThat(charges(row).get(1).get("amount_cents")).isEqualTo(30000L);
        assertThat(charges(row).get(1).get("plan_id")).isEqualTo(next);
    }

    @Test
    @DisplayName("CA14: adulto paga como ele mesmo, menor com dois responsáveis paga pelo financeiro, menor sem responsável financeiro falha")
    void payer() {
        var t = tenant();
        UUID adult = row(t, 15, 20000);
        UUID minorStudent = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Menor", "2015-03-03");
        UUID minor = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), minorStudent, 15, 20000);
        BillingFixtures.guardian(adminJdbc(), t.orgId(), minorStudent, false);
        UUID financial = BillingFixtures.guardian(adminJdbc(), t.orgId(), minorStudent, true);
        run(at("2026-10-05T12:00:00Z"), t);
        assertThat(charges(adult).get(0).get("payer_guardian_id")).isNull();
        assertThat(charges(minor).get(0).get("payer_guardian_id")).isEqualTo(financial);

        UUID orphanStudent = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Sem Responsavel", "2015-03-03");
        BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), orphanStudent, 15, 20000);
        assertThatThrownBy(() -> run(at("2026-11-05T12:00:00Z"), t)).isInstanceOf(IllegalStateException.class).hasMessageContaining("financial guardian");
    }
}
