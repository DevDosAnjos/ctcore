package br.com.ctcore.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-MOD-02: padrão do catálogo, cópia na migração e isolamento de graduation_level no banco. */
class GraduationCatalogIT extends PostgresIT {
    @AfterEach
    void clean() {
        adminJdbc().update("delete from graduation_level");
        adminJdbc().update("delete from organization_modality");
    }

    @Test
    @DisplayName("CA1: padrão do Jiu-Jitsu com os níveis do IBJJF em ordem; outras modalidades sem padrão; papel operacional só lê")
    void defaultIsReadOnlyAndOnlyForJiuJitsu() throws Exception {
        List<String> names = adminJdbc().queryForList("select d.name from modality_default_level d join modality m on m.id = d.modality_id "
                + "where m.name = 'Jiu-Jitsu' order by d.position", String.class);
        assertThat(names).hasSize(19).startsWith("Cinza e Branca").contains("Branca", "Azul", "Roxa", "Marrom", "Preta", "Coral").endsWith("Vermelha");
        assertThat(names.indexOf("Branca")).isLessThan(names.indexOf("Azul"));
        assertThat(adminJdbc().queryForObject("select count(*) from modality_default_level d join modality m on m.id = d.modality_id where m.name <> 'Jiu-Jitsu'",
                Integer.class)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from modality_default_level where kids and max_age is null", Integer.class)).isZero();
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            assertThat(sql.executeQuery("select 1 from modality_default_level").next()).isTrue();
            for (String denied : new String[] {
                    "insert into modality_default_level (modality_id, position, name, color, max_degree, kids) select id, 99, 'x', '#000000', 0, false from modality limit 1",
                    "update modality_default_level set name = 'x'", "delete from modality_default_level"}) {
                assertThatThrownBy(() -> sql.executeUpdate(denied)).hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("CA3: a migração copia o padrão para quem já tinha o Jiu-Jitsu ativo, e só para ele")
    void migrationBackfillsActiveModalities() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).schemas("backfill_check").createSchemas(true)
                .locations("classpath:db/migration").target("19").load().migrate();
        var admin = adminJdbc();
        UUID active = UUID.randomUUID(), inactive = UUID.randomUUID();
        admin.update("insert into backfill_check.organization (id, name) values (?, 'A'), (?, 'B')", active, inactive);
        admin.update("insert into backfill_check.organization_modality (organization_id, modality_id, active) "
                + "select ?, id, true from backfill_check.modality where name in ('Jiu-Jitsu', 'Judô')", active);
        admin.update("insert into backfill_check.organization_modality (organization_id, modality_id, active) "
                + "select ?, id, false from backfill_check.modality where name = 'Jiu-Jitsu'", inactive);
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).schemas("backfill_check")
                .locations("classpath:db/migration").load().migrate();
        assertThat(admin.queryForObject("select count(*) from backfill_check.graduation_level where organization_id = ?", Integer.class, active)).isEqualTo(19);
        assertThat(admin.queryForObject("select count(*) from backfill_check.graduation_level where organization_id = ?", Integer.class, inactive)).isZero();
        admin.execute("drop schema backfill_check cascade");
    }

    @Test
    @DisplayName("CA9: RLS de graduation_level com o papel operacional: só a organização do contexto")
    void rlsIsolatesLevels() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var admin = adminJdbc();
        admin.update("insert into organization (id, name) values (?, 'A'), (?, 'B')", a, b);
        UUID judo = admin.queryForObject("select id from modality where name = 'Judô'", UUID.class);
        admin.update("insert into graduation_level (id, organization_id, modality_id, position, name, color, max_degree, kids) values "
                + "(?, ?, ?, 0, 'Branca', '#FFFFFF', 4, false), (?, ?, ?, 0, 'Branca', '#FFFFFF', 4, false)", UUID.randomUUID(), a, judo, UUID.randomUUID(), b, judo);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select 1 from graduation_level")) {
                assertThat(rows.next()).isFalse();
            }
            sql.execute("select set_config('app.organization_id', '" + a + "', true)");
            try (var rows = sql.executeQuery("select organization_id from graduation_level")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isEqualTo(a);
                assertThat(rows.next()).isFalse();
            }
            assertThat(sql.executeUpdate("update graduation_level set name = 'x' where organization_id = '" + b + "'")).isZero();
            assertThat(sql.executeUpdate("delete from graduation_level where organization_id = '" + b + "'")).isZero();
            var savepoint = app.setSavepoint();
            assertThatThrownBy(() -> sql.executeUpdate("insert into graduation_level (id, organization_id, modality_id, position, name, color, max_degree, kids) values ('"
                    + UUID.randomUUID() + "', '" + b + "', '" + judo + "', 1, 'Azul', '#0000FF', 4, false)")).hasMessageContaining("row-level security");
            app.rollback(savepoint);
            app.rollback();
        }
    }
}
