package br.com.ctcore.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MOD-03: lista, marcação, idempotência, modalidade inativa, perfis, isolamento (API e RLS) e auditoria, com banco real. */
class UnitModalityIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void clean() {
        adminJdbc().update("delete from unit_modality");
        adminJdbc().update("delete from graduation_level");
        adminJdbc().update("delete from organization_modality");
    }

    private static MockHttpServletRequestBuilder list(UUID unit) {
        return MockMvcRequestBuilders.get("/api/v1/units/" + unit + "/modalities");
    }

    private static MockHttpServletRequestBuilder offer(UUID unit, UUID modality, String body) {
        return MockMvcRequestBuilders.put("/api/v1/units/" + unit + "/modalities/" + modality).contentType("application/json").content(body);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        return call(request, who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID id(String name) {
        return adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
    }

    private void activate(Tenant t, String... names) {
        for (String name : names) {
            adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) "
                    + "on conflict (organization_id, modality_id) do update set active = true", t.orgId(), id(name));
        }
    }

    private int audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, t.orgId(), action);
    }

    private UUID secondUnit(Tenant t) {
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Second', ?)", second, t.orgId(), "second-" + second);
        adminJdbc().update("insert into unit_settings (unit_id, organization_id) values (?, ?)", second, t.orgId());
        return second;
    }

    @Test
    @DisplayName("CA1, CA2, CA3: lista só ativas em ordem, marca, repete sem nova auditoria, desmarca; inativa e inexistente 404; sem offered 400; guarda ao desativar")
    void listAndOffer() throws Exception {
        var t = tenant();
        activate(t, "Judô", "Boxe");
        var coordinator = who(t, Role.COORDINATOR);
        var first = body(send(list(t.unitId()), coordinator));
        assertThat(first.get("canManage").asBoolean()).isTrue();
        assertThat(first.get("items").findValuesAsText("name")).containsExactly("Boxe", "Judô");
        first.get("items").forEach(n -> assertThat(n.get("offered").asBoolean()).isFalse());

        var judo = id("Judô");
        var response = send(offer(t.unitId(), judo, "{\"offered\":true}"), coordinator);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).get("offered").asBoolean()).isTrue();
        assertThat(send(offer(t.unitId(), judo, "{\"offered\":true}"), coordinator).getStatus()).isEqualTo(200);
        assertThat(audits(t, "unit.modality.offered")).isEqualTo(1);
        assertThat(body(send(list(t.unitId()), coordinator)).get("items").get(1).get("offered").asBoolean()).isTrue();

        assertThat(send(offer(t.unitId(), id("Karatê"), "{\"offered\":true}"), coordinator).getStatus()).isEqualTo(404);
        assertThat(send(offer(t.unitId(), UUID.randomUUID(), "{\"offered\":true}"), coordinator).getStatus()).isEqualTo(404);
        assertThat(send(offer(t.unitId(), judo, "{}"), coordinator).getStatus()).isEqualTo(400);

        adminJdbc().update("update organization_modality set active = false where organization_id = ? and modality_id = ?", t.orgId(), judo);
        assertThat(body(send(list(t.unitId()), coordinator)).get("items").findValuesAsText("name")).containsExactly("Boxe");
        assertThat(send(offer(t.unitId(), judo, "{\"offered\":false}"), coordinator).getStatus()).isEqualTo(404);
        adminJdbc().update("update organization_modality set active = true where organization_id = ? and modality_id = ?", t.orgId(), judo);
        assertThat(body(send(list(t.unitId()), coordinator)).get("items").get(1).get("offered").asBoolean()).isTrue();

        assertThat(body(send(offer(t.unitId(), judo, "{\"offered\":false}"), coordinator)).get("offered").asBoolean()).isFalse();
        assertThat(audits(t, "unit.modality.withdrawn")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4: Administrador da unidade lê e marca; de outra unidade, Professor e portal 403; anônimo 401; nada muda")
    void permissions() throws Exception {
        var t = tenant();
        activate(t, "Judô");
        var judo = id("Judô");
        UUID other = secondUnit(t);
        assertThat(send(list(t.unitId()), null).getStatus()).isEqualTo(401);
        assertThat(send(offer(t.unitId(), judo, "{\"offered\":true}"), null).getStatus()).isEqualTo(401);

        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = who(t, role);
            assertThat(send(list(t.unitId()), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(offer(t.unitId(), judo, "{\"offered\":true}"), who).getStatus()).as(role.name()).isEqualTo(403);
        }
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(send(list(other), admin).getStatus()).isEqualTo(403);
        assertThat(send(offer(other, judo, "{\"offered\":true}"), admin).getStatus()).isEqualTo(403);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_modality", Integer.class)).isZero();

        assertThat(body(send(list(t.unitId()), admin)).get("canManage").asBoolean()).isTrue();
        assertThat(send(offer(t.unitId(), judo, "{\"offered\":true}"), admin).getStatus()).isEqualTo(200);
        assertThat(send(offer(other, judo, "{\"offered\":true}"), who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA5, CA6: unidade de outra organização 404 sem mudar nada; marcações separadas; RLS com ctcore_app; auditoria sem dado pessoal")
    void isolationAndAudit() throws Exception {
        var a = tenant();
        var b = tenant();
        activate(a, "Judô");
        activate(b, "Judô");
        var judo = id("Judô");
        var ca = who(a, Role.COORDINATOR);
        var cb = who(b, Role.COORDINATOR);
        assertThat(send(list(b.unitId()), ca).getStatus()).isEqualTo(404);
        assertThat(send(offer(b.unitId(), judo, "{\"offered\":true}"), ca).getStatus()).isEqualTo(404);
        assertThat(send(list(UUID.randomUUID()), ca).getStatus()).isEqualTo(404);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_modality", Integer.class)).isZero();

        send(offer(a.unitId(), judo, "{\"offered\":true}"), ca);
        assertThat(body(send(list(b.unitId()), cb)).get("items").get(0).get("offered").asBoolean()).isFalse();
        send(offer(b.unitId(), judo, "{\"offered\":true}"), cb);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_modality", Integer.class)).isEqualTo(2);

        var audit = adminJdbc().queryForList("select actor_id::text || actor_profile || coalesce(before_data::text, '') || coalesce(after_data::text, '') "
                + "from audit_log where organization_id = ? and action like 'unit.modality.%'", String.class, a.orgId());
        assertThat(audit).hasSize(1);
        audit.forEach(s -> assertThat(s).doesNotContain("@", "password", "token"));

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select 1 from unit_modality")) {
                assertThat(rows.next()).isFalse();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select organization_id from unit_modality")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isEqualTo(a.orgId());
                assertThat(rows.next()).isFalse();
            }
            assertThat(sql.executeUpdate("update unit_modality set offered = false where organization_id = '" + b.orgId() + "'")).isZero();
            assertThatThrownBy(() -> sql.executeUpdate("insert into unit_modality (organization_id, unit_id, modality_id, offered) values ('"
                    + b.orgId() + "', '" + b.unitId() + "', '" + id("Boxe") + "', true)")).hasMessageContaining("row-level security");
            app.rollback();
        }
    }
}
