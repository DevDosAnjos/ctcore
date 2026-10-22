package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
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
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-MAT-05, RN-MAT-07, RN-COB-05, RN-MAT-19: a primeira cobrança da conclusão (mensalidade, taxa, ponteiro da segunda) com datas fixas. */
class FirstChargesIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired AuditService audit;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired ScopedAudit scopedAudit;

    @AfterEach
    void clean() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from enrollment_start");
        BillingFixtures.cleanAll(adminJdbc());
    }

    private AccountPrincipal admin(Tenant t) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(t.unitId()));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), "gen-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private UUID start(UUID row, long fee, String second) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into enrollment_start (id, organization_id, enrollment_id, unit_id, concluded_at, fee_cents, second_due_on) "
                + "select ?, organization_id, enrollment_id, unit_id, now(), ?, ?::date from enrollment_modality where id = ?", id, fee, second, row);
        return id;
    }

    private List<FirstCharges.Charge> create(Tenant t, UUID row, UUID start, String today, long fee, String second) {
        var terms = new FirstCharges.UnitTerms(t.unitId(), start, LocalDate.parse(today), 5, fee, LocalDate.parse(second));
        AccountPrincipal who = admin(t);
        try (var ignored = br.com.ctcore.isolation.OrganizationContext.open(who)) {
            return transactions.execute(() -> new FirstCharges(jdbc, audit).create(who, student(row), List.of(row), Map.of(t.unitId(), terms), null));
        }
    }

    private MonthlyGeneration.Run generate(Tenant t, String instant) {
        var generation = new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
        return transactions.provision(t.orgId(), () -> generation.runUnit(t.orgId(), t.unitId()));
    }

    private UUID row(Tenant t, String birth) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", birth);
        return BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 25, 20000);
    }

    @Test
    @DisplayName("CA18, CA19, CA20, CA23: mensalidade cheia com benefício, taxa ligada ao início, ponteiro da segunda e auditoria sem dados pessoais")
    void monthlyFeeAndSecond() {
        var t = tenant();
        UUID row = row(t, "1990-01-01");
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(row), row, "PERCENT", 25, null, "ACTIVE");
        UUID start = start(row, 5000, "2026-11-25");
        var charges = create(t, row, start, "2026-10-21", 5000, "2026-11-25");
        assertThat(charges).extracting(FirstCharges.Charge::type).containsExactly("MONTHLY", "ENROLLMENT_FEE");
        var monthly = adminJdbc().queryForMap("select * from charge where type = 'MONTHLY' and enrollment_modality_id = ?", row);
        assertThat(monthly).containsEntry("plan_cents", 20000L).containsEntry("benefit_cents", 5000L).containsEntry("amount_cents", 15000L)
                .containsEntry("tolerance_days", 5).containsEntry("origin", "ENROLLMENT").containsEntry("status", "OPEN");
        assertThat(monthly.get("reference_month").toString()).isEqualTo("2026-10-01");
        assertThat(monthly.get("due_on").toString()).isEqualTo("2026-10-21");
        assertThat(monthly.get("payer_guardian_id")).isNull();
        var fee = adminJdbc().queryForMap("select * from charge where type = 'ENROLLMENT_FEE'");
        assertThat(fee).containsEntry("amount_cents", 5000L).containsEntry("tolerance_days", 5).containsEntry("enrollment_start_id", start);
        assertThat(fee.get("due_on").toString()).isEqualTo("2026-10-21");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'charge.created' and organization_id = ? and after_data::text not like '%Aluno%'",
                Long.class, t.orgId())).isEqualTo(2);
        // nunca duas taxas do mesmo início
        assertThatThrownBy(() -> create(t, row, start, "2026-10-21", 5000, "2026-11-25")).isInstanceOf(RuntimeException.class);

        // CA20: a geração diária cria a segunda 10 dias antes, com referência de novembro
        assertThat(generate(t, "2026-11-14T12:00:00Z").created()).isZero();
        assertThat(generate(t, "2026-11-15T12:00:00Z").created()).isEqualTo(1);
        var second = adminJdbc().queryForMap("select reference_month, due_on from charge where type = 'MONTHLY' and reference_month = date '2026-11-01'");
        assertThat(second.get("due_on").toString()).isEqualTo("2026-11-25");
    }

    @Test
    @DisplayName("CA20: vencimento no mesmo mês da primeira cobrança usa o mês seguinte como referência, sem colidir")
    void sameMonthReference() {
        var t = tenant();
        UUID row = row(t, "1990-01-01");
        create(t, row, start(row, 0, "2026-10-28"), "2026-10-01", 0, "2026-10-28");
        var pointer = adminJdbc().queryForMap("select next_charge_ref, next_charge_due_on from enrollment_modality where id = ?", row);
        assertThat(pointer.get("next_charge_ref").toString()).isEqualTo("2026-11-01");
        assertThat(pointer.get("next_charge_due_on").toString()).isEqualTo("2026-10-28");
        assertThat(generate(t, "2026-10-18T12:00:00Z").created()).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from charge where reference_month = date '2026-11-01' and due_on = date '2026-10-28'", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA18: bolsa de 100% não gera a mensalidade, mas o ponteiro fica; menor paga pelo responsável financeiro e, sem ele, desfaz")
    void zeroAndMinor() {
        var t = tenant();
        UUID full = row(t, "1990-01-01");
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(full), full, "PERCENT", 100, null, "ACTIVE");
        assertThat(create(t, full, start(full, 0, "2026-11-25"), "2026-10-21", 0, "2026-11-25")).isEmpty();
        assertThat(adminJdbc().queryForObject("select next_charge_due_on from enrollment_modality where id = ?", LocalDate.class, full)).isEqualTo(LocalDate.parse("2026-11-25"));

        UUID minor = row(t, "2020-01-01");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), student(minor), true);
        create(t, minor, start(minor, 0, "2026-11-25"), "2026-10-21", 0, "2026-11-25");
        assertThat(adminJdbc().queryForObject("select payer_guardian_id from charge where enrollment_modality_id = ?", UUID.class, minor)).isEqualTo(guardian);

        UUID orphan = row(t, "2020-01-01");
        UUID orphanStart = start(orphan, 0, "2026-11-25");
        assertThatThrownBy(() -> create(t, orphan, orphanStart, "2026-10-21", 0, "2026-11-25")).isInstanceOf(IllegalStateException.class);
    }
}
