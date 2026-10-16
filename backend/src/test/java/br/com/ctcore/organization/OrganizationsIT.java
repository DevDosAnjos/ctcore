package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.isolation.Scopes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationsIT extends PostgresIT {
    @Autowired Organizations organizations;
    JdbcTemplate jdbc = adminJdbc();


    private java.util.Optional<Map<String,Object>> settings(UUID org) {
        if (org == null) return organizations.settings(null);
        try (var scope = Scopes.open(org, null)) { return organizations.settings(org); }
    }
    private java.util.Optional<Map<String,Object>> limits(UUID org) {
        try (var scope = Scopes.open(org, null)) { return organizations.limits(org); }
    }
    private java.util.Optional<Map<String,Object>> unitSettings(UUID org, UUID unit) {
        try (var scope = Scopes.open(org, unit)) { return organizations.unitSettings(org, unit); }
    }

    Organizations.InitialOrganization input() {
        UUID id = UUID.randomUUID();
        return new Organizations.InitialOrganization(id, "Synthetic organization", UUID.randomUUID(), "Initial unit", id.toString());
    }

    @Test
    @DisplayName("RN-ORG-04/16: atomic initialization persists all defaults and preserves snapshots")
    void initializesDefaultsAndSnapshots() {
        var first = input();
        organizations.initialize(first, Organizations.Defaults.standard());
        var second = input();
        organizations.initialize(second, new Organizations.Defaults(45));
        Map<String, Object> settings = settings(first.id()).orElseThrow();
        assertThat(settings).containsEntry("child_name", "Infantil").containsEntry("child_min_age", 4)
                .containsEntry("child_max_age", 12).containsEntry("youth_name", "Juvenil")
                .containsEntry("youth_min_age", 13).containsEntry("youth_max_age", 17)
                .containsEntry("adult_name", "Adulto").containsEntry("adult_min_age", 18)
                .containsEntry("adult_max_age", null).containsEntry("deactivation_days", 30)
                .containsEntry("former_student_retention_months", 12).containsEntry("trial_retention_months", 6)
                .containsEntry("trial_limit", 2).containsEntry("cross_unit_training", "plan");
        for (String field : new String[]{"graduation_requirements", "modality_settings", "terms_versions"}) {
            assertThat(settings.get(field).toString()).isEqualTo("{}");
        }
        assertThat(settings(second.id()).orElseThrow()).containsEntry("deactivation_days", 45);
        var unit = unitSettings(first.id(), first.unitId()).orElseThrow();
        assertThat(unit).containsEntry("enrollment_fee_enabled", false).containsEntry("enrollment_fee_cents", 0L)
                .containsEntry("cash_fee_cents", 0L).containsEntry("pix_fee_cents", 0L)
                .containsEntry("bank_slip_fee_cents", 0L).containsEntry("credit_card_fee_cents", 0L)
                .containsEntry("debit_card_fee_cents", 0L).containsEntry("pix_key", null)
                .containsEntry("automatic_debit_fee_cents", 0L).containsEntry("manual_pix_fee_cents", 0L)
                .containsEntry("terminal_card_fee_cents", 0L).containsEntry("transfer_fee_cents", 0L)
                .containsEntry("payment_account_reference", null).containsEntry("directory_enabled", false)
                .containsEntry("trial_link_enabled", true).containsEntry("self_registration_link_enabled", true);
        assertThat(jdbc.queryForObject("select due_days::text from unit_settings where unit_id = ?", String.class, first.unitId()))
                .isEqualTo("{1,5,10,15}");
        assertThat(jdbc.queryForObject("select reminder_days::text from unit_settings where unit_id = ?", String.class, first.unitId()))
                .isEqualTo("{-3,0,3,10}");
        assertThat(jdbc.queryForObject("select teacher_permissions::text from unit_settings where unit_id = ?", String.class, first.unitId()))
                .isEqualTo("{t,t,t,t}");
        assertThat(jdbc.queryForMap("select active, timezone from unit where id = ?", first.unitId()))
                .containsEntry("active", true).containsEntry("timezone", "America/Sao_Paulo");
        assertThat(limits(first.id()).orElseThrow()).containsEntry("students", null)
                .containsEntry("management_users", null).containsEntry("units", null).containsEntry("modules", null)
                .containsEntry("operational_units", 10).containsEntry("operational_management_users", 200);
        assertThat(unitSettings(second.id(), first.unitId())).isEmpty();
        assertThat(settings(UUID.randomUUID())).isEmpty();
        assertThat(limits(UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> settings(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> unitSettings(first.id(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> organizations.initialize(null, Organizations.Defaults.standard())).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> organizations.initialize(input(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("RN-ORG-04: failure after organization insert rolls back all provisioning")
    void rollsBack() {
        var first = input();
        organizations.initialize(first, Organizations.Defaults.standard());
        var failed = new Organizations.InitialOrganization(UUID.randomUUID(), "Failed", first.unitId(), "Duplicate", "duplicate");
        assertThatThrownBy(() -> organizations.initialize(failed, Organizations.Defaults.standard()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from organization where id = ?", Integer.class, failed.id())).isZero();
        for (String table : new String[]{"unit", "organization_settings", "unit_settings", "organization_limits"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where organization_id = ?", Integer.class, failed.id())).isZero();
        }
    }

    @Test
    @DisplayName("RN-ORG-04: failure on the final insert also leaves no partial initialization")
    void rollsBackLateFailure() {
        var failed = input();
        jdbc.execute("create function reject_provisioning_test() returns trigger language plpgsql as $$ begin raise exception 'provoked failure'; end $$");
        jdbc.execute("create trigger reject_provisioning_test before insert on organization_limits for each row when (new.organization_id = '" + failed.id() + "') execute function reject_provisioning_test()");
        try {
            assertThatThrownBy(() -> organizations.initialize(failed, Organizations.Defaults.standard())).hasMessageContaining("provoked failure");
            assertThat(jdbc.queryForObject("select count(*) from organization where id = ?", Integer.class, failed.id())).isZero();
            for (String table : new String[]{"unit", "organization_settings", "unit_settings", "organization_limits"}) {
                assertThat(jdbc.queryForObject("select count(*) from " + table + " where organization_id = ?", Integer.class, failed.id())).isZero();
            }
        } finally {
            jdbc.execute("drop trigger reject_provisioning_test on organization_limits");
            jdbc.execute("drop function reject_provisioning_test()");
        }
    }

    @Test
    @DisplayName("RN-ORG-10: organization settings are shared; due dates and payments belong to each unit")
    void settingsOwnershipAndConstraints() {
        var org = input();
        organizations.initialize(org, Organizations.Defaults.standard());
        UUID unit2 = UUID.randomUUID();
        jdbc.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Second', ?)", unit2, org.id(), unit2.toString());
        jdbc.update("insert into unit_settings (organization_id, unit_id, due_days, pix_key) values (?, ?, '{2,28}', 'synthetic-key')", org.id(), unit2);
        assertThat(unitSettings(org.id(), unit2).orElseThrow()).containsEntry("pix_key", "synthetic-key");
        assertThat(unitSettings(org.id(), org.unitId()).orElseThrow()).containsEntry("pix_key", null);
        assertThat(jdbc.queryForObject("select count(*) from unit u join organization_settings s on s.organization_id = u.organization_id where u.organization_id = ?", Integer.class, org.id())).isEqualTo(2);
        var other = input();
        organizations.initialize(other, Organizations.Defaults.standard());
        assertThatThrownBy(() -> jdbc.update("update unit_settings set organization_id = ? where unit_id = ?", other.id(), unit2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for (String assignment : new String[]{"deactivation_days = 14", "deactivation_days = 91", "trial_limit = 0", "trial_limit = 4",
                "child_min_age = -1", "child_max_age = 2", "youth_min_age = 10", "youth_max_age = 1", "adult_min_age = 15",
                "adult_max_age = 1", "former_student_retention_months = 0", "trial_retention_months = 0",
                "cross_unit_training = 'invalid'", "graduation_requirements = '[]'", "modality_settings = '[]'", "terms_versions = '[]'"}) {
            assertThatThrownBy(() -> jdbc.update("update organization_settings set " + assignment + " where organization_id = ?", org.id()))
                    .as(assignment).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        for (String assignment : new String[]{"due_days = '{0}'", "due_days = '{29}'", "due_days = '{}'", "due_days = '{1,NULL}'",
                "enrollment_fee_cents = -1", "cash_fee_cents = -1", "pix_fee_cents = -1", "bank_slip_fee_cents = -1",
                "credit_card_fee_cents = -1", "debit_card_fee_cents = -1", "automatic_debit_fee_cents = -1",
                "manual_pix_fee_cents = -1", "terminal_card_fee_cents = -1", "transfer_fee_cents = -1", "teacher_permissions = '{true}'"}) {
            assertThatThrownBy(() -> jdbc.update("update unit_settings set " + assignment + " where unit_id = ?", org.unitId()))
                    .as(assignment).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> jdbc.update("update organization set name = ' ' where id = ?", org.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update unit set name = '' where id = ?", org.unitId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into organization_settings (organization_id) values (?)", UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into unit_settings (organization_id, unit_id) values (?, ?)", org.id(), UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CA6: all five tables enforce RLS for a non-owner without superuser/BYPASSRLS")
    void rowLevelSecurity() throws SQLException {
        var a = input();
        var b = input();
        organizations.initialize(a, Organizations.Defaults.standard());
        organizations.initialize(b, Organizations.Defaults.standard());
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime");
                Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try (var result = statement.executeQuery("select rolsuper, rolbypassrls from pg_roles where rolname = current_user")) {
                result.next();
                assertThat(result.getBoolean(1)).isFalse();
                assertThat(result.getBoolean(2)).isFalse();
            }
            for (String table : new String[]{"organization", "unit", "organization_settings", "unit_settings", "organization_limits"}) {
                String key = table.equals("organization") ? "id" : "organization_id";
                statement.execute("select set_config('app.organization_id', '" + a.id() + "', true)");
                try (var rows = statement.executeQuery("select " + key + " from " + table)) {
                    assertThat(rows.next()).as(table).isTrue();
                    assertThat(rows.getObject(1)).isEqualTo(a.id());
                    assertThat(rows.next()).isFalse();
                }
                assertThat(statement.executeUpdate("update " + table + " set " + key + " = " + key + " where " + key + " = '" + b.id() + "'"))
                        .isZero();
                assertThat(statement.executeUpdate("update " + table + " set " + key + " = " + key + " where " + key + " = '" + a.id() + "'"))
                        .isEqualTo(1);
                var savepoint = connection.setSavepoint();
                assertThatThrownBy(() -> statement.executeUpdate("update " + table + " set " + key + " = '" + b.id() + "' where " + key + " = '" + a.id() + "'"))
                        .hasMessageContaining("row-level security");
                connection.rollback(savepoint);
                statement.execute("select set_config('app.organization_id', '', true)");
                try (var rows = statement.executeQuery("select count(*) from " + table)) {
                    rows.next();
                    assertThat(rows.getInt(1)).as(table).isZero();
                }
                assertThat(statement.executeUpdate("delete from " + table)).isZero();
                assertThat(statement.executeUpdate("update " + table + " set " + key + " = " + key)).isZero();
            }
            for (String context : new String[]{a.id().toString(), ""}) {
                statement.execute("select set_config('app.organization_id', '" + context + "', true)");
                for (String insert : new String[]{
                        "insert into organization (id, name) values ('" + UUID.randomUUID() + "', 'denied')",
                        "insert into unit (organization_id, name, short_name) values ('" + b.id() + "', 'denied', 'denied')",
                        "insert into organization_settings (organization_id) values ('" + b.id() + "')",
                        "insert into unit_settings (organization_id, unit_id) values ('" + b.id() + "', '" + b.unitId() + "')",
                        "insert into organization_limits (organization_id) values ('" + b.id() + "')"}) {
                    var savepoint = connection.setSavepoint();
                    assertThatThrownBy(() -> statement.execute(insert)).hasMessageContaining("row-level security");
                    connection.rollback(savepoint);
                }
            }
            connection.rollback();
            try (var result = statement.executeQuery("select nullif(current_setting('app.organization_id', true), '')")) {
                result.next();
                assertThat(result.getString(1)).isNull();
            }
        }
    }
}
