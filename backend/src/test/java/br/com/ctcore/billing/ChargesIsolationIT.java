package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-01 (CA4, CA5, CA14): isolamento entre organizações e entre perfis em GET /charges e na lista de alunos, com o filtro e com a RLS. */
class ChargesIsolationIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private UUID lateStudent(Tenant t, String name) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), name, "1990-01-01");
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, amount_cents, status) "
                + "values (?, ?, ?, ?, 'ONE_OFF', 'Kimono', date '2020-01-10', 1000, 'OPEN')", UUID.randomUUID(), t.orgId(), t.unitId(), s);
        return s;
    }

    private JsonNode body(String path, AccountPrincipal who) throws Exception {
        var r = call(MockMvcRequestBuilders.get(path), who).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    @Test
    @DisplayName("CA14: cada organização vê e conta só as cobranças e os alunos em atraso dela, no filtro e na RLS")
    void organizations() throws Exception {
        Tenant a = tenant(), b = tenant();
        lateStudent(a, "Aluno A");
        lateStudent(b, "Aluno B");

        for (Role role : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR}) {
            JsonNode charges = body("/api/v1/charges", who(a, role));
            assertThat(charges.get("items")).hasSize(1);
            assertThat(charges.get("items").get(0).get("student").get("name").asText()).isEqualTo("Aluno A");
            assertThat(charges.get("totals").get("count").asInt()).isEqualTo(1);
            assertThat(charges.get("counts").get("overdue").asInt()).isEqualTo(1);
            JsonNode students = body("/api/v1/students?financial=OVERDUE", who(a, role));
            assertThat(students.get("total").asInt()).isEqualTo(1);
            assertThat(students.get("counts").get("overdue").asInt()).isEqualTo(1);
        }
        // Unidade da outra organização: 404, sem vazar que existe.
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges?unitId=" + b.unitId()), who(a, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(404);
        // RLS: sem nenhum filtro no SQL, a organização da sessão só enxerga as próprias linhas.
        var principal = who(b, Role.COORDINATOR);
        try (var ignored = OrganizationContext.open(principal)) {
            long rows = transactions.execute(() -> jdbc.sql("select count(*) from charge").query(Long.class).single());
            assertThat(rows).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("CA5: perfis sem cobranças recebem 403 e o Administrador sem unidade na lista não vê as de outra unidade da organização")
    void profiles() throws Exception {
        Tenant a = tenant();
        lateStudent(a, "Aluno");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(MockMvcRequestBuilders.get("/api/v1/charges"), who(a, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        UUID other = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", other, a.orgId(), "u" + other.toString().substring(0, 12));
        var admin = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), a.orgId(), Role.ADMINISTRATOR, java.util.Set.of(other));
        assertThat(body("/api/v1/charges", admin).get("items")).isEmpty();
        assertThat(body("/api/v1/charges", admin).get("counts").get("overdue").asInt()).isZero();
    }
}
