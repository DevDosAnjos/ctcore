package br.com.ctcore.mail;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Retries due PENDING messages (R2), drops old SENT/DISABLED/SKIPPED/BOUNCED rows and refreshes the queue gauge. */
@Component
public class EmailRetryJob {
    static final int BATCH = 50;
    static final Duration KEEP = Duration.ofDays(30);

    private final EmailService service;
    private final JdbcClient jdbc;
    private final PlatformTransactionManager manager;
    private final Clock clock;

    public EmailRetryJob(EmailService service, @Lazy JdbcClient jdbc, @Lazy PlatformTransactionManager manager, Clock clock) {
        this.service = service;
        this.jdbc = jdbc;
        this.manager = manager;
        this.clock = clock;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 30_000)
    @SchedulerLock(name = "email-retry", lockAtMostFor = "PT5M")
    public void run() {
        runOnce();
    }

    /** Rows stay locked (skip locked) until the batch commits, so two runs never send the same message. */
    void runOnce() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            List<UUID> due = jdbc.sql("select id from email_message where status = 'PENDING' and next_attempt_at <= :now "
                    + "order by next_attempt_at limit " + BATCH + " for update skip locked").param("now", now).query(UUID.class).list();
            due.forEach(service::attempt);
        });
        jdbc.sql("delete from email_message where status in ('SENT', 'DISABLED', 'SKIPPED', 'BOUNCED') and created_at < :old")
                .param("old", now.minus(KEEP)).update();
        service.refreshQueue();
    }
}
