package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** CA3, CA18, CA21 (esquema e RLS): sem coluna de saúde, sem DELETE para o papel operacional, RLS e regras de integridade. */
class StudentSchemaIT extends PostgresIT {
    JdbcTemplate admin = adminJdbc();

    private UUID org() {
        UUID id = UUID.randomUUID();
        admin.update("insert into organization (id, name) values (?, 'Org')", id);
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", id, id, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID student(UUID org, String cpf) {
        UUID id = UUID.randomUUID();
        admin.update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, 'Aluno', 'aluno', date '2000-01-01', ?, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP')", id, org, org, cpf);
        return id;
    }

    private UUID guardian(UUID org, String cpf) {
        UUID account = UUID.randomUUID(), id = UUID.randomUUID();
        admin.update("insert into account (id, email, status) values (?, ?, 'INVITED')", account, account + "@x.example");
        admin.update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Resp', ?, '11900000000')", id, org, account, cpf);
        return id;
    }

    private void link(UUID org, UUID student, UUID guardian, boolean financial) {
        admin.update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", org, student, guardian, financial);
    }

    @Test
    @DisplayName("CA3 / RN-LGP-02: a tabela student não tem coluna de saúde nem de documento médico")
    void noHealthColumns() {
        var columns = admin.queryForList("select column_name from information_schema.columns where table_name in ('student', 'guardian', 'student_guardian')", String.class);
        assertThat(columns).isNotEmpty().noneMatch(c -> c.matches(".*(blood|medical|health|allerg|disease|certificate).*"));
    }

    @Test
    @DisplayName("CA18 / RN-ALU-08: o papel operacional não apaga student nem student_guardian")
    void runtimeRoleCannotDelete() throws Exception {
        UUID org = org();
        UUID s = student(org, "52998224725"), g = guardian(org, "11144477735");
        link(org, s, g, true);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + org + "', true)");
            assertThatThrownBy(() -> sql.executeUpdate("delete from student_guardian")).hasMessageContaining("permission denied");
            app.rollback();
            sql.execute("select set_config('app.organization_id', '" + org + "', true)");
            assertThatThrownBy(() -> sql.executeUpdate("delete from student")).hasMessageContaining("permission denied");
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA21: RLS das três tabelas: sem contexto e com outra organização, zero linhas; o contexto certo lê")
    void rowLevelSecurity() throws Exception {
        UUID a = org(), b = org();
        UUID s = student(a, "52998224725"), g = guardian(a, "11144477735");
        link(a, s, g, true);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            for (String table : new String[]{"student", "guardian", "student_guardian"}) {
                for (String scope : new String[]{"", b.toString(), a.toString()}) {
                    sql.execute("select set_config('app.organization_id', '" + scope + "', true)");
                    try (var rows = sql.executeQuery("select count(*) from " + table)) {
                        rows.next();
                        assertThat(rows.getInt(1)).as(table + " com contexto '" + scope + "'").isEqualTo(scope.equals(a.toString()) ? 1 : 0);
                    }
                }
            }
            sql.execute("select set_config('app.organization_id', '" + b + "', true)");
            assertThat(sql.executeUpdate("update student set name = 'Outro'")).isZero();
            app.rollback();
        }
    }

    @Test
    @DisplayName("RN-RSP-02: o banco recusa o terceiro responsável e o segundo financeiro")
    void guardianLimits() {
        UUID org = org();
        UUID s = student(org, "52998224725");
        UUID g1 = guardian(org, "11144477735"), g2 = guardian(org, "12345678909"), g3 = guardian(org, "98765432100");
        link(org, s, g1, true);
        assertThatThrownBy(() -> link(org, s, g2, true)).hasMessageContaining("student_guardian_financial");
        link(org, s, g2, false);
        assertThatThrownBy(() -> link(org, s, g3, false)).hasMessageContaining("two guardians");
    }

    @Test
    @DisplayName("RN-ALU-04: CPF único por organização (outra organização aceita); emergência: três campos ou nenhum")
    void cpfAndEmergency() {
        UUID a = org(), b = org();
        student(a, "52998224725");
        student(b, "52998224725");
        assertThatThrownBy(() -> student(a, "52998224725")).hasMessageContaining("student_org_cpf");
        UUID s = student(a, null);
        assertThatThrownBy(() -> admin.update("update student set emergency_name = 'Mãe' where id = ?", s)).hasMessageContaining("student_emergency_all_or_none");
        assertThatThrownBy(() -> student(a, "123")).hasMessageContaining("cpf");
    }

    // The rows share the container with the other integration tests (SeedDataIT empties account): leave nothing behind.
    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        for (String table : new String[]{"student_guardian", "guardian", "student"}) admin.update("delete from " + table);
        admin.update("delete from account where email like '%@x.example'");
    }
}
