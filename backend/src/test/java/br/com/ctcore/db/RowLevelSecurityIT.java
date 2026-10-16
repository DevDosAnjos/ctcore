package br.com.ctcore.db;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RowLevelSecurityIT extends PostgresIT {
    @Test
    @DisplayName("RNF-06 / RN-ORG-01 / RN-ORG-02: real feature flags enforce RLS with runtime login")
    void isolatesRealFlags() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        adminJdbc().update("insert into organization_feature (organization_id, feature_key, enabled) values (?, 'proof', true), (?, 'proof', false)", a, b);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime");
             Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            try (var rows = sql.executeQuery("select rolsuper, rolbypassrls, exists(select 1 from pg_class where relowner = (select oid from pg_roles where rolname = current_user) and relnamespace = 'public'::regnamespace) from pg_roles where rolname = current_user")) {
                rows.next();
                assertThat(rows.getBoolean(1)).isFalse();
                assertThat(rows.getBoolean(2)).isFalse();
                assertThat(rows.getBoolean(3)).isFalse();
            }
            for (String scope : new String[]{a.toString(), ""}) {
                sql.execute("select set_config('app.organization_id', '" + scope + "', true)");
                try (var rows = sql.executeQuery("select organization_id from organization_feature")) {
                    if (scope.isEmpty()) assertThat(rows.next()).isFalse();
                    else {
                        assertThat(rows.next()).isTrue();
                        assertThat(rows.getObject(1)).isEqualTo(a);
                        assertThat(rows.next()).isFalse();
                    }
                }
                assertThat(sql.executeUpdate("delete from organization_feature where organization_id = '" + b + "'")).isZero();
                assertThat(sql.executeUpdate("update organization_feature set enabled = true where organization_id = '" + b + "'")).isZero();
                for (String denied : new String[]{
                        "insert into organization_feature (organization_id, feature_key, enabled) values ('" + b + "', 'denied', true)",
                        "update organization_feature set organization_id = '" + b + "' where organization_id = '" + a + "'"}) {
                    var savepoint = app.setSavepoint();
                    if (scope.isEmpty() && denied.startsWith("update")) assertThat(sql.executeUpdate(denied)).isZero();
                    else assertThatThrownBy(() -> sql.executeUpdate(denied)).hasMessageContaining("row-level security");
                    app.rollback(savepoint);
                }
            }
            app.rollback();
        }
    }
}
