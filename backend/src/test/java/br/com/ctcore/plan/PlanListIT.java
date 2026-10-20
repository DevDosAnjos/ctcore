package br.com.ctcore.plan;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-PLA-02: tabela de planos, filtros, perfis, alcance e isolamento (API e RLS), com banco real. */
class PlanListIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from plan");
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/plans" + query), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID modality(String name) {
        return adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID plan(Tenant t, UUID unit, String modality, String name, long cents, Integer freq, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, weekly_frequency, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), unit, modality(modality), name, Modalities.normalize(name), cents, freq, status);
        return id;
    }

    private List<String> names(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.get("items").forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    @Test
    @DisplayName("RN-PLA-02: CA1, CA3: lista ordenada por modalidade e nome, com campos do contrato, frequência nula e sem presença; size inválido 400")
    void list() throws Exception {
        var t = tenant();
        plan(t, t.unitId(), "Judô", "Mensal", 20000, 2, "ACTIVE");
        plan(t, t.unitId(), "Boxe", "Zeta", 15000, null, "ACTIVE");
        plan(t, t.unitId(), "Boxe", "Alfa", 30000, 3, "INACTIVE");
        var r = get("", who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).isEqualTo(200);
        var b = body(r);
        assertThat(names(b)).containsExactly("Alfa", "Zeta", "Mensal");
        assertThat(b.get("total").asLong()).isEqualTo(3);
        assertThat(b.get("page").asInt()).isEqualTo(1);
        assertThat(b.get("size").asInt()).isEqualTo(25);
        var first = b.get("items").get(0);
        assertThat(first.get("valueCents").isIntegralNumber()).isTrue();
        assertThat(first.get("valueCents").asLong()).isEqualTo(30000);
        assertThat(first.get("studentsUsing").asInt()).isZero();
        assertThat(first.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(first.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(first.get("modality").get("name").asText()).isEqualTo("Boxe");
        assertThat(b.get("items").get(1).get("weeklyFrequency").isNull()).isTrue();
        assertThat(r.getContentAsString().toLowerCase()).doesNotContain("attend", "presen", "lesson");
        assertThat(get("?size=10", who(t, Role.COORDINATOR)).getStatus()).isEqualTo(400);
        assertThat(names(body(get("?size=25&page=2", who(t, Role.COORDINATOR))))).isEmpty();
    }

    @Test
    @DisplayName("CA2: filtros por modalidade, unidade, situação e nome, sozinhos e combinados; status inválido 400")
    void filters() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        plan(t, t.unitId(), "Judô", "Judô Mensal", 20000, 2, "ACTIVE");
        plan(t, second, "Judô", "Judô Anual", 15000, 3, "INACTIVE");
        plan(t, t.unitId(), "Boxe", "Boxe Mensal", 30000, null, "ACTIVE");
        var c = who(t, Role.COORDINATOR);
        assertThat(names(body(get("?modalityId=" + modality("Judô"), c)))).containsExactly("Judô Anual", "Judô Mensal");
        assertThat(names(body(get("?unitId=" + second, c)))).containsExactly("Judô Anual");
        assertThat(names(body(get("?status=ACTIVE", c)))).containsExactly("Boxe Mensal", "Judô Mensal");
        assertThat(names(body(get("?q=JUDO", c)))).containsExactly("Judô Anual", "Judô Mensal");
        assertThat(names(body(get("?q=judo&status=ACTIVE&unitId=" + t.unitId() + "&modalityId=" + modality("Judô"), c)))).containsExactly("Judô Mensal");
        assertThat(names(body(get("?q=judo&modalityId=" + modality("Boxe"), c)))).isEmpty();
        assertThat(names(body(get("?q=%25", c)))).isEmpty();
        assertThat(get("?status=XYZ", c).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA4: Administrador só vê as unidades dele; demais perfis 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        plan(t, t.unitId(), "Judô", "Minha", 10000, 2, "ACTIVE");
        plan(t, second, "Judô", "Alheia", 20000, 2, "ACTIVE");
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(names(body(get("", admin)))).containsExactly("Minha");
        assertThat(names(body(get("?unitId=" + second, admin)))).isEmpty();
        assertThat(body(get("?unitId=" + second, admin)).get("total").asLong()).isZero();
        assertThat(names(body(get("", who(t, Role.COORDINATOR))))).containsExactly("Alheia", "Minha");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("", null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA5: plano de outra organização não aparece; RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        plan(a, a.unitId(), "Judô", "Meu", 10000, 2, "ACTIVE");
        plan(b, b.unitId(), "Judô", "Dele", 99000, 2, "ACTIVE");
        assertThat(names(body(get("", who(a, Role.COORDINATOR))))).containsExactly("Meu");
        assertThat(names(body(get("?unitId=" + b.unitId(), who(a, Role.COORDINATOR))))).isEmpty();

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from plan where organization_id = '" + b.orgId() + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
