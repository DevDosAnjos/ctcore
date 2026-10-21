package br.com.ctcore.plan;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-PLA-01: resumo de planos e benefícios, regras do esquema, perfis, alcance e isolamento (API e RLS), com banco real. */
class PlanSummaryIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from plan");
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/plans/summary" + query), who).getResponse();
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

    private UUID plan(Tenant t, UUID unit, String name, long cents, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), unit, modality("Judô"), name, Modalities.normalize(name), cents, status);
        return id;
    }

    @Test
    @DisplayName("CA1, CA2: resumo com 3 ativos e 1 desativado; organização sem plano devolve zeros e nulos")
    void summary() throws Exception {
        var t = tenant();
        plan(t, t.unitId(), "Plano A", 20000, "ACTIVE");
        plan(t, t.unitId(), "Plano B", 15000, "ACTIVE");
        plan(t, t.unitId(), "Plano C", 30000, "ACTIVE");
        plan(t, t.unitId(), "Plano D", 100, "INACTIVE");
        var r = get("", who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).isEqualTo(200);
        var plans = body(r).get("plans");
        assertThat(plans.get("active").asInt()).isEqualTo(3);
        assertThat(plans.get("inactive").asInt()).isEqualTo(1);
        assertThat(plans.get("minValueCents").asLong()).isEqualTo(15000);
        assertThat(plans.get("maxValueCents").asLong()).isEqualTo(30000);
        assertThat(plans.get("studentsUsing").asInt()).isZero();
        assertThat(body(r).get("benefits").get("active").asInt()).isZero();
        assertThat(body(r).get("benefits").get("high").asInt()).isZero();

        var empty = body(get("", who(tenant(), Role.COORDINATOR))).get("plans");
        assertThat(empty.get("active").asInt()).isZero();
        assertThat(empty.get("inactive").asInt()).isZero();
        assertThat(empty.get("minValueCents").isNull()).isTrue();
        assertThat(empty.get("maxValueCents").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA3, CA4: alcance do Administrador por unidade; demais perfis 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        plan(t, t.unitId(), "Minha", 10000, "ACTIVE");
        plan(t, second, "Alheia", 20000, "ACTIVE");
        plan(t, second, "Alheia 2", 30000, "ACTIVE");
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(body(get("", admin)).get("plans").get("active").asInt()).isEqualTo(1);
        assertThat(body(get("?unitId=" + t.unitId(), admin)).get("plans").get("active").asInt()).isEqualTo(1);
        var other = body(get("?unitId=" + second, admin));
        assertThat(other.get("plans").get("active").asInt()).isZero();
        assertThat(other.get("plans").get("minValueCents").isNull()).isTrue();
        var coord = who(t, Role.COORDINATOR);
        assertThat(body(get("", coord)).get("plans").get("active").asInt()).isEqualTo(3);
        assertThat(body(get("?unitId=" + second, coord)).get("plans").get("active").asInt()).isEqualTo(2);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("", null).getStatus()).isEqualTo(401);
        var noUnits = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of());
        assertThat(body(get("", noUnits)).get("plans").get("active").asInt()).isZero();
    }

    @Test
    @DisplayName("CA9: benefícios do resumo contam só ativos não vencidos e, entre eles, os altos; Administrador conta só a unidade dele")
    void benefitCounts() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, "sum-" + account + "@example.com");
        UUID teacher = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        String[][] specs = {{"PERCENT", "30", "null", "ACTIVE", "0"}, {"PERCENT", "60", "null", "ACTIVE", "0"}, {"AMOUNT", "9000", "null", "ACTIVE", "0"},
                {"PERCENT", "80", "(current_date - 2)", "ACTIVE", "0"}, {"PERCENT", "80", "null", "ENDED", "0"}, {"PERCENT", "90", "null", "ACTIVE", "1"}};
        UUID modality = modality("Judô");
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), modality);
        for (String[] s : specs) {
            UUID unit = s[4].equals("1") ? second : t.unitId(), plan = plan(t, unit, "P" + UUID.randomUUID(), 15000, "ACTIVE");
            UUID klass = UUID.randomUUID(), student = UUID.randomUUID(), enrollment = UUID.randomUUID(), row = UUID.randomUUID();
            adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
            adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                    klass, t.orgId(), unit, "T" + klass, "t" + klass, modality, teacher);
            adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                    + "values (?, ?, ?, 'Aluno', 'aluno', date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", student, t.orgId(), unit);
            adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
            adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, status, started_at) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, 5, 'ACTIVE', now())", row, t.orgId(), enrollment, student, unit, modality, klass, plan);
            adminJdbc().update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, valid_until, status, ended_at) "
                    + "values (gen_random_uuid(), ?, ?, ?, ?, ?, ?, 'Motivo', 'Mestre', ?, " + s[2] + ", ?, case when ? = 'ENDED' then now() end)",
                    t.orgId(), row, student, unit, s[0], Long.parseLong(s[1]), account, s[3], s[3]);
        }
        var coord = body(get("", who(t, Role.COORDINATOR))).get("benefits");
        assertThat(coord.get("active").asInt()).isEqualTo(4);
        assertThat(coord.get("high").asInt()).isEqualTo(3);
        var admin = body(get("", who(t, Role.ADMINISTRATOR))).get("benefits");
        assertThat(admin.get("active").asInt()).isEqualTo(3);
        assertThat(admin.get("high").asInt()).isEqualTo(2);
        adminJdbc().update("delete from benefit");
        for (String table : new String[] {"enrollment_modality", "enrollment", "student", "training_class", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
    }

    @Test
    @DisplayName("CA5: plano de outra organização não entra na conta; RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        plan(a, a.unitId(), "Meu", 10000, "ACTIVE");
        plan(b, b.unitId(), "Dele", 99000, "ACTIVE");
        var plans = body(get("", who(a, Role.COORDINATOR))).get("plans");
        assertThat(plans.get("active").asInt()).isEqualTo(1);
        assertThat(plans.get("maxValueCents").asLong()).isEqualTo(10000);
        assertThat(body(get("?unitId=" + b.unitId(), who(a, Role.COORDINATOR))).get("plans").get("active").asInt()).isZero();

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select 1 from plan")) {
                assertThat(rows.next()).isFalse();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*), count(distinct organization_id) from plan")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);
            }
            String insert = "insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents) values (gen_random_uuid(), '"
                    + b.orgId() + "', '" + b.unitId() + "', '" + modality("Judô") + "', 'X1', 'x1', 100)";
            assertThatThrownBy(() -> sql.execute(insert)).hasMessageContaining("row-level security");
        }
    }

    @Test
    @DisplayName("CA6: banco recusa valor <= 0 e duplicado ativo; aceita desativado homônimo; sem coluna de presença")
    void schema() {
        var t = tenant();
        assertThatThrownBy(() -> plan(t, t.unitId(), "Zero", 0, "ACTIVE")).hasMessageContaining("value_cents");
        plan(t, t.unitId(), "Mensal", 10000, "ACTIVE");
        assertThatThrownBy(() -> plan(t, t.unitId(), "Mensal", 12000, "ACTIVE")).hasMessageContaining("plan_unit_modality_name");
        plan(t, t.unitId(), "Mensal", 9000, "INACTIVE");
        assertThat(adminJdbc().queryForObject("select count(*) from information_schema.columns where table_name = 'plan' "
                + "and (column_name ilike '%attend%' or column_name ilike '%presen%' or column_name ilike '%lesson%' or column_name ilike '%done%')",
                Integer.class)).isZero();
    }
}
