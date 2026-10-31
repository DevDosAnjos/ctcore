package br.com.ctcore.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-NOT-05: limpeza diária das notificações com 30 dias ou mais, por organização, sem DELETE para o papel operacional. */
class NotificationRetentionJobIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;

    final Clock clock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clean() {
        cleanLinked("retnot-");
    }

    private void note(Tenant t, UUID link, String createdAt, boolean read) {
        adminJdbc().update("insert into notification (id, organization_id, link_id, unit_id, type, params, created_at, read_at) "
                + "values (?, ?, ?, ?, 'PAYMENT_RECEIVED', '{}'::jsonb, ?::timestamptz, ?::timestamptz)", UUID.randomUUID(), t.orgId(), link, t.unitId(), createdAt,
                read ? "2026-06-14T00:00:00Z" : null);
    }

    private int count(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from notification where organization_id = ?", Integer.class, t.orgId());
    }

    private UUID coordinator(Tenant t) {
        return linked(t.orgId(), Role.COORDINATOR, "retnot-").activeLinkId();
    }

    private void seed(Tenant t) {
        UUID link = coordinator(t);
        note(t, link, "2026-05-17T12:00:01Z", false); // 29 dias e 23h59: fica
        note(t, link, "2026-05-16T12:00:00Z", false); // 30 dias exatos: some
        note(t, link, "2026-05-16T12:00:00Z", true); // 30 dias, lida: some
        note(t, link, "2026-05-15T12:00:00Z", true); // 31 dias: some
    }

    @Test
    @DisplayName("CA1, CA2, CA5: some o de 30 dias ou mais (lido ou não), fica o de 29, as duas organizações são limpas e o log traz só a quantidade")
    void purgesExpired() {
        var a = tenant();
        var b = tenant();
        seed(a);
        seed(b);
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        int deleted;
        try {
            deleted = new NotificationRetentionJob(transactions, jdbc, clock).runOnce();
        } finally {
            root.detachAppender(logs);
        }
        assertThat(deleted).isGreaterThanOrEqualTo(6);
        assertThat(count(a)).isEqualTo(1);
        assertThat(count(b)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from notification where created_at = '2026-05-17T12:00:01Z'", Integer.class)).isEqualTo(2);
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).isEqualTo("notification retention deleted=" + deleted));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("retnot-") || e.getFormattedMessage().contains("@"));
        assertThat(new NotificationRetentionJob(transactions, jdbc, clock).runOnce()).isZero();
    }

    @Test
    @DisplayName("CA3: falha em uma organização não impede as demais e o log leva só a classe da exceção")
    void failureIsIsolated() {
        var a = tenant();
        var b = tenant();
        seed(a);
        seed(b);
        OrganizationTransactions flaky = Mockito.mock(OrganizationTransactions.class);
        Mockito.when(flaky.provision(Mockito.any(), Mockito.<Supplier<Object>>any())).thenAnswer(i -> {
            if (a.orgId().equals(i.getArgument(0))) throw new IllegalStateException("falha retnot-erro@example.com");
            return transactions.provision(i.getArgument(0), i.<Supplier<Object>>getArgument(1));
        });
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            new NotificationRetentionJob(flaky, jdbc, clock).runOnce();
        } finally {
            root.detachAppender(logs);
        }
        assertThat(count(a)).isEqualTo(4);
        assertThat(count(b)).isEqualTo(1);
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("notification retention failed").contains("cause=IllegalStateException"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("retnot-erro") || e.getThrowableProxy() != null);
    }

    @Test
    @DisplayName("CA7: o papel operacional não tem DELETE; a função apaga só as vencidas da organização informada")
    void functionOnly() throws Exception {
        var a = tenant();
        var b = tenant();
        seed(a);
        seed(b);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            var savepoint = app.setSavepoint();
            assertThatThrownBy(() -> sql.executeUpdate("delete from notification")).hasMessageContaining("permission denied");
            app.rollback(savepoint);
            var rs = sql.executeQuery("select purge_expired_notifications('" + a.orgId() + "', '2026-05-16T12:00:00Z')");
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3);
            app.commit();
        }
        assertThat(count(a)).isEqualTo(1);
        assertThat(count(b)).isEqualTo(4);
    }
}
