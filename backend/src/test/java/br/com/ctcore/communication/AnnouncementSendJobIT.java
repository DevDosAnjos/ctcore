package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import br.com.ctcore.identity.AccountPrincipal;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-COM-02 (#82, R7, CA8): a tarefa envia os agendados vencidos, uma vez cada, e o erro de um não impede o outro. */
class AnnouncementSendJobIT extends AnnouncementBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired AnnouncementSender sender;

    private AnnouncementSendJob job(AnnouncementSender with) {
        return new AnnouncementSendJob(transactions, jdbc, with, Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    private UUID scheduled(Tenant t, AccountPrincipal author, UUID klass, String title, String when) {
        UUID id = announcement(t, author, "CLASS", t.unitId(), klass, "GENERAL", title, when, null);
        adminJdbc().update("update announcement set channels = '{PORTAL,EMAIL}' where id = ?", id);
        return id;
    }

    @Test
    @DisplayName("CA8: dois vencidos viram Enviados com e-mail uma única vez (duas execuções) e o futuro continua agendado")
    void sendsDueOnce() throws Exception {
        Tenant t = tenant();
        AccountPrincipal author = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Tarefa", teacher);
        enrolled(t, klass, teacher, "1990-01-01", "tarefa-1@example.test", "ACTIVE");
        UUID a = scheduled(t, author, klass, "Vencido A", "2026-10-05T11:00:00Z"), b = scheduled(t, author, klass, "Vencido B", "2026-10-01T12:00:00Z");
        UUID future = scheduled(t, author, klass, "Futuro", "2026-10-05T12:00:01Z");

        assertThat(job(sender).runOnce()).isEqualTo(2);
        assertThat(job(sender).runOnce()).isZero();
        assertThat(count("select count(*) from announcement where id in (?, ?) and sent_at = '2026-10-05T12:00:00Z' and recipient_count = 1", a, b)).isEqualTo(2);
        assertThat(count("select count(*) from announcement where id = ? and sent_at is null", future)).isEqualTo(1);
        assertThat(count("select count(*) from email_message where organization_id = ? and template = 'ANNOUNCEMENT'", t.orgId())).isEqualTo(2);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action like 'announcement.%'", t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA8: o erro de um comunicado não impede o envio do outro e o que falhou continua agendado")
    void oneFailureDoesNotStopTheOther() throws Exception {
        Tenant t = tenant();
        AccountPrincipal author = account(t, Role.COORDINATOR);
        UUID teacher = teacher(t, null), klass = klass(t, "Turma Erro", teacher);
        UUID bad = scheduled(t, author, klass, "Falha", "2026-10-01T12:00:00Z"), good = scheduled(t, author, klass, "Bom", "2026-10-02T12:00:00Z");
        AnnouncementSender failing = spy(sender);
        doThrow(new IllegalStateException("falha sintética anuncio-erro@example.test")).when(failing).dispatch(any(), eq(bad), any());
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            assertThat(job(failing).runOnce()).isEqualTo(1);
        } finally {
            root.detachAppender(logs);
        }
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("announcement send failed").contains("cause=IllegalStateException"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("anuncio-erro") || e.getThrowableProxy() != null);
        assertThat(count("select count(*) from announcement where id = ? and sent_at is not null", good)).isEqualTo(1);
        assertThat(count("select count(*) from announcement where id = ? and sent_at is null", bad)).isEqualTo(1);
    }
}
