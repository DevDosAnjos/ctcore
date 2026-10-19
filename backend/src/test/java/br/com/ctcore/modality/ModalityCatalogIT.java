package br.com.ctcore.modality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModalityCatalogIT extends PostgresIT {
    @Test
    @DisplayName("CA1: 14 modalidades de nome único; o papel operacional só lê o catálogo")
    void catalogIsReadOnlyForRuntime() throws Exception {
        assertThat(adminJdbc().queryForObject("select count(distinct name) from modality", Integer.class)).isEqualTo(14);
        assertThat(adminJdbc().queryForObject("select count(*) from modality", Integer.class)).isEqualTo(14);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            assertThat(sql.executeQuery("select 1 from modality").next()).isTrue();
            for (String denied : new String[] {"insert into modality (name) values ('Sambo')", "update modality set name = 'x'", "delete from modality"}) {
                assertThatThrownBy(() -> sql.executeUpdate(denied)).hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    @DisplayName("CA9: RLS de organization_modality e modality_request com o papel operacional: só a organização do contexto")
    void rlsIsolatesOrganizationTables() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), account = UUID.randomUUID(), judo = UUID.randomUUID();
        var admin = adminJdbc();
        admin.update("insert into organization (id, name) values (?, 'A'), (?, 'B')", a, b);
        admin.update("insert into account (id, email, name, status) values (?, ?, 'Synthetic', 'ACTIVE')", account, account + "@synthetic.example");
        judo = admin.queryForObject("select id from modality where name = 'Judô'", UUID.class);
        admin.update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true), (?, ?, true)", a, judo, b, judo);
        admin.update("insert into modality_request (id, organization_id, requested_by, name) values (?, ?, ?, 'Sambo'), (?, ?, ?, 'Sumô')",
                UUID.randomUUID(), a, account, UUID.randomUUID(), b, account);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            for (String table : new String[] {"organization_modality", "modality_request"}) {
                sql.execute("select set_config('app.organization_id', '', true)");
                try (var rows = sql.executeQuery("select organization_id from " + table)) {
                    assertThat(rows.next()).isFalse();
                }
                sql.execute("select set_config('app.organization_id', '" + a + "', true)");
                try (var rows = sql.executeQuery("select organization_id from " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getObject(1)).isEqualTo(a);
                    assertThat(rows.next()).isFalse();
                }
                String set = table.equals("modality_request") ? "name = 'x'" : "active = false";
                assertThat(sql.executeUpdate("update " + table + " set " + set + " where organization_id = '" + b + "'")).isZero();
                assertThat(sql.executeUpdate("delete from " + table + " where organization_id = '" + b + "'")).isZero();
            }
            UUID other = judo;
            for (String denied : new String[] {
                    "insert into organization_modality (organization_id, modality_id, active) values ('" + b + "', '" + other + "', true)",
                    "insert into modality_request (id, organization_id, requested_by, name) values ('" + UUID.randomUUID() + "', '" + b + "', '" + account + "', 'Aikido')"}) {
                var savepoint = app.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate(denied)).hasMessageContaining("row-level security");
                app.rollback(savepoint);
            }
            app.rollback();
        }
    }
}
