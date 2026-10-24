package br.com.ctcore.notification;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import br.com.ctcore.organization.OrganizationBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-NOT-04: convite sem aceite há 7 dias avisa os Coordenadores, uma vez por convite. */
class InvitePendingJobIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired br.com.ctcore.isolation.OrganizationTransactions transactions;
    @Autowired Notifications notifications;

    final Clock clock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clean() {
        adminJdbc().update("delete from account_invite where account_id in (select id from account where email like 'invnot-%@example.com')");
        cleanLinked("invnot-");
    }

    private UUID invite(Tenant t, String createdAt, String state) {
        UUID account = UUID.randomUUID(), link = UUID.randomUUID(), invite = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Convidada Sintética', 'INVITED')", account, "invnot-" + account + "@example.com");
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, 'ADMINISTRATOR', 'PENDING')", link, account, t.orgId());
        adminJdbc().update("insert into account_invite (id, account_id, link_id, organization_id, code_hash, expires_at, created_at, used_at, canceled_at) "
                + "values (?, ?, ?, ?, ?, ?::timestamptz, ?::timestamptz, ?::timestamptz, ?::timestamptz)", invite, account, link, t.orgId(), "h" + invite,
                "2026-07-01T00:00:00Z", createdAt, "used".equals(state) ? "2026-06-10T00:00:00Z" : null, "canceled".equals(state) ? "2026-06-10T00:00:00Z" : null);
        return invite;
    }

    @Test
    @DisplayName("R2: falha ao avisar vai ao log só com a classe da exceção, sem o endereço")
    void failureLogsOnlyClass() {
        var t = tenant();
        linked(t.orgId(), Role.COORDINATOR, "invnot-");
        invite(t, "2026-06-08T12:00:00Z", "open");
        Notifications failing = org.mockito.Mockito.mock(Notifications.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("falha convite-erro@example.com")).when(failing)
                .publish(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            new InvitePendingJob(transactions, jdbc, failing, clock).runOnce();
        } finally {
            root.detachAppender(logs);
        }
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("invite pending notice failed").contains("cause=IllegalStateException"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("convite-erro") || e.getThrowableProxy() != null);
    }

    @Test
    @DisplayName("CA7: convite de 7 dias sem uso nem cancelamento avisa os Coordenadores; usado, cancelado e de 6 dias não; repetir não duplica")
    void notifiesOnce() {
        var t = tenant();
        var coordinator = linked(t.orgId(), Role.COORDINATOR, "invnot-");
        var admin = linked(t.orgId(), Role.ADMINISTRATOR, "invnot-", t.unitId());
        UUID old = invite(t, "2026-06-08T12:00:00Z", "open");
        invite(t, "2026-06-08T12:00:00Z", "used");
        invite(t, "2026-06-08T12:00:00Z", "canceled");
        invite(t, "2026-06-09T12:00:00Z", "open");
        var job = new InvitePendingJob(transactions, jdbc, notifications, clock);

        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(job.runOnce()).isEqualTo(1);
        var rows = adminJdbc().queryForList("select type, ref_type, ref_id, link_id, params::text as params from notification");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "INVITE_PENDING").containsEntry("ref_type", "invite").containsEntry("ref_id", old)
                .containsEntry("link_id", coordinator.activeLinkId());
        assertThat((String) rows.get(0).get("params")).contains("Convidada Sintética");
        assertThat(admin.activeLinkId()).isNotEqualTo(rows.get(0).get("link_id"));
    }
}
