package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.organization.OrganizationBase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-COM-01 (#81): checks, unicidade da leitura por aluno, RLS e privilégios de announcement e announcement_read. */
class AnnouncementSchemaIT extends OrganizationBase {
    @AfterEach
    void clean() {
        adminJdbc().update("delete from announcement_read");
        adminJdbc().update("delete from announcement");
        adminJdbc().update("delete from no_class_day");
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like 'ann-%@example.com'");
    }

    private UUID account() {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Autora Sintética', 'ACTIVE')", id, "ann-" + id + "@example.com");
        return id;
    }

    private static final String INSERT = "insert into announcement (id, organization_id, unit_id, target_kind, class_id, type, title, body, scheduled_for, sent_at, recipient_count, author_account_id) "
            + "values (?, ?, ?, ?, ?, ?, ?, 'Corpo', ?::timestamptz, ?::timestamptz, ?, ?)";

    private UUID announcement(Tenant t, UUID author) {
        UUID id = UUID.randomUUID();
        adminJdbc().update(INSERT, id, t.orgId(), t.unitId(), "UNIT", null, "GENERAL", "Aviso", null, "2026-10-01T12:00:00Z", 3, author);
        return id;
    }

    private void insert(Tenant t, UUID unit, String kind, String type, String title, String scheduled, String sent, Integer count, UUID author) {
        adminJdbc().update(INSERT, UUID.randomUUID(), t.orgId(), unit, kind, null, type, title, scheduled, sent, count, author);
    }

    @Test
    @DisplayName("Checks: destino incoerente, tipo e título inválidos e situação inconsistente são recusados pelo banco")
    void checks() {
        Tenant t = tenant();
        UUID author = account();
        String at = "2026-12-01T12:00:00Z";
        assertThatThrownBy(() -> insert(t, null, "UNIT", "GENERAL", "A", at, null, null, author)).hasMessageContaining("announcement_target_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "CLASS", "GENERAL", "A", at, null, null, author)).hasMessageContaining("announcement_target_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "ORGANIZATION", "GENERAL", "A", at, null, null, author)).hasMessageContaining("announcement_target_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "UNIT", "OTHER", "A", at, null, null, author)).hasMessageContaining("announcement_type_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "UNIT", "GENERAL", "", at, null, null, author)).hasMessageContaining("announcement_title_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "UNIT", "GENERAL", "x".repeat(121), at, null, null, author)).hasMessageContaining("announcement_title_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "UNIT", "GENERAL", "A", null, null, null, author)).hasMessageContaining("announcement_state_check");
        assertThatThrownBy(() -> insert(t, t.unitId(), "UNIT", "GENERAL", "A", null, at, null, author)).hasMessageContaining("announcement_state_check");
        assertThatThrownBy(() -> insert(t, null, "CLASSES", "NO_CLASS_DAY", "A", at, null, null, author)).hasMessageContaining("announcement_target_check");
        insert(t, null, "ORGANIZATION", "GENERAL", "A", at, null, null, author);
        insert(t, t.unitId(), "CLASSES", "NO_CLASS_DAY", "A", at, null, null, author);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA7: a leitura é única por aluno e comunicado, mesmo aberta por outra conta")
    void readUnique() {
        Tenant t = tenant();
        UUID author = account(), other = account();
        UUID id = announcement(t, author);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno Sintetico", "2018-01-01");
        String read = "insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)";
        adminJdbc().update(read, t.orgId(), id, student, author);
        assertThatThrownBy(() -> adminJdbc().update(read, t.orgId(), id, student, other)).hasMessageContaining("announcement_read_pkey");
    }

    @Test
    @DisplayName("CA6: o papel operacional não altera a leitura nem o comunicado e não enxerga nem grava na outra organização")
    void privilegesAndRls() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID author = account();
        UUID id = announcement(a, author);
        UUID student = BillingFixtures.student(adminJdbc(), a.orgId(), a.unitId(), "Aluno Sintetico", "2018-01-01");
        adminJdbc().update("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)", a.orgId(), id, student, author);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            for (String table : new String[] {"announcement", "announcement_read"}) {
                var rs = sql.executeQuery("select count(*) from " + table);
                rs.next();
                assertThat(rs.getInt(1)).as(table).isZero();
            }
            assertThat(sql.executeUpdate("delete from announcement")).isZero();
            var savepoint = app.setSavepoint();
            assertThatThrownBy(() -> sql.executeUpdate("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values ('" + a.orgId() + "', '"
                    + id + "', '" + student + "', '" + author + "')")).hasMessageContaining("row-level security");
            app.rollback(savepoint);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            for (String denied : new String[] {"update announcement_read set account_id = account_id", "delete from announcement_read", "update announcement_class set class_id = class_id"}) {
                savepoint = app.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate(denied)).as(denied).hasMessageContaining("permission denied");
                app.rollback(savepoint);
            }
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA9: o papel operacional altera e apaga o agendado, mas o enviado não muda nem some; só o envio (sent_at) passa; o dia sem aula apagado só zera a ligação")
    void sentIsFinal() throws Exception {
        Tenant t = tenant();
        UUID author = account();
        UUID sent = announcement(t, author);
        UUID scheduled = UUID.randomUUID();
        adminJdbc().update(INSERT, scheduled, t.orgId(), t.unitId(), "UNIT", null, "GENERAL", "Agendado", "2099-01-01T12:00:00Z", null, null, author);
        UUID day = UUID.randomUUID();
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason, origin) values (?, ?, ?, date '2099-02-01', date '2099-02-01', 'Motivo', 'COORDINATOR')",
                day, t.orgId(), t.unitId());
        adminJdbc().update("update announcement set no_class_day_id = ? where id = ?", day, sent);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + t.orgId() + "', true)");
            for (String denied : new String[] {"update announcement set title = 'x' where id = '" + sent + "'", "update announcement set sent_at = null where id = '" + sent + "'",
                    "delete from announcement where id = '" + sent + "'"}) {
                var savepoint = app.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate(denied)).as(denied).hasMessageContaining("announcement already sent");
                app.rollback(savepoint);
            }
            assertThat(sql.executeUpdate("update announcement set title = 'Novo' where id = '" + scheduled + "'")).isEqualTo(1);
            assertThat(sql.executeUpdate("update announcement set sent_at = now(), recipient_count = 3 where id = '" + scheduled + "'")).isEqualTo(1);
            assertThat(sql.executeUpdate("delete from no_class_day where id = '" + day + "'")).isEqualTo(1);
            app.rollback();
        }
        assertThat(adminJdbc().queryForObject("select no_class_day_id from announcement where id = ?", UUID.class, sent)).isEqualTo(day);
    }
}
