package br.com.ctcore.modality;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MOD-02: cópia do padrão, leitura, gravação, validação, permissões, isolamento e auditoria, com banco real. */
class GraduationIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from modality_request");
        adminJdbc().update("delete from graduation_level");
        adminJdbc().update("delete from organization_modality");
    }

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

    private void activate(AccountPrincipal who, String name) throws Exception {
        assertThat(send(MockMvcRequestBuilders.put("/api/v1/modalities/" + id(name) + "/activation").contentType("application/json").content("{\"active\":true}"), who)
                .getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequestBuilder get(UUID modality) {
        return MockMvcRequestBuilders.get("/api/v1/modalities/" + modality + "/graduation");
    }

    private MockHttpServletRequestBuilder put(UUID modality, String body) {
        return MockMvcRequestBuilders.put("/api/v1/modalities/" + modality + "/graduation").contentType("application/json").content(body);
    }

    private static String level(String name) {
        return level(null, name);
    }

    private static String level(String id, String name) {
        return "{" + (id == null ? "" : "\"id\":\"" + id + "\",") + "\"name\":\"" + name + "\",\"color\":\"#112233\",\"maxDegree\":4,\"minAge\":16,\"kids\":false}";
    }

    private static String update(boolean enabled, boolean proof, String... levels) {
        return "{\"enabled\":" + enabled + ",\"requireOriginProof\":" + proof + ",\"levels\":[" + String.join(",", levels) + "]}";
    }

    private int audits(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'modality.graduation_updated'", Integer.class, t.orgId());
    }

    private int storedLevels(Tenant t, UUID modality) {
        return adminJdbc().queryForObject("select count(*) from graduation_level where organization_id = ? and modality_id = ?", Integer.class, t.orgId(), modality);
    }

    @Test
    @DisplayName("CA2, CA11: ativar copia o padrão uma vez, edição sobrevive a desativar e reativar, Judô sem padrão, lista traz graduationEnabled")
    void copyOnActivation() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        UUID bjj = id("Jiu-Jitsu");
        activate(coordinator, "Jiu-Jitsu");
        var view = body(send(get(bjj), coordinator));
        assertThat(view.get("enabled").asBoolean()).isFalse();
        assertThat(view.get("levels")).hasSize(19);
        assertThat(view.get("levels").get(0).get("name").asText()).isEqualTo("Cinza e Branca");

        assertThat(send(put(bjj, update(true, false, level("Única"))), coordinator).getStatus()).isEqualTo(200);
        send(MockMvcRequestBuilders.put("/api/v1/modalities/" + bjj + "/activation").contentType("application/json").content("{\"active\":false}"), coordinator);
        activate(coordinator, "Jiu-Jitsu");
        var again = body(send(get(bjj), coordinator));
        assertThat(again.get("levels")).hasSize(1);
        assertThat(again.get("enabled").asBoolean()).isTrue();

        activate(coordinator, "Judô");
        assertThat(body(send(get(id("Judô")), coordinator)).get("levels")).isEmpty();
        var list = body(send(MockMvcRequestBuilders.get("/api/v1/modalities"), coordinator));
        for (JsonNode n : list) assertThat(n.get("graduationEnabled").asBoolean()).isEqualTo(n.get("name").asText().equals("Jiu-Jitsu"));
    }

    @Test
    @DisplayName("CA3: mudar o padrão depois não altera a cópia da organização")
    void copyIsIndependent() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        activate(coordinator, "Jiu-Jitsu");
        adminJdbc().update("update modality_default_level set name = 'Alterada' where position = 0");
        try {
            assertThat(body(send(get(id("Jiu-Jitsu")), coordinator)).get("levels").get(0).get("name").asText()).isEqualTo("Cinza e Branca");
        } finally {
            adminJdbc().update("update modality_default_level set name = 'Cinza e Branca' where position = 0");
        }
    }

    @Test
    @DisplayName("CA5, CA8, CA10: grava, mantém ids, remove ausente, reordena com troca de nomes, repete sem nova auditoria, desliga mantendo níveis")
    void save() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        UUID judo = id("Judô");
        activate(coordinator, "Judô");
        assertThat(send(put(judo, update(false, false)), coordinator).getStatus()).isEqualTo(200);
        assertThat(audits(t)).isZero();

        var first = body(send(put(judo, update(true, true, level("Branca"), level("Azul"), level("Roxa"))), coordinator));
        assertThat(first.get("requireOriginProof").asBoolean()).isTrue();
        assertThat(first.get("canManage").asBoolean()).isTrue();
        String white = first.get("levels").get(0).get("id").asText(), blue = first.get("levels").get(1).get("id").asText();
        assertThat(audits(t)).isEqualTo(1);

        // Azul sobe, Branca renomeada com o nome que a outra tinha, Roxa fora, uma nova no fim.
        String body = update(true, true, level(blue, "Branca"), level(white, "Azul"), level("Verde"));
        var second = body(send(put(judo, body), coordinator));
        assertThat(second.findValuesAsText("name")).containsExactly("Branca", "Azul", "Verde");
        assertThat(second.get("levels").get(0).get("id").asText()).isEqualTo(blue);
        assertThat(second.get("levels").get(1).get("id").asText()).isEqualTo(white);
        assertThat(storedLevels(t, judo)).isEqualTo(3);
        assertThat(audits(t)).isEqualTo(2);
        var repeated = body(send(put(judo, update(true, true, level(blue, "Branca"), level(white, "Azul"), level(second.get("levels").get(2).get("id").asText(), "Verde"))), coordinator));
        assertThat(repeated.findValuesAsText("name")).containsExactly("Branca", "Azul", "Verde");
        assertThat(audits(t)).isEqualTo(2);

        var off = body(send(put(judo, update(false, true, level(blue, "Branca"))), coordinator));
        assertThat(off.get("enabled").asBoolean()).isFalse();
        assertThat(off.get("levels")).hasSize(1);
        assertThat(body(send(put(judo, update(false, false)), coordinator)).get("levels")).isEmpty();
        var audit = adminJdbc().queryForList("select actor_id::text || actor_profile || coalesce(before_data::text, '') || coalesce(after_data::text, '') from audit_log "
                + "where organization_id = ? and action = 'modality.graduation_updated'", String.class, t.orgId());
        assertThat(audit).hasSize(4);
        audit.forEach(s -> assertThat(s).doesNotContain("@", "password", "token"));
    }

    @Test
    @DisplayName("CA7: cada violação responde 400 no campo e nada é gravado; id de outra modalidade ou organização, 400")
    void validation() throws Exception {
        var t = tenant();
        var other = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        var foreign = member(other, Role.COORDINATOR);
        UUID judo = id("Judô");
        activate(coordinator, "Judô");
        activate(foreign, "Judô");
        String foreignId = body(send(put(judo, update(true, false, level("Dela"))), foreign)).get("levels").get(0).get("id").asText();
        String ok = level("Boa");
        String[][] cases = {
                {"levels[1].name", update(false, false, ok, level(""))},
                {"levels[1].name", update(false, false, ok, level("x".repeat(41)))},
                {"levels[1].name", update(false, false, ok, level("boa"))},
                {"levels[1].name", update(false, false, level("Faixa Preta"), level("faixa préta".replace("é", "e")))},
                {"levels[0].color", update(false, false, level("A").replace("#112233", "112233"))},
                {"levels[0].secondaryColor", update(false, false, level("A").replace("\"kids\"", "\"secondaryColor\":\"red\",\"kids\""))},
                {"levels[0].maxDegree", update(false, false, level("A").replace("\"maxDegree\":4", "\"maxDegree\":-1"))},
                {"levels[0].maxDegree", update(false, false, level("A").replace("\"maxDegree\":4", "\"maxDegree\":11"))},
                {"levels[0].minAge", update(false, false, level("A").replace("\"minAge\":16", "\"minAge\":-1"))},
                {"levels[0].maxAge", update(false, false, level("A").replace("\"minAge\":16", "\"maxAge\":121"))},
                {"levels[0].minAge", update(false, false, level("A").replace("\"minAge\":16", "\"minAge\":16,\"maxAge\":10"))},
                {"levels", update(false, false, java.util.stream.IntStream.range(0, 31).mapToObj(i -> level("N" + i)).toArray(String[]::new))},
                {"levels", update(true, false)},
                {"enabled", "{\"levels\":[]}"},
                {"levels[1].id", update(false, false, ok, level(foreignId, "Outra"))},
                {"levels[0].id", update(false, false, level(UUID.randomUUID().toString(), "Fantasma"))}};
        for (String[] c : cases) {
            var r = send(put(judo, c[1]), coordinator);
            assertThat(r.getStatus()).as(c[0]).isEqualTo(400);
            assertThat(r.getContentAsString()).as(c[0]).contains(c[0]);
        }
        assertThat(storedLevels(t, judo)).isZero();
        assertThat(audits(t)).isZero();
        assertThat(storedLevels(other, judo)).isEqualTo(1);
        // 31 níveis não passa, 30 sim
        assertThat(send(put(judo, update(false, false, java.util.stream.IntStream.range(0, 30).mapToObj(i -> level("N" + i)).toArray(String[]::new))), coordinator).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA4, CA6: perfis, anônimo, modalidade inexistente e não ativa")
    void permissions() throws Exception {
        var t = tenant();
        var coordinator = member(t, Role.COORDINATOR);
        UUID judo = id("Judô");
        UUID boxe = id("Boxe");
        activate(coordinator, "Judô");
        assertThat(send(get(judo), null).getStatus()).isEqualTo(401);
        assertThat(send(put(judo, update(false, false)), null).getStatus()).isEqualTo(401);
        var admin = body(send(get(judo), member(t, Role.ADMINISTRATOR)));
        assertThat(admin.get("canManage").asBoolean()).isFalse();
        assertThat(body(send(get(judo), coordinator)).get("canManage").asBoolean()).isTrue();
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = member(t, role);
            assertThat(send(put(judo, update(true, false, level("X"))), who).getStatus()).as(role.name()).isEqualTo(403);
            if (role != Role.ADMINISTRATOR) assertThat(send(get(judo), who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(storedLevels(t, judo)).isZero();
        assertThat(send(get(UUID.randomUUID()), coordinator).getStatus()).isEqualTo(404);
        assertThat(send(put(UUID.randomUUID(), update(false, false)), coordinator).getStatus()).isEqualTo(404);
        var inactiveGet = send(get(boxe), coordinator);
        assertThat(inactiveGet.getStatus()).isEqualTo(409);
        assertThat(inactiveGet.getContentAsString()).contains("MODALITY_NOT_ACTIVE");
        assertThat(send(put(boxe, update(false, false)), coordinator).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA9: duas organizações com a mesma modalidade não veem nem alteram os níveis uma da outra")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var ca = member(a, Role.COORDINATOR);
        var cb = member(b, Role.COORDINATOR);
        UUID judo = id("Judô");
        activate(ca, "Judô");
        activate(cb, "Judô");
        send(put(judo, update(true, false, level("Só da A"))), ca);
        send(put(judo, update(true, false, level("Só da B"), level("Outra da B"))), cb);
        assertThat(body(send(get(judo), ca)).findValuesAsText("name")).isEqualTo(List.of("Só da A"));
        assertThat(body(send(get(judo), cb)).findValuesAsText("name")).isEqualTo(List.of("Só da B", "Outra da B"));
        assertThat(storedLevels(a, judo)).isEqualTo(1);
        assertThat(storedLevels(b, judo)).isEqualTo(2);
    }
}
