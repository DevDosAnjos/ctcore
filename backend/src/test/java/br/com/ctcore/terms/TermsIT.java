package br.com.ctcore.terms;

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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MAT-05 (RN-MAT-14): modelo, publicação, numeração por tipo, validação, perfis, isolamento com RLS, imutabilidade, auditoria e corrida. */
class TermsIT extends OrganizationBase {
    private static final String TEXT_A = "Texto do termo, versão A, com mais de vinte caracteres.";
    private static final String TEXT_B = "Texto do termo, versão B, com mais de vinte caracteres.";

    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from terms_version");
        adminJdbc().update("delete from account where email like 'terms-%@example.com'");
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "terms-" + p.accountId() + "@example.com");
        return p;
    }

    private static MockHttpServletRequestBuilder read(String kind) {
        return MockMvcRequestBuilders.get("/api/v1/organization/terms/" + kind);
    }

    private static MockHttpServletRequestBuilder publishRequest(String kind, String body) {
        return MockMvcRequestBuilders.post("/api/v1/organization/terms/" + kind + "/versions").contentType("application/json").content(body);
    }

    private static String payload(String text, Boolean reaccept) {
        return "{" + (text == null ? "" : "\"body\":\"" + text + "\"") + (text != null && reaccept != null ? "," : "")
                + (reaccept == null ? "" : "\"reaccept\":" + reaccept) + "}";
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        return call(request, who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private int count(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from terms_version where organization_id = ?", Integer.class, t.orgId());
    }

    private int audits(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'terms.published'", Integer.class, t.orgId());
    }

    @Test
    @DisplayName("CA1: sem publicação, a leitura devolve o modelo como versão 0, sem histórico, e o Coordenador gerencia")
    void model() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        var enrollment = body(send(read("ENROLLMENT"), c));
        assertThat(enrollment.get("canManage").asBoolean()).isTrue();
        assertThat(enrollment.get("current").get("version").asInt()).isZero();
        assertThat(enrollment.get("current").get("body").asText()).isEqualTo(Terms.ENROLLMENT_TEMPLATE);
        assertThat(enrollment.get("current").get("reaccept").asBoolean()).isFalse();
        assertThat(enrollment.get("current").get("publishedAt").isNull()).isTrue();
        assertThat(enrollment.get("versions")).isEmpty();
        assertThat(body(send(read("IMAGE_USE"), c)).get("current").get("body").asText()).isEqualTo(Terms.IMAGE_USE_TEMPLATE);
        assertThat(count(t)).isZero();
    }

    @Test
    @DisplayName("CA2: publica versões 1 e 2, a atual é a última, o histórico vem da mais nova e os tipos numeram à parte")
    void publish() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        var first = send(publishRequest("ENROLLMENT", payload(" " + TEXT_A + " ", false)), c);
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(body(first).get("version").asInt()).isEqualTo(1);
        assertThat(body(first).get("body").asText()).isEqualTo(TEXT_A);
        assertThat(body(first).get("publishedBy").asText()).isEqualTo("Gestora Sintética");
        var view = body(send(read("ENROLLMENT"), c));
        assertThat(view.get("current")).isEqualTo(body(first));
        var second = send(publishRequest("ENROLLMENT", payload(TEXT_B, true)), c);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(body(second).get("version").asInt()).isEqualTo(2);
        assertThat(body(second).get("reaccept").asBoolean()).isTrue();
        view = body(send(read("ENROLLMENT"), c));
        assertThat(view.get("current").get("version").asInt()).isEqualTo(2);
        assertThat(view.get("versions").findValuesAsText("version")).containsExactly("2", "1");
        assertThat(view.get("versions").get(1).get("reaccept").asBoolean()).isFalse();
        assertThat(body(send(publishRequest("IMAGE_USE", payload(TEXT_A, false)), c)).get("version").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA3: entradas inválidas dão 400 sem gravar; 20 e 20000 caracteres passam; texto igual ao atual e kind desconhecido")
    void validate() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        for (String bad : new String[] {payload(null, true), payload("x".repeat(19), true), payload("x".repeat(20001), true), payload(" ".repeat(30), true),
                payload(" " + "x".repeat(19) + " ", true), payload(TEXT_A, null), "{}"}) {
            assertThat(send(publishRequest("ENROLLMENT", bad), c).getStatus()).as(bad.length() > 80 ? "grande" : bad).isEqualTo(400);
        }
        assertThat(count(t)).isZero();
        assertThat(audits(t)).isZero();
        assertThat(send(publishRequest("ENROLLMENT", payload("x".repeat(20), false)), c).getStatus()).isEqualTo(201);
        assertThat(send(publishRequest("ENROLLMENT", payload("y".repeat(20000), false)), c).getStatus()).isEqualTo(201);
        var same = send(publishRequest("ENROLLMENT", payload("y".repeat(20000), true)), c);
        assertThat(same.getStatus()).isEqualTo(400);
        assertThat(same.getContentAsString()).contains("body");
        // sem publicação, o texto do modelo também é recusado
        var template = send(publishRequest("IMAGE_USE", json.writeValueAsString(java.util.Map.of("body", Terms.IMAGE_USE_TEMPLATE, "reaccept", false))), c);
        assertThat(template.getStatus()).isEqualTo(400);
        assertThat(count(t)).isEqualTo(2);
        assertThat(send(read("OUTRO"), c).getStatus()).isEqualTo(404);
        assertThat(send(publishRequest("OUTRO", payload(TEXT_A, false)), c).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA5: Administrador lê sem gerenciar e não publica; Professor, Aluno e Responsável 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        assertThat(send(read("ENROLLMENT"), null).getStatus()).isEqualTo(401);
        assertThat(send(publishRequest("ENROLLMENT", payload(TEXT_A, false)), null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = account(t, role);
            assertThat(send(publishRequest("ENROLLMENT", payload(TEXT_A, false)), who).getStatus()).as(role.name()).isEqualTo(403);
            var r = send(read("ENROLLMENT"), who);
            assertThat(r.getStatus()).as(role.name()).isEqualTo(role == Role.ADMINISTRATOR ? 200 : 403);
            if (role == Role.ADMINISTRATOR) assertThat(body(r).get("canManage").asBoolean()).isFalse();
        }
        assertThat(count(t)).isZero();
        assertThat(audits(t)).isZero();
        assertThat(send(MockMvcRequestBuilders.put("/api/v1/organization/terms/ENROLLMENT/versions/1"), account(t, Role.COORDINATOR)).getStatus()).isIn(404, 405);
        assertThat(send(MockMvcRequestBuilders.delete("/api/v1/organization/terms/ENROLLMENT/versions/1"), account(t, Role.COORDINATOR)).getStatus()).isIn(404, 405);
    }

    @Test
    @DisplayName("CA6, CA4: organizações separadas e RLS com ctcore_app; o papel de execução não faz update nem delete")
    void isolationAndImmutability() throws Exception {
        var a = tenant();
        var b = tenant();
        var ca = account(a, Role.COORDINATOR);
        var cb = account(b, Role.COORDINATOR);
        assertThat(send(publishRequest("ENROLLMENT", payload(TEXT_A, false)), cb).getStatus()).isEqualTo(201);
        assertThat(body(send(read("ENROLLMENT"), ca)).get("versions")).isEmpty();
        assertThat(body(send(publishRequest("ENROLLMENT", payload(TEXT_B, true)), ca)).get("version").asInt()).isEqualTo(1);
        assertThat(count(b)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select body from terms_version where organization_id = ?", String.class, b.orgId())).isEqualTo(TEXT_A);

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select 1 from terms_version")) {
                assertThat(rows.next()).isFalse();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*), count(distinct organization_id) from terms_version")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);
            }
            assertThatThrownBy(() -> sql.executeUpdate("insert into terms_version (id, organization_id, kind, version, body, reaccept, published_by_account_id) values (gen_random_uuid(), '"
                    + b.orgId() + "', 'ENROLLMENT', 9, '" + TEXT_A + "', false, '" + ca.accountId() + "')")).hasMessageContaining("row-level security");
            app.rollback();
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            assertThatThrownBy(() -> sql.executeUpdate("update terms_version set body = 'alterado alterado alterado'")).hasMessageContaining("permission denied");
            app.rollback();
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            assertThatThrownBy(() -> sql.executeUpdate("delete from terms_version")).hasMessageContaining("permission denied");
            app.rollback();
        }
        assertThat(count(a)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA7: cada publicação audita ator, perfil, kind, versão e reaccept, sem o texto; recusas não auditam")
    void audit() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        send(publishRequest("IMAGE_USE", payload(TEXT_A, true)), c);
        send(publishRequest("IMAGE_USE", payload(TEXT_A, true)), c);
        send(publishRequest("IMAGE_USE", payload("curto", true)), c);
        send(publishRequest("OUTRO", payload(TEXT_B, true)), c);
        send(publishRequest("IMAGE_USE", payload(TEXT_B, true)), account(t, Role.ADMINISTRATOR));
        assertThat(audits(t)).isEqualTo(1);
        var row = adminJdbc().queryForObject("select actor_id::text || '|' || actor_profile || '|' || coalesce(before_data::text, '') || '|' || coalesce(after_data::text, '') "
                + "from audit_log where organization_id = ? and action = 'terms.published'", String.class, t.orgId());
        assertThat(row).contains(c.accountId().toString(), "COORDINATOR", "IMAGE_USE", "\"version\"", "\"reaccept\"").doesNotContain("Texto do termo");
    }

    @Test
    @DisplayName("CA8: publicação que perde a corrida pelo mesmo número dá 409, sem segunda versão e sem auditoria")
    void concurrent() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        var other = account(t, Role.COORDINATOR);
        var pool = Executors.newSingleThreadExecutor();
        try (Connection rival = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            rival.setAutoCommit(false);
            // O rival tem a versão 1 gravada e ainda não confirmada: a publicação não a vê, calcula 1 e espera o índice único.
            try (Statement sql = rival.createStatement()) {
                sql.execute("insert into terms_version (id, organization_id, kind, version, body, reaccept, published_by_account_id) values (gen_random_uuid(), '"
                        + t.orgId() + "', 'ENROLLMENT', 1, '" + TEXT_B + "', false, '" + other.accountId() + "')");
            }
            Future<MockHttpServletResponse> loser = pool.submit(() -> send(publishRequest("ENROLLMENT", payload(TEXT_A, false)), c));
            Thread.sleep(1500);
            assertThat(loser.isDone()).isFalse();
            rival.commit();
            var r = loser.get();
            assertThat(r.getStatus()).isEqualTo(409);
            assertThat(r.getContentAsString()).contains("TERMS_VERSION_CONFLICT");
        } finally {
            pool.shutdown();
        }
        assertThat(count(t)).isEqualTo(1);
        assertThat(audits(t)).isZero();
        assertThat(send(publishRequest("ENROLLMENT", payload(TEXT_A, false)), c).getStatus()).isEqualTo(201);
        assertThat(count(t)).isEqualTo(2);
        assertThat(audits(t)).isEqualTo(1);
    }
}
