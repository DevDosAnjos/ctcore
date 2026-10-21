package br.com.ctcore.benefit;

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

/** RF-BEN-01: lista, filtros, situação, "alto", perfis, alcance, isolamento (API e RLS) e regras do esquema, com banco real. */
class BenefitListIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    private static final String TODAY = "(now() at time zone 'America/Sao_Paulo')::date";

    @AfterEach
    void clean() {
        for (String table : new String[] {"benefit", "enrollment_modality", "enrollment", "student", "plan", "training_class", "teacher", "unit_modality",
                "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
    }

    private JsonNode list(String query, AccountPrincipal who) throws Exception {
        MockHttpServletResponse r = call(MockMvcRequestBuilders.get("/api/v1/benefits" + query), who).getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private int status(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/benefits" + query), who).getResponse().getStatus();
    }

    private List<String> names(JsonNode body) {
        List<String> out = new ArrayList<>();
        body.get("items").forEach(i -> out.add(i.get("student").get("name").asText()));
        return out;
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", id, "ben-" + id + "@example.com");
        return id;
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    /** Aluno matriculado numa modalidade (índice na ordem do nome) com plano de {@code planCents}; devolve a linha da modalidade da matrícula. */
    private UUID enrolled(Tenant t, UUID unit, String name, int kind, long planCents) {
        UUID modality = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(kind);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), modality);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        UUID teacher = UUID.randomUUID(), klass = UUID.randomUUID(), plan = UUID.randomUUID(), student = UUID.randomUUID(), enrollment = UUID.randomUUID(), row = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "T" + klass, "t" + klass, modality, teacher);
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                plan, t.orgId(), unit, modality, "P" + plan, "p" + plan, planCents);
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, ?, ?, date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", student, t.orgId(), unit, name, Modalities.normalize(name));
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at) values (?, ?, ?, ?, ?, ?, ?, ?, 5, 'ACTIVE', now())", row, t.orgId(), enrollment, student, unit, modality, klass, plan);
        return row;
    }

    /** Benefício direto no banco; {@code validUntil} é uma expressão SQL ou nulo. */
    private UUID benefit(Tenant t, UUID row, String type, long value, String validUntil, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, "
                + "valid_until, status, ended_at) select ?, em.organization_id, em.id, em.student_id, em.unit_id, ?, ?, 'Motivo', 'Mestre', ?, "
                + (validUntil == null ? "null" : validUntil) + ", ?, case when ? = 'ENDED' then now() end from enrollment_modality em where em.id = ?",
                id, type, value, account(), status, status, row);
        return id;
    }

    private UUID benefit(Tenant t, UUID row, String type, long value) {
        return benefit(t, row, type, value, null, "ACTIVE");
    }

    @Test
    @DisplayName("CA1, CA4, CA5: lista ordenada com todos os campos; high e mensalidade com benefício")
    void listAndHigh() throws Exception {
        var t = tenant();
        benefit(t, enrolled(t, t.unitId(), "Carlos", 0, 15000), "PERCENT", 50);
        benefit(t, enrolled(t, t.unitId(), "Bruno", 0, 15000), "PERCENT", 51);
        benefit(t, enrolled(t, t.unitId(), "Ana", 0, 15000), "AMOUNT", 7500);
        benefit(t, enrolled(t, t.unitId(), "Davi", 0, 15000), "AMOUNT", 7600);
        var coord = who(t, Role.COORDINATOR);
        var all = list("", coord);
        assertThat(names(all)).containsExactly("Ana", "Bruno", "Carlos", "Davi");
        assertThat(all.get("total").asInt()).isEqualTo(4);
        assertThat(all.get("page").asInt()).isEqualTo(1);
        assertThat(all.get("size").asInt()).isEqualTo(25);
        var first = all.get("items").get(0);
        assertThat(first.get("type").asText()).isEqualTo("AMOUNT");
        assertThat(first.get("value").asLong()).isEqualTo(7500);
        assertThat(first.get("reason").asText()).isEqualTo("Motivo");
        assertThat(first.get("decidedBy").asText()).isEqualTo("Mestre");
        assertThat(first.get("grantedBy").asText()).isEqualTo("Gestora Sintética");
        assertThat(first.get("validUntil").isNull()).isTrue();
        assertThat(first.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(first.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(first.get("modality").get("name").asText()).isNotBlank();
        assertThat(first.get("high").asBoolean()).isFalse();
        assertThat(first.get("monthlyValueCents").asLong()).isEqualTo(7500);
        assertThat(all.get("items").get(1).get("high").asBoolean()).isTrue();
        assertThat(all.get("items").get(2).get("high").asBoolean()).isFalse();
        assertThat(all.get("items").get(3).get("high").asBoolean()).isTrue();
        assertThat(names(list("?high=true", coord))).containsExactly("Bruno", "Davi");
        assertThat(status("?size=10", coord)).isEqualTo(400);
        assertThat(list("?size=25&page=2", coord).get("items")).isEmpty();
    }

    @Test
    @DisplayName("CA6: ontem = EXPIRED; hoje e sem prazo = ACTIVE; encerrado = ENDED")
    void statuses() throws Exception {
        var t = tenant();
        benefit(t, enrolled(t, t.unitId(), "Ana", 0, 10000), "PERCENT", 10, TODAY + " - 1", "ACTIVE");
        benefit(t, enrolled(t, t.unitId(), "Bia", 0, 10000), "PERCENT", 10, TODAY, "ACTIVE");
        benefit(t, enrolled(t, t.unitId(), "Cao", 0, 10000), "PERCENT", 10);
        benefit(t, enrolled(t, t.unitId(), "Dan", 0, 10000), "PERCENT", 10, TODAY + " - 1", "ENDED");
        var coord = who(t, Role.COORDINATOR);
        var items = list("", coord).get("items");
        assertThat(List.of(items.get(0).get("status").asText(), items.get(1).get("status").asText(), items.get(2).get("status").asText(),
                items.get(3).get("status").asText())).containsExactly("EXPIRED", "ACTIVE", "ACTIVE", "ENDED");
        assertThat(names(list("?status=EXPIRED", coord))).containsExactly("Ana");
        assertThat(names(list("?status=ENDED", coord))).containsExactly("Dan");
        assertThat(names(list("?status=ACTIVE", coord))).containsExactly("Bia", "Cao");
    }

    @Test
    @DisplayName("CA2: filtros type, modalityId, unitId, q (sem acento), combinados; valores inválidos dão 400")
    void filters() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID jiu = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(0);
        benefit(t, enrolled(t, t.unitId(), "João", 0, 10000), "PERCENT", 10);
        benefit(t, enrolled(t, t.unitId(), "Maria", 1, 10000), "AMOUNT", 1000);
        benefit(t, enrolled(t, second, "Pedro", 0, 10000), "AMOUNT", 2000);
        var coord = who(t, Role.COORDINATOR);
        assertThat(names(list("?type=AMOUNT", coord))).containsExactly("Maria", "Pedro");
        assertThat(names(list("?modalityId=" + jiu, coord))).containsExactly("João", "Pedro");
        assertThat(names(list("?unitId=" + second, coord))).containsExactly("Pedro");
        assertThat(names(list("?q=joao", coord))).containsExactly("João");
        assertThat(names(list("?type=AMOUNT&modalityId=" + jiu, coord))).containsExactly("Pedro");
        assertThat(names(list("?type=AMOUNT&modalityId=" + jiu + "&unitId=" + t.unitId(), coord))).isEmpty();
        assertThat(status("?type=X", coord)).isEqualTo(400);
        assertThat(status("?status=XYZ", coord)).isEqualTo(400);
    }

    @Test
    @DisplayName("CA7: Administrador só vê a unidade dele; unitId fora do alcance é vazio; Professor, Aluno e Responsável 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID other = unit(t, "Zona Sul");
        benefit(t, enrolled(t, t.unitId(), "Minha", 0, 10000), "PERCENT", 10);
        benefit(t, enrolled(t, other, "Alheia", 0, 10000), "PERCENT", 10);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(names(list("", admin))).containsExactly("Minha");
        assertThat(list("?unitId=" + other, admin).get("items")).isEmpty();
        assertThat(names(list("", who(t, Role.COORDINATOR)))).containsExactly("Alheia", "Minha");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(status("", who(t, role))).as(role.name()).isEqualTo(403);
        }
        assertThat(status("", null)).isEqualTo(401);
        assertThat(list("", new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of())).get("items")).isEmpty();
    }

    @Test
    @DisplayName("CA8: outra organização não aparece; RLS com ctcore_app; DELETE negado; um ativo por modalidade")
    void isolationAndSchema() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID rowA = enrolled(a, a.unitId(), "Ana", 0, 10000);
        benefit(a, rowA, "PERCENT", 10);
        benefit(b, enrolled(b, b.unitId(), "Beto", 0, 10000), "PERCENT", 10);
        assertThat(names(list("", who(a, Role.COORDINATOR)))).containsExactly("Ana");
        assertThat(list("?unitId=" + b.unitId(), who(a, Role.COORDINATOR)).get("total").asInt()).isZero();

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*), count(distinct organization_id) from benefit")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);
            }
            assertThatThrownBy(() -> sql.execute("delete from benefit")).hasMessageContaining("permission denied");
        }
        assertThatThrownBy(() -> benefit(a, rowA, "AMOUNT", 100)).hasMessageContaining("benefit_active_modality");
        benefit(a, rowA, "AMOUNT", 100, null, "ENDED");
    }

    @Test
    @DisplayName("CA3: o banco recusa 0%, 101%, desconto de 0 centavo e tipo desconhecido")
    void checks() {
        var t = tenant();
        UUID row = enrolled(t, t.unitId(), "Ana", 0, 10000);
        assertThatThrownBy(() -> benefit(t, row, "PERCENT", 0)).hasMessageContaining("benefit_check");
        assertThatThrownBy(() -> benefit(t, row, "PERCENT", 101)).hasMessageContaining("benefit_check");
        assertThatThrownBy(() -> benefit(t, row, "AMOUNT", 0)).hasMessageContaining("benefit_check");
        assertThatThrownBy(() -> benefit(t, row, "TIER", 10)).hasMessageContaining("type");
    }
}
