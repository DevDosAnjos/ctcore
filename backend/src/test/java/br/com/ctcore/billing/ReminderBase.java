package br.com.ctcore.billing;

import br.com.ctcore.integration.SmsSender;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.organization.OrganizationBase;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Apoio dos testes de lembretes (#79): relógio fixo em 2026-10-05, alunos com e-mail, cobranças em datas dadas e o histórico. Hoje de referência: 2026-10-05. */
abstract class ReminderBase extends OrganizationBase {
    static final String NOON = "2026-10-05T12:00:00Z";

    @Autowired protected JdbcClient jdbc;
    @Autowired protected OrganizationTransactions transactions;
    @Autowired protected EmailService email;
    @Autowired protected SmsSender sms;
    @Autowired protected MessageSource messages;

    @AfterEach
    void cleanReminders() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from email_message where to_email like 'rem-%' or to_email like 'gen-%'");
    }

    protected ReminderJob job(String instant) {
        return job(instant, sms);
    }

    protected ReminderJob job(String instant, SmsSender smsSender) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ReminderJob(transactions, jdbc, new ReminderSender(jdbc, email, clock), new BlockWarningSms(jdbc, smsSender, messages, clock), clock);
    }

    protected ReminderJob job() {
        return job(NOON);
    }

    protected UUID adult(Tenant t, String address) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set email = ?, phone = '11912345678' where id = ?", address, s);
        return s;
    }

    protected UUID charge(Tenant t, UUID student, String due, String status) {
        UUID id = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 15000, status);
        adminJdbc().update("update charge set due_on = ?::date, notified_at = now() where id = ?", due, id);
        return id;
    }

    protected List<Map<String, Object>> mails(String like) {
        return adminJdbc().queryForList("select template, subject, status, organization_id from email_message where to_email like ? order by created_at", like);
    }

    protected List<Map<String, Object>> history(UUID charge) {
        return adminJdbc().queryForList("select kind, offset_days, channel, status, reason, sent_by_account_id from charge_reminder where charge_id = ? order by created_at, channel", charge);
    }

    protected ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
        return logs;
    }

    protected void release(ListAppender<ILoggingEvent> logs) {
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }
}
