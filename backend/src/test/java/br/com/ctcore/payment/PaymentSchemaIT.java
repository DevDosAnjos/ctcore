package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.Role;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CA19 (banco): isolamento por organização, ausência de DELETE, checks, função do aviso e o novo tipo de notificação. */
class PaymentSchemaIT extends PaymentOnlineBase {
    private static final String[] TABLES = {"payment_attempt", "payment_attempt_charge", "payment_notice", "payment_duplicate", "payment_alert"};

    private UUID attempt(Tenant t, UUID charge, String providerId) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into payment_attempt (id, organization_id, unit_id, method, amount_cents, provider_id) values (?, ?, ?, 'PIX', 100, ?)", id, t.orgId(), t.unitId(), providerId);
        adminJdbc().update("insert into payment_attempt_charge (organization_id, attempt_id, charge_id, amount_cents) values (?, ?, ?, 100)", t.orgId(), id, charge);
        adminJdbc().update("insert into payment_duplicate (id, organization_id, charge_id, attempt_id, unit_id, amount_cents, method, provider_id) values (?, ?, ?, ?, ?, 100, 'PIX', ?)",
                UUID.randomUUID(), t.orgId(), charge, id, t.unitId(), providerId);
        adminJdbc().update("insert into payment_alert (id, organization_id, unit_id, attempt_id, kind, detail) values (?, ?, ?, ?, 'DIVERGENCE', 'x')", UUID.randomUUID(), t.orgId(), t.unitId(), id);
        adminJdbc().update("insert into payment_notice (id, organization_id, provider_id, content, result) values (?, ?, ?, '{}'::jsonb, 'APPLIED')", UUID.randomUUID(), t.orgId(), providerId);
        return id;
    }

    private static int count(Statement sql, String query) throws Exception {
        var rs = sql.executeQuery(query);
        rs.next();
        return rs.getInt(1);
    }

    @Test
    @DisplayName("CA19: sem contexto o papel de execução não lê nenhuma linha; com o contexto de outra organização também não; ninguém apaga; aviso desconhecido grava e não lê")
    void isolationAndNoDelete() throws Exception {
        var a = connected();
        var b = connected();
        attempt(a, charge(a, "Maria Souza", 100, "OPEN"), "prov-a-" + UUID.randomUUID());
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            for (String table : TABLES) assertThat(count(sql, "select count(*) from " + table)).as(table + " sem contexto").isZero();
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            for (String table : TABLES) assertThat(count(sql, "select count(*) from " + table)).as(table + " outra organização").isZero();
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            assertThat(count(sql, "select count(*) from payment_attempt")).isEqualTo(1);
            for (String table : TABLES) {
                var savepoint = app.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate("delete from " + table)).as(table).hasMessageContaining("permission denied");
                app.rollback(savepoint);
            }
            app.rollback();
            app.setAutoCommit(true);
            String unknown = "unknown-" + UUID.randomUUID();
            assertThat(sql.executeUpdate("insert into payment_notice (id, provider_id, content, result) values (gen_random_uuid(), '" + unknown + "', '{}'::jsonb, 'UNKNOWN')")).isEqualTo(1);
            assertThat(count(sql, "select count(*) from payment_notice where provider_id = '" + unknown + "'")).isZero();
            assertThat(count(adminJdbc(), unknown)).isEqualTo(1);
        }
    }

    private static int count(org.springframework.jdbc.core.JdbcTemplate admin, String provider) {
        return admin.queryForObject("select count(*) from payment_notice where provider_id = ?", Integer.class, provider);
    }

    @Test
    @DisplayName("função do aviso devolve só a organização dona; checks do banco; alerta em aberto não repete; PAYMENT_REFUSED é aceito")
    void checksAndOwner() {
        var t = connected();
        UUID charge = charge(t, "Maria Souza", 100, "OPEN");
        String provider = "prov-" + UUID.randomUUID();
        UUID attempt = attempt(t, charge, provider);
        assertThat(adminJdbc().queryForObject("select payment_attempt_owner(?)", UUID.class, provider)).isEqualTo(t.orgId());
        assertThat(adminJdbc().queryForObject("select payment_attempt_owner('nope')", UUID.class)).isNull();
        String insert = "insert into payment_attempt (id, organization_id, unit_id, method, amount_cents, provider_id) values (?, ?, ?, ?, ?, ?)";
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), "CASH", 100, "p1")).hasMessageContaining("payment_attempt_method_check");
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), "PIX", 0, "p2")).hasMessageContaining("payment_attempt_amount_cents_check");
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), "PIX", 100, provider)).hasMessageContaining("provider_id");
        String alert = "insert into payment_alert (id, organization_id, unit_id, attempt_id, kind, detail) values (?, ?, ?, ?, 'DIVERGENCE', 'y')";
        assertThatThrownBy(() -> adminJdbc().update(alert, UUID.randomUUID(), t.orgId(), t.unitId(), attempt)).hasMessageContaining("payment_alert_open_once");
        adminJdbc().update("update payment_alert set resolved_at = now() where attempt_id = ?", attempt);
        assertThat(adminJdbc().update(alert, UUID.randomUUID(), t.orgId(), t.unitId(), attempt)).isEqualTo(1);
        var admin = linked(t.orgId(), Role.ADMINISTRATOR, "payon-", t.unitId());
        assertThat(adminJdbc().update("insert into notification (id, organization_id, link_id, unit_id, type) values (?, ?, ?, ?, 'PAYMENT_REFUSED')",
                UUID.randomUUID(), t.orgId(), admin.activeLinkId(), t.unitId())).isEqualTo(1);
    }
}
