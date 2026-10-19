package br.com.ctcore.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-PRO-01: lista, filtros, graduação por modalidade, sem contato, conta única, perfis, alcance e isolamento (API e RLS), com banco real. */
class TeacherIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    private final java.util.List<UUID> accounts = new java.util.ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void clean() {
        adminJdbc().update("delete from teacher_modality");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
        for (UUID id : accounts) {
            adminJdbc().update("delete from account_link where account_id = ?", id);
            adminJdbc().update("delete from account where id = ?", id);
        }
        accounts.clear();
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/teachers" + query), who).getResponse();
    }

    private static java.util.List<String> names(JsonNode items) {
        return java.util.stream.StreamSupport.stream(items.spliterator(), false).map(i -> i.get("name").asText()).toList();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID modality(String name) {
        return adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
    }

    private UUID level(Tenant t, UUID modality, String name, int maxDegree) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, max_degree, kids) "
                + "values (?, ?, ?, (select count(*) from graduation_level where organization_id = ? and modality_id = ?), ?, '#112233', ?, false)",
                id, t.orgId(), modality, t.orgId(), modality, name, maxDegree);
        return id;
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID teacher(Tenant t, String name, String status, UUID account, UUID... units) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, email, phone, directory_consent, status, account_id) "
                + "values (?, ?, ?, ?, 'prof.secreto@synthetic.example', '+55 11 98888-7777', true, ?, ?)",
                id, t.orgId(), name, Modalities.normalize(name), status, account);
        for (UUID unit : units) {
            adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        }
        return id;
    }

    private void teaches(Tenant t, UUID teacher, UUID modality, UUID level, int degree) {
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id, level_id, degree) values (?, ?, ?, ?, ?)",
                t.orgId(), teacher, modality, level, degree);
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Synthetic', 'ACTIVE')", id, id + "@synthetic.example");
        accounts.add(id);
        return id;
    }

    private void link(Tenant t, UUID account, String role, String status) {
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, ?, ?)", account, t.orgId(), role, status);
    }

    @Test
    @DisplayName("CA1, CA3: Coordenador lista ordenado por nome, com unidades, modalidades com graduação, classCount 0 e envelope")
    void list() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID jj = modality("Jiu-Jitsu");
        UUID judo = modality("Judô");
        UUID blue = level(t, jj, "Azul", 4);
        UUID zeca = teacher(t, "Zeca", "ACTIVE", null, t.unitId());
        UUID ana = teacher(t, "Ana", "ACTIVE", null, t.unitId(), second);
        teaches(t, ana, jj, blue, 2);
        teaches(t, ana, judo, null, 0);
        var c = who(t, Role.COORDINATOR);
        var r = get("", c);
        assertThat(r.getStatus()).isEqualTo(200);
        var data = body(r);
        assertThat(data.get("page").asInt()).isEqualTo(1);
        assertThat(data.get("size").asInt()).isEqualTo(25);
        assertThat(data.get("total").asInt()).isEqualTo(2);
        assertThat(names(data.get("items"))).containsExactly("Ana", "Zeca");
        var first = data.get("items").get(0);
        assertThat(first.get("id").asText()).isEqualTo(ana.toString());
        assertThat(first.get("classCount").asInt()).isZero();
        assertThat(first.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(first.get("photoUrl").isNull()).isTrue();
        assertThat(first.get("units").findValuesAsText("name")).containsExactly("Unit", "Zona Sul");
        var mods = first.get("modalities");
        assertThat(mods.findValuesAsText("name")).containsExactly("Jiu-Jitsu", "Judô");
        assertThat(mods.get(0).get("level").asText()).isEqualTo("Azul");
        assertThat(mods.get(0).get("degree").asInt()).isEqualTo(2);
        assertThat(mods.get(1).get("level").isNull()).isTrue();
        assertThat(mods.get(1).get("degree").isNull()).isTrue();
        assertThat(body(get("?sort=name", c)).get("items").get(0).get("name").asText()).isEqualTo("Ana");
        assertThat(body(get("?size=25&page=2", c)).get("items")).isEmpty();
        for (String bad : new String[] {"?size=10", "?page=0", "?sort=email"}) {
            assertThat(get(bad, c).getStatus()).as(bad).isEqualTo(400);
        }
        assertThat(zeca).isNotNull();
    }

    @Test
    @DisplayName("CA2: filtros q (sem acento e caixa), modalidade, unidade e situação, sozinhos e combinados; status inválido 400")
    void filters() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID jj = modality("Jiu-Jitsu");
        UUID joao = teacher(t, "João Silva", "ACTIVE", null, t.unitId());
        UUID maria = teacher(t, "Maria 100%", "INACTIVE", null, second);
        teaches(t, joao, jj, null, 0);
        teaches(t, maria, jj, null, 0);
        var c = who(t, Role.COORDINATOR);
        assertThat(names(body(get("?q=joao", c)).get("items"))).containsExactly("João Silva");
        assertThat(body(get("?q=JOÃO", c)).get("total").asInt()).isEqualTo(1);
        assertThat(names(body(get("?q=%", c)).get("items"))).containsExactly("Maria 100%");
        assertThat(body(get("?q=_", c)).get("total").asInt()).isZero();
        assertThat(body(get("?modalityId=" + jj, c)).get("total").asInt()).isEqualTo(2);
        assertThat(body(get("?modalityId=" + modality("Judô"), c)).get("total").asInt()).isZero();
        assertThat(names(body(get("?unitId=" + second, c)).get("items"))).containsExactly("Maria 100%");
        assertThat(names(body(get("?status=INACTIVE", c)).get("items"))).containsExactly("Maria 100%");
        assertThat(names(body(get("?status=active", c)).get("items"))).containsExactly("João Silva");
        assertThat(body(get("?q=maria&status=INACTIVE&unitId=" + second + "&modalityId=" + jj, c)).get("total").asInt()).isEqualTo(1);
        assertThat(body(get("?q=joao&unitId=" + second, c)).get("total").asInt()).isZero();
        assertThat(get("?status=XYZ", c).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA3: tabela recusa duas graduações da mesma modalidade, grau acima do máximo e nível de outra modalidade")
    void graduationRules() {
        var t = tenant();
        UUID jj = modality("Jiu-Jitsu");
        UUID blue = level(t, jj, "Azul", 4);
        UUID judoLevel = level(t, modality("Judô"), "Faixa", 4);
        UUID id = teacher(t, "Ana", "ACTIVE", null, t.unitId());
        teaches(t, id, jj, blue, 4);
        assertThatThrownBy(() -> teaches(t, id, jj, blue, 1)).hasMessageContaining("teacher_modality_pkey");
        UUID other = teacher(t, "Beto", "ACTIVE", null, t.unitId());
        assertThatThrownBy(() -> teaches(t, other, jj, blue, 5)).hasMessageContaining("degree above");
        assertThatThrownBy(() -> teaches(t, other, jj, judoLevel, 1)).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> teaches(t, other, modality("Judô"), null, 11)).hasMessageContaining("check");
    }

    @Test
    @DisplayName("CA4: resposta bruta não traz telefone nem e-mail; directoryConsent reflete o gravado")
    void noContact() throws Exception {
        var t = tenant();
        teacher(t, "Ana", "ACTIVE", null, t.unitId());
        var r = get("", who(t, Role.COORDINATOR));
        String raw = r.getContentAsString();
        assertThat(raw).doesNotContain("prof.secreto", "98888", "\"email\"", "\"phone\"", "synthetic.example");
        assertThat(body(r).get("items").get(0).get("directoryConsent").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("CA5: conta com vínculos TEACHER e STUDENT aparece uma vez, com access do vínculo TEACHER; sem vínculo, NONE; conta repetida recusada")
    void access() throws Exception {
        var t = tenant();
        UUID both = account();
        link(t, both, "STUDENT", "ACTIVE");
        link(t, both, "TEACHER", "PENDING");
        UUID onlyStudent = account();
        link(t, onlyStudent, "STUDENT", "ACTIVE");
        teacher(t, "Ana", "ACTIVE", both, t.unitId());
        teacher(t, "Beto", "ACTIVE", onlyStudent, t.unitId());
        teacher(t, "Caio", "ACTIVE", null, t.unitId());
        var items = body(get("", who(t, Role.COORDINATOR))).get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("access").asText()).isEqualTo("PENDING");
        assertThat(items.get(1).get("access").asText()).isEqualTo("NONE");
        assertThat(items.get(2).get("access").asText()).isEqualTo("NONE");
        assertThatThrownBy(() -> teacher(t, "Dora", "ACTIVE", both, t.unitId())).hasMessageContaining("teacher_org_account");
    }

    @Test
    @DisplayName("CA6: Administrador só vê professores com unidade dele (e todas as unidades deles); unitId alheio vazio; demais perfis 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        teacher(t, "Ana", "ACTIVE", null, t.unitId(), second);
        teacher(t, "Beto", "ACTIVE", null, second);
        var admin = who(t, Role.ADMINISTRATOR);
        var data = body(get("", admin));
        assertThat(names(data.get("items"))).containsExactly("Ana");
        assertThat(data.get("total").asInt()).isEqualTo(1);
        assertThat(data.get("items").get(0).get("units")).hasSize(2);
        assertThat(body(get("?unitId=" + second, admin)).get("items")).isEmpty();
        assertThat(body(get("?unitId=" + t.unitId(), admin)).get("total").asInt()).isEqualTo(1);
        assertThat(body(get("", who(t, Role.COORDINATOR))).get("total").asInt()).isEqualTo(2);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("", null).getStatus()).isEqualTo(401);
        var noUnits = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(body(get("", noUnits)).get("total").asInt()).isZero();
    }

    @Test
    @DisplayName("RF-PRO-02 CA1, CA3: painel traz todos os campos com contato; a lista continua sem e-mail e telefone")
    void detail() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID jj = modality("Jiu-Jitsu");
        UUID blue = level(t, jj, "Azul", 4);
        UUID ana = teacher(t, "Ana", "ACTIVE", null, t.unitId(), second);
        adminJdbc().update("update teacher set bio = 'Faixa azul' where id = ?", ana);
        teaches(t, ana, jj, blue, 2);
        teaches(t, ana, modality("Judô"), null, 0);
        var c = who(t, Role.COORDINATOR);
        var r = call(MockMvcRequestBuilders.get("/api/v1/teachers/" + ana), c).getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        var d = body(r);
        assertThat(d.get("id").asText()).isEqualTo(ana.toString());
        assertThat(d.get("name").asText()).isEqualTo("Ana");
        assertThat(d.get("bio").asText()).isEqualTo("Faixa azul");
        assertThat(d.get("email").asText()).isEqualTo("prof.secreto@synthetic.example");
        assertThat(d.get("phone").asText()).isEqualTo("+55 11 98888-7777");
        assertThat(d.get("directoryConsent").asBoolean()).isTrue();
        assertThat(d.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.get("access").asText()).isEqualTo("NONE");
        assertThat(d.get("photoUrl").isNull()).isTrue();
        assertThat(d.get("units").findValuesAsText("name")).containsExactly("Unit", "Zona Sul");
        assertThat(d.get("modalities").findValuesAsText("name")).containsExactly("Jiu-Jitsu", "Judô");
        assertThat(d.get("modalities").get(0).get("level").asText()).isEqualTo("Azul");
        assertThat(d.get("modalities").get(0).get("degree").asInt()).isEqualTo(2);
        assertThat(d.get("classes")).isEmpty();
        assertThat(d.get("upcomingClasses")).isEmpty();
        assertThat(get("", c).getContentAsString()).doesNotContain("prof.secreto", "98888", "\"email\"", "\"phone\"");
    }

    @Test
    @DisplayName("RF-PRO-02 CA2: Administrador só abre professor com unidade dele (e vê todas); demais perfis 403; anônimo 401; inexistente 404")
    void detailPermissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID shared = teacher(t, "Ana", "ACTIVE", null, t.unitId(), second);
        UUID foreign = teacher(t, "Beto", "ACTIVE", null, second);
        var admin = who(t, Role.ADMINISTRATOR);
        var open = call(MockMvcRequestBuilders.get("/api/v1/teachers/" + shared), admin).getResponse();
        assertThat(open.getStatus()).isEqualTo(200);
        assertThat(body(open).get("units")).hasSize(2);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + foreign), admin).getResponse().getStatus()).isEqualTo(404);
        var noUnits = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + shared), noUnits).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + UUID.randomUUID()), who(t, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(404);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + shared), who(t, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + shared), null).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RF-PRO-02 CA2: id de outra organização é 404 e, com ctcore_app, a consulta direta não enxerga a linha")
    void detailIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID tb = teacher(b, "Ana", "ACTIVE", null, b.unitId());
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/" + tb), who(a, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(404);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select 1 from teacher where id = '" + tb + "'")) {
                assertThat(rows.next()).isFalse();
            }
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA7: cada organização só vê os seus professores de mesmo nome; RLS com ctcore_app nas três tabelas")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID jj = modality("Jiu-Jitsu");
        UUID ta = teacher(a, "Ana", "ACTIVE", null, a.unitId());
        UUID tb = teacher(b, "Ana", "ACTIVE", null, b.unitId());
        teaches(a, ta, jj, null, 0);
        teaches(b, tb, jj, null, 0);
        var items = body(get("", who(a, Role.COORDINATOR))).get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).get("id").asText()).isEqualTo(ta.toString());
        assertThat(body(get("?unitId=" + b.unitId(), who(a, Role.COORDINATOR))).get("items")).isEmpty();

        for (String table : new String[] {"teacher", "teacher_unit", "teacher_modality"}) {
            try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
                app.setAutoCommit(false);
                sql.execute("select set_config('app.organization_id', '', true)");
                try (var rows = sql.executeQuery("select 1 from " + table)) {
                    assertThat(rows.next()).as(table).isFalse();
                }
                sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
                try (var rows = sql.executeQuery("select count(distinct organization_id), min(organization_id::text) from " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).as(table).isEqualTo(1);
                    assertThat(rows.getString(2)).isEqualTo(a.orgId().toString());
                }
                String insert = switch (table) {
                    case "teacher" -> "insert into teacher (id, organization_id, name, name_key) values (gen_random_uuid(), '" + b.orgId() + "', 'X1', 'x1')";
                    case "teacher_unit" -> "insert into teacher_unit (organization_id, teacher_id, unit_id) values ('" + b.orgId() + "', '" + tb + "', '" + b.unitId() + "')";
                    default -> "insert into teacher_modality (organization_id, teacher_id, modality_id) values ('" + b.orgId() + "', '" + tb + "', '" + modality("Judô") + "')";
                };
                assertThatThrownBy(() -> sql.executeUpdate(insert)).as(table).hasMessageContaining("row-level security");
                app.rollback();
            }
        }
    }
}
