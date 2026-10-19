package br.com.ctcore.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MOD-01: listagem, ativação, pedidos, permissões, isolamento e auditoria, com banco real e a porta de uso simulada. */
class ModalityIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoBean ModalityUsage usage;

    /** O banco é compartilhado: pedidos prendem a conta por chave estrangeira e quebrariam os testes que apagam contas. */
    @AfterEach
    void cleanModalityTables() {
        adminJdbc().update("delete from modality_request");
        adminJdbc().update("delete from graduation_level");
        adminJdbc().update("delete from organization_modality");
    }

    private static MockHttpServletRequestBuilder list() {
        return MockMvcRequestBuilders.get("/api/v1/modalities");
    }

    private static MockHttpServletRequestBuilder activate(UUID id, String body) {
        return MockMvcRequestBuilders.put("/api/v1/modalities/" + id + "/activation").contentType("application/json").content(body);
    }

    private static MockHttpServletRequestBuilder requests() {
        return MockMvcRequestBuilders.get("/api/v1/modalities/requests");
    }

    private static MockHttpServletRequestBuilder ask(String body) {
        return MockMvcRequestBuilders.post("/api/v1/modalities/requests").contentType("application/json").content(body);
    }

    /** Principal com conta real, necessária para a chave estrangeira do pedido. */
    private AccountPrincipal member(Tenant t, Role role) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Synthetic', 'ACTIVE')", account, account + "@synthetic.example");
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(t.unitId()));
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

    private int audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, t.orgId(), action);
    }

    @Test
    @DisplayName("CA2, CA3, CA5: lista 14 inativas, ativa duas em ordem, repete sem nova auditoria, desativa; id inexistente 404; sem active 400")
    void listAndActivate() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        var first = body(send(list(), coordinator));
        assertThat(first).hasSize(14);
        first.forEach(n -> assertThat(n.get("active").asBoolean()).isFalse());
        assertThat(first.get(0).get("canManage").asBoolean()).isTrue();

        var response = send(activate(id("Judô"), "{\"active\":true}"), coordinator);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(body(response).get("active").asBoolean()).isTrue();
        assertThat(send(activate(id("Judô"), "{\"active\":true}"), coordinator).getStatus()).isEqualTo(200);
        assertThat(audits(t, "modality.activated")).isEqualTo(1);
        send(activate(id("Boxe"), "{\"active\":true}"), coordinator);
        var after = body(send(list(), coordinator));
        List<String> active = after.findValues("active").stream().map(JsonNode::asText).toList();
        assertThat(active.stream().filter("true"::equals)).hasSize(2);
        assertThat(after.findValuesAsText("name")).isSorted();

        assertThat(body(send(activate(id("Judô"), "{\"active\":false}"), coordinator)).get("active").asBoolean()).isFalse();
        assertThat(audits(t, "modality.deactivated")).isEqualTo(1);
        assertThat(send(activate(UUID.randomUUID(), "{\"active\":true}"), coordinator).getStatus()).isEqualTo(404);
        assertThat(send(activate(id("Judô"), "{}"), coordinator).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA6: turma ou aluno ativo recusa a desativação com 409 e contagens; sem uso desativa")
    void inUse() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        UUID judo = id("Judô");
        send(activate(judo, "{\"active\":true}"), coordinator);
        when(usage.activeClasses(judo)).thenReturn(2);
        when(usage.activeStudents(judo)).thenReturn(5);
        var response = send(activate(judo, "{\"active\":false}"), coordinator);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("MODALITY_IN_USE");
        var fields = body(response).findValue("fields");
        assertThat(fields.findParents("field").stream().filter(f -> f.get("field").asText().equals("activeClasses")).findFirst().orElseThrow().get("message").asText()).isEqualTo("2");
        assertThat(fields.findParents("field").stream().filter(f -> f.get("field").asText().equals("activeStudents")).findFirst().orElseThrow().get("message").asText()).isEqualTo("5");
        assertThat(adminJdbc().queryForObject("select active from organization_modality where modality_id = ? and organization_id = ?", Boolean.class, judo, t.orgId())).isTrue();
        when(usage.activeClasses(judo)).thenReturn(0);
        when(usage.activeStudents(judo)).thenReturn(1);
        assertThat(send(activate(judo, "{\"active\":false}"), coordinator).getStatus()).isEqualTo(409);
        when(usage.activeStudents(judo)).thenReturn(0);
        assertThat(send(activate(judo, "{\"active\":false}"), coordinator).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA7: pedido válido 201 com auditoria; tamanhos 400; duplicado aberto 409; nome do catálogo 409")
    void requestRules() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        var created = send(ask("{\"name\":\"Sambo\",\"note\":\"Temos turma\"}"), coordinator);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(body(created).get("status").asText()).isEqualTo("OPEN");
        assertThat(audits(t, "modality.requested")).isEqualTo(1);
        assertThat(send(ask("{\"name\":\"a\"}"), coordinator).getStatus()).isEqualTo(400);
        assertThat(send(ask("{\"name\":\"" + "x".repeat(61) + "\"}"), coordinator).getStatus()).isEqualTo(400);
        assertThat(send(ask("{\"name\":\"Sumô\",\"note\":\"" + "x".repeat(301) + "\"}"), coordinator).getStatus()).isEqualTo(400);
        var again = send(ask("{\"name\":\"sambo\"}"), coordinator);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(again.getContentAsString()).contains("REQUEST_ALREADY_OPEN");
        for (String name : new String[] {"judô", "Judo", "KARATE"}) {
            var r = send(ask("{\"name\":\"" + name + "\"}"), coordinator);
            assertThat(r.getStatus()).as(name).isEqualTo(409);
            assertThat(r.getContentAsString()).contains("ALREADY_IN_CATALOG");
        }
    }

    @Test
    @DisplayName("CA4: perfis e anônimo; Administrador só lê")
    void permissions() throws Exception {
        var t = tenant();
        var judo = id("Judô");
        assertThat(send(list(), null).getStatus()).isEqualTo(401);
        assertThat(send(activate(judo, "{\"active\":true}"), null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = member(t, role);
            assertThat(send(activate(judo, "{\"active\":true}"), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(ask("{\"name\":\"Sambo\"}"), who).getStatus()).as(role.name()).isEqualTo(403);
            var read = send(list(), who);
            assertThat(read.getStatus()).as(role.name()).isEqualTo(role == Role.ADMINISTRATOR ? 200 : 403);
            if (role == Role.ADMINISTRATOR) {
                assertThat(body(read).get(0).get("canManage").asBoolean()).isFalse();
                assertThat(send(requests(), who).getStatus()).isEqualTo(200);
            } else {
                assertThat(send(requests(), who).getStatus()).isEqualTo(403);
            }
        }
        assertThat(adminJdbc().queryForObject("select count(*) from organization_modality where organization_id = ?", Integer.class, t.orgId())).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from modality_request where organization_id = ?", Integer.class, t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA8, CA9, CA10: isolamento entre organizações, pedidos do mais novo ao mais antigo, auditoria sem dados pessoais")
    void isolationAndAudit() throws Exception {
        var a = tenant();
        var b = tenant();
        var ca = member(a, Role.COORDINATOR);
        var cb = member(b, Role.COORDINATOR);
        send(activate(id("Judô"), "{\"active\":true}"), ca);
        send(activate(id("Judô"), "{\"active\":true}"), cb);
        send(activate(id("Boxe"), "{\"active\":true}"), cb);
        send(ask("{\"name\":\"Sambo\"}"), ca);
        send(ask("{\"name\":\"Sumô\"}"), ca);
        send(ask("{\"name\":\"Aikido\"}"), cb);
        var mine = body(send(requests(), ca));
        assertThat(mine).hasSize(2);
        assertThat(mine.get(0).get("name").asText()).isEqualTo("Sumô");
        assertThat(mine.get(1).get("name").asText()).isEqualTo("Sambo");
        assertThat(body(send(requests(), member(a, Role.ADMINISTRATOR))).size()).isEqualTo(2);
        assertThat(body(send(list(), ca)).findValues("active").stream().filter(JsonNode::asBoolean).count()).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from organization_modality where organization_id = ?", Integer.class, b.orgId())).isEqualTo(2);
        var audit = adminJdbc().queryForList("select actor_id::text || actor_profile || coalesce(before_data::text, '') || coalesce(after_data::text, '') "
                + "from audit_log where organization_id = ? and action like 'modality.%'", String.class, a.orgId());
        assertThat(audit).hasSize(3);
        audit.forEach(s -> assertThat(s).doesNotContain("@", "password", "token"));
    }
}
