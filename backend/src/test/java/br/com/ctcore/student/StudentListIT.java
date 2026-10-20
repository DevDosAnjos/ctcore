package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-01 (lista): ordem, filtros, alcance, perfis, dado mínimo e isolamento (API e RLS), com banco real. */
class StudentListIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired FileStorage storage;
    private long cpfSeq;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students" + query), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID student(Tenant t, UUID unit, String name, LocalDate birth) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state, emergency_name, emergency_relationship, emergency_phone) values (?, ?, ?, ?, ?, ?, ?, 'secreto@ficticia.example', "
                + "'11987654321', '01001000', 'Rua Segredo', '1', 'Centro', 'São Paulo', 'SP', 'Contato Segredo', 'Mãe', '11911112222')",
                id, t.orgId(), unit, name, Modalities.normalize(name), birth, "%011d".formatted(++cpfSeq));
        return id;
    }

    private UUID range(Tenant t, String name) {
        return adminJdbc().queryForObject("select id from age_range where organization_id = ? and name = ?", UUID.class, t.orgId(), name);
    }

    private List<String> names(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.get("items").forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    @Test
    @DisplayName("CA1, CA2: ordem por nome sem acento nem caixa, campos, paginação, idade e maioridade no dia; size, page e sort inválidos dão 400")
    void lists() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        student(t, t.unitId(), "Zélia", today().minusYears(30));
        student(t, t.unitId(), "álvaro", today().minusYears(18));
        student(t, t.unitId(), "Bruno", today().minusYears(18).plusDays(1));
        student(t, t.unitId(), "Carla", today().minusYears(5));
        UUID photo = student(t, t.unitId(), "Alice", today().minusYears(12));
        try (var scope = Scopes.open(t.orgId(), t.unitId())) {
            storage.put("alice.png", new byte[] {1, 2, 3}, "image/png");
        }
        adminJdbc().update("update student set photo = 'alice.png' where id = ?", photo);

        JsonNode b = body(get("", c));
        assertThat(names(b)).containsExactly("Alice", "álvaro", "Bruno", "Carla", "Zélia");
        assertThat(b.get("total").asLong()).isEqualTo(5);
        assertThat(b.get("page").asInt()).isEqualTo(1);
        assertThat(b.get("size").asInt()).isEqualTo(25);
        JsonNode alice = b.get("items").get(0);
        assertThat(alice.get("photoUrl").asText()).startsWith("http");
        assertThat(alice.get("age").asInt()).isEqualTo(12);
        assertThat(alice.get("minor").asBoolean()).isTrue();
        assertThat(alice.get("ageRange").get("name").asText()).isEqualTo("Infantil");
        assertThat(alice.get("registeredUnit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(alice.get("birthDate").asText()).isEqualTo(today().minusYears(12).toString());
        assertThat(alice.get("createdAt").isNull()).isFalse();
        JsonNode alvaro = b.get("items").get(1);
        assertThat(alvaro.get("photoUrl").isNull()).isTrue();
        assertThat(alvaro.get("age").asInt()).isEqualTo(18);
        assertThat(alvaro.get("minor").asBoolean()).isFalse();
        JsonNode bruno = b.get("items").get(2);
        assertThat(bruno.get("age").asInt()).isEqualTo(17);
        assertThat(bruno.get("minor").asBoolean()).isTrue();
        assertThat(b.get("items").get(3).get("ageRange").get("name").asText()).isEqualTo("Infantil");
    }

    @Test
    @DisplayName("CA1: paginação e parâmetros inválidos")
    void paging() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        for (char ch = 'A'; ch <= 'E'; ch++) student(t, t.unitId(), "Aluno " + ch, today().minusYears(20));
        assertThat(names(body(get("?sort=name", c)))).hasSize(5);
        assertThat(names(body(get("?size=25&page=2", c)))).isEmpty();
        assertThat(body(get("?size=25&page=2", c)).get("total").asLong()).isEqualTo(5);
        for (String bad : new String[] {"?size=10", "?page=0", "?page=-1", "?sort=age", "?sort=name,up"}) {
            assertThat(get(bad, c).getStatus()).as(bad).isEqualTo(400);
        }
        // 26 alunos: a página 2 de 25 traz o último
        for (int i = 0; i < 21; i++) student(t, t.unitId(), "Outro %02d".formatted(i), today().minusYears(20));
        JsonNode p2 = body(get("?page=2", c));
        assertThat(p2.get("items")).hasSize(1);
        assertThat(p2.get("total").asLong()).isEqualTo(26);
        assertThat(body(get("?size=50", c)).get("items")).hasSize(26);
    }

    @Test
    @DisplayName("CA3: filtros por nome, unidade e faixa, sozinhos e combinados; faixa sem teto; faixa inexistente 404; não UUID 400")
    void filters() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID other = unit(t, "Zona Sul");
        student(t, t.unitId(), "João Silva", today().minusYears(30));
        student(t, other, "Joana Souza", today().minusYears(10));
        student(t, other, "Pedro 100%", today().minusYears(15));
        student(t, t.unitId(), "Maria", today().minusYears(10));

        assertThat(names(body(get("?q=joao", c)))).containsExactly("João Silva");
        assertThat(names(body(get("?q=JOAO SI", c)))).containsExactly("João Silva");
        assertThat(names(body(get("?q=%", c)))).containsExactly("Pedro 100%");
        assertThat(names(body(get("?q=100%", c)))).containsExactly("Pedro 100%");
        assertThat(names(body(get("?q=_", c)))).isEmpty();
        assertThat(names(body(get("?unitId=" + other, c)))).containsExactly("Joana Souza", "Pedro 100%");
        assertThat(names(body(get("?ageRangeId=" + range(t, "Infantil"), c)))).containsExactly("Joana Souza", "Maria");
        assertThat(names(body(get("?ageRangeId=" + range(t, "Juvenil"), c)))).containsExactly("Pedro 100%");
        assertThat(names(body(get("?ageRangeId=" + range(t, "Adulto"), c)))).containsExactly("João Silva");
        assertThat(names(body(get("?q=jo&unitId=" + other + "&ageRangeId=" + range(t, "Infantil"), c)))).containsExactly("Joana Souza");
        assertThat(names(body(get("?q=maria&unitId=" + other, c)))).isEmpty();
        assertThat(get("?ageRangeId=" + UUID.randomUUID(), c).getStatus()).isEqualTo(404);
        for (String bad : new String[] {"?unitId=x", "?ageRangeId=x"}) assertThat(get(bad, c).getStatus()).as(bad).isEqualTo(400);
    }

    @Test
    @DisplayName("D6: faixas sobrepostas valem pela de maior idade mínima; sem faixa que cubra, ageRange nulo")
    void overlappingRanges() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        adminJdbc().update("insert into age_range (id, organization_id, name, name_key, min_age, max_age) values (?, ?, 'Pré', 'pre', 3, 5)", UUID.randomUUID(), t.orgId());
        adminJdbc().update("insert into age_range (id, organization_id, name, name_key, min_age, max_age) values (?, ?, 'Mirim', 'mirim', 5, 8)", UUID.randomUUID(), t.orgId());
        student(t, t.unitId(), "Cinco", today().minusYears(5));
        student(t, t.unitId(), "Dois", today().minusYears(2));
        JsonNode b = body(get("", c));
        assertThat(b.get("items").get(0).get("name").asText()).isEqualTo("Cinco");
        assertThat(b.get("items").get(0).get("ageRange").get("name").asText()).isEqualTo("Mirim");
        assertThat(b.get("items").get(1).get("ageRange").isNull()).isTrue();
        assertThat(names(body(get("?ageRangeId=" + adminJdbc().queryForObject("select id from age_range where name = 'Pré' and organization_id = ?", UUID.class, t.orgId()), c))))
                .containsExactly("Cinco");
    }

    @Test
    @DisplayName("RN-ALU-07: CA4, CA5: Administrador só vê as unidades dele; unitId fora vazio; sem unidade vazio; demais perfis 403; anônimo 401")
    void permissions() throws Exception {
        Tenant t = tenant();
        UUID second = unit(t, "Zona Sul");
        student(t, t.unitId(), "Minha", today().minusYears(20));
        student(t, second, "Alheia", today().minusYears(20));
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(names(body(get("", admin)))).containsExactly("Minha");
        assertThat(names(body(get("?unitId=" + second, admin)))).isEmpty();
        assertThat(body(get("?unitId=" + second, admin)).get("total").asLong()).isZero();
        var noUnit = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(names(body(get("", noUnit)))).isEmpty();
        var coordinator = who(t, Role.COORDINATOR);
        assertThat(names(body(get("", coordinator)))).containsExactly("Alheia", "Minha");
        assertThat(names(body(get("?unitId=" + second, coordinator)))).containsExactly("Alheia");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("", null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA6: a resposta não traz CPF, e-mail, telefone, endereço nem emergência")
    void minimal() throws Exception {
        Tenant t = tenant();
        UUID id = student(t, t.unitId(), "Com Dados", today().minusYears(20));
        adminJdbc().update("update student set cpf = '52998224725' where id = ?", id);
        String raw = get("", who(t, Role.COORDINATOR)).getContentAsString();
        for (String secret : new String[] {"52998224725", "secreto@ficticia.example", "11987654321", "Rua Segredo", "Contato Segredo", "11911112222", "01001000"}) {
            assertThat(raw).doesNotContain(secret);
        }
        for (String key : new String[] {"cpf", "email", "phone", "street", "emergencyName"}) assertThat(raw).doesNotContain("\"" + key + "\"");
    }

    @Test
    @DisplayName("CA7: aluno de outra organização não aparece; unidade ou faixa de outra organização não revela nada; RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        student(a, a.unitId(), "Mesmo Nome", today().minusYears(20));
        student(b, b.unitId(), "Mesmo Nome", today().minusYears(20));
        student(b, b.unitId(), "Só Dele", today().minusYears(20));
        var ca = who(a, Role.COORDINATOR);
        JsonNode list = body(get("", ca));
        assertThat(names(list)).containsExactly("Mesmo Nome");
        assertThat(list.get("items").get(0).get("registeredUnit").get("id").asText()).isEqualTo(a.unitId().toString());
        assertThat(names(body(get("?unitId=" + b.unitId(), ca)))).isEmpty();
        assertThat(get("?ageRangeId=" + range(b, "Adulto"), ca).getStatus()).isEqualTo(404);

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            try (var rows = sql.executeQuery("select count(*) from student")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from student where organization_id = '" + b.orgId() + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }
}
