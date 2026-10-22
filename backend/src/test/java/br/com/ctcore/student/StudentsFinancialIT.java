package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-01 (R10, R11, D5): situação financeira na lista de alunos, filtro Em atraso e contagem; nunca para o Professor. */
class StudentsFinancialIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private void charge(Tenant t, UUID student, String status, String due) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, amount_cents, status) "
                + "values (?, ?, ?, ?, 'ONE_OFF', 'Kimono', ?::date, 1000, ?)", id, t.orgId(), t.unitId(), student, due, status);
    }

    private JsonNode body(String path, AccountPrincipal who) throws Exception {
        var r = call(MockMvcRequestBuilders.get(path), who).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private List<String> pairs(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.get("items").forEach(i -> out.add(i.get("name").asText().replaceAll("[0-9a-f-]{36}", "") + ":" + i.get("financialStatus").asText()));
        return out;
    }

    @Test
    @DisplayName("CA12: em atraso, só pago e sem cobrança; o filtro traz só o primeiro e a contagem é 1; cancelada e em conferência não atrasam")
    void list() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID late = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Atrasado"), paid = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pago"),
                clean = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Limpo"), other = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Outros");
        charge(t, late, "OPEN", "2020-01-10");
        charge(t, paid, "PAID", "2020-01-10");
        charge(t, other, "CANCELED", "2020-01-10");
        charge(t, other, "IN_REVIEW", "2020-01-10");
        charge(t, other, "OPEN", "2999-01-10");

        JsonNode all = body("/api/v1/students", c);
        assertThat(pairs(all)).hasSize(4).contains("Atrasado:OVERDUE", "Pago:CURRENT", "Limpo:CURRENT", "Outros:CURRENT");
        assertThat(all.get("counts").get("overdue").asInt()).isEqualTo(1);
        JsonNode filtered = body("/api/v1/students?financial=OVERDUE", c);
        assertThat(pairs(filtered)).containsExactly("Atrasado:OVERDUE");
        assertThat(filtered.get("total").asInt()).isEqualTo(1);
        assertThat(filtered.get("counts").get("overdue").asInt()).isEqualTo(1);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students?financial=X"), c).getResponse().getStatus()).isEqualTo(400);
        assertThat(clean).isNotNull();
    }

    @Test
    @DisplayName("CA13: o Professor não lista alunos (403) e a lista simples e a ficha não trazem financialStatus")
    void teacher() throws Exception {
        Tenant t = tenant();
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Atrasado");
        charge(t, s, "OPEN", "2020-01-10");

        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students"), who(t, Role.TEACHER)).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students?financial=OVERDUE"), who(t, Role.TEACHER)).getResponse().getStatus()).isEqualTo(403);
        var c = who(t, Role.COORDINATOR);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students/" + s), c).getResponse().getContentAsString()).doesNotContain("financialStatus");
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students/" + s + "/record"), c).getResponse().getContentAsString()).doesNotContain("financialStatus");
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students/simple"), c).getResponse().getContentAsString()).doesNotContain("financialStatus");
    }
}
