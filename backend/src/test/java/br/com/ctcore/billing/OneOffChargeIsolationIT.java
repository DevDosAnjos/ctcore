package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-11 (CA8): a organização B nunca cria avulsa para aluno da A nem enxerga a dela, no filtro e na RLS; perfis sem permissão não criam. */
class OneOffChargeIsolationIT extends OrganizationBase {
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

    private int post(UUID student, Tenant unitOf, AccountPrincipal who) throws Exception {
        String body = "{\"unitId\":\"" + unitOf.unitId() + "\",\"description\":\"Kimono\",\"amountCents\":100,\"dueOn\":\"" + LocalDate.now().plusDays(2) + "\"}";
        return call(MockMvcRequestBuilders.post("/api/v1/students/" + student + "/charges").contentType("application/json").content(body), who).getResponse().getStatus();
    }

    @Test
    @DisplayName("CA8: B recebe 404 com a unidade da A ou com a sua, a RLS esconde e barra a linha da A e perfis sem permissão recebem 403")
    void isolated() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), a.orgId(), a.unitId(), "Aluno", "1990-01-01");
        BillingFixtures.modality(adminJdbc(), a.orgId(), a.unitId(), student, 15, 20000);
        UUID existing = BillingFixtures.oneOff(adminJdbc(), a.orgId(), a.unitId(), student, 100, "OPEN");

        var adminB = account(b, Role.ADMINISTRATOR);
        assertThat(post(student, a, adminB)).isEqualTo(404);
        assertThat(post(student, b, adminB)).isEqualTo(404);
        assertThat(post(student, a, account(b, Role.COORDINATOR))).isEqualTo(404);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) assertThat(post(student, a, account(a, role))).as(role.name()).isEqualTo(403);
        assertThat(adminJdbc().queryForObject("select count(*) from charge", Long.class)).isEqualTo(1);

        try (var ignored = OrganizationContext.open(adminB)) {
            assertThat(transactions.execute(() -> jdbc.sql("select count(*) from charge where id = :id").param("id", existing).query(Long.class).single())).isZero();
        }
    }
}
