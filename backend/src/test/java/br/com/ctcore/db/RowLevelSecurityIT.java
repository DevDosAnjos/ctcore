package br.com.ctcore.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova a técnica de isolamento (D9): tabela criada só no teste. A política real é da #14.
 * O superusuário ignora RLS, por isso as consultas usam um papel comum que não é dono da tabela.
 */
class RowLevelSecurityIT {

    static final UUID ORG_A = UUID.randomUUID();
    static final UUID ORG_B = UUID.randomUUID();

    @BeforeAll
    static void createSchema() throws SQLException {
        try (Connection admin = connect(PostgresIT.POSTGRES.getUsername(), PostgresIT.POSTGRES.getPassword());
                Statement s = admin.createStatement()) {
            s.execute("create role rls_app login password 'rls_app' nosuperuser nobypassrls");
            s.execute("create table rls_items (id uuid primary key default gen_random_uuid(),"
                    + " organization_id uuid not null, name text not null)");
            s.execute("alter table rls_items enable row level security");
            s.execute("alter table rls_items force row level security");
            s.execute("create policy by_org on rls_items"
                    + " using (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)"
                    + " with check (organization_id = nullif(current_setting('app.organization_id', true), '')::uuid)");
            s.execute("grant select, insert on rls_items to rls_app");
        }
    }

    static Connection connect(String user, String password) throws SQLException {
        return DriverManager.getConnection(PostgresIT.POSTGRES.getJdbcUrl(), user, password);
    }

    static void useOrganization(Statement s, UUID org) throws SQLException {
        s.execute("select set_config('app.organization_id', '" + org + "', false)");
    }

    @Test
    @DisplayName("RNF-30 / CA15: RLS isola linhas por organização, sem organização não vê nada e insert cruzado é recusado")
    void isolatesRowsByOrganization() throws SQLException {
        try (Connection app = connect("rls_app", "rls_app"); Statement s = app.createStatement()) {
            useOrganization(s, ORG_A);
            s.execute("insert into rls_items (organization_id, name) values ('" + ORG_A + "', 'a')");
            useOrganization(s, ORG_B);
            s.execute("insert into rls_items (organization_id, name) values ('" + ORG_B + "', 'b')");

            useOrganization(s, ORG_A);
            try (var rs = s.executeQuery("select name from rls_items")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("a");
                assertThat(rs.next()).isFalse();
            }

            // INSERT de linha da organização B com a sessão em A é recusado.
            assertThatThrownBy(() -> s.execute(
                    "insert into rls_items (organization_id, name) values ('" + ORG_B + "', 'cruzado')"))
                    .hasMessageContaining("row-level security");

            // Sem organização definida: zero linhas.
            s.execute("select set_config('app.organization_id', '', false)");
            try (var rs = s.executeQuery("select count(*) from rls_items")) {
                rs.next();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }
}
