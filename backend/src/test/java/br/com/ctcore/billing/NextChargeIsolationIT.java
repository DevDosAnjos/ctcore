package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-02 (CA10): a organização B nunca vê nem ajusta a modalidade da A, no filtro e na RLS; perfis sem permissão não ajustam. */
class NextChargeIsolationIT extends OrganizationBase {
    private static final LocalDate REF = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(2);

    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "gen-" + p.accountId() + "@example.com");
        return p;
    }

    private int[] attempt(UUID student, UUID row, AccountPrincipal who) throws Exception {
        String base = "/api/v1/students/" + student + "/modalities/" + row + "/next-charge";
        return new int[] {
                call(MockMvcRequestBuilders.patch(base).contentType("application/json").content("{\"dueOn\":\"" + REF.withDayOfMonth(28) + "\"}"), who).getResponse().getStatus(),
                call(MockMvcRequestBuilders.post(base + "/cancel").contentType("application/json").content("{\"reason\":\"x\"}"), who).getResponse().getStatus()};
    }

    @Test
    @DisplayName("CA10: outra organização recebe 404 nas duas rotas e a RLS esconde a linha mesmo sem filtro; Professor, Aluno, Responsável e anônimo não ajustam")
    void isolated() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), a.orgId(), a.unitId(), "Aluno", "1990-01-01");
        UUID row = BillingFixtures.modality(adminJdbc(), a.orgId(), a.unitId(), student, 15, 20000);
        adminJdbc().update("update enrollment_modality set next_charge_ref = ?, next_charge_due_on = ? where id = ?", REF, REF.withDayOfMonth(15), row);

        var adminB = account(b, Role.ADMINISTRATOR);
        assertThat(attempt(student, row, adminB)).containsExactly(404, 404);
        assertThat(attempt(student, row, account(b, Role.COORDINATOR))).containsExactly(404, 404);
        assertThat(attempt(student, row, null)).containsExactly(401, 401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(attempt(student, row, account(a, role))).as(role.name()).containsExactly(403, 403);
        }
        assertThat(adminJdbc().queryForObject("select count(*) from charge", Long.class)).isZero();
        assertThat(adminJdbc().queryForObject("select next_charge_due_on from enrollment_modality where id = ?", LocalDate.class, row)).isEqualTo(REF.withDayOfMonth(15));
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id in (?, ?) and action in ('charge.next_due_changed', 'charge.canceled', 'charge.generation_adjusted')",
                Long.class, a.orgId(), b.orgId())).isZero();

        try (var ignored = OrganizationContext.open(adminB)) {
            assertThat(transactions.execute(() -> jdbc.sql("select count(*) from enrollment_modality where id = :id").param("id", row).query(Long.class).single())).isZero();
            assertThat(transactions.execute(() -> jdbc.sql("update enrollment_modality set next_charge_due_on = current_date where id = :id").param("id", row).update())).isZero();
        }
    }
}
