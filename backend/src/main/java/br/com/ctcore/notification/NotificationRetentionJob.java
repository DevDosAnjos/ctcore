package br.com.ctcore.notification;

import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-NOT-05: apaga as notificações com 30 dias ou mais, lidas ou não. Cada organização roda no seu escopo (função de banco restrita a ela);
 * uma com falha não impede as demais. O log leva só a quantidade.
 */
@Component
public class NotificationRetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionJob.class);
    public static final int RETENTION_DAYS = 30;

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final Clock clock;

    public NotificationRetentionJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "UTC")
    @SchedulerLock(name = "notification-retention", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    /** Devolve quantas notificações foram apagadas. */
    public int runOnce() {
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        int total = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            try {
                total += transactions.provision(org, () -> jdbc.sql("select purge_expired_notifications(:org, :cutoff)")
                        .param("org", org).param("cutoff", cutoff).query(Integer.class).single());
            } catch (RuntimeException e) {
                LOG.error("notification retention failed org={} cause={}", org, e.getClass().getSimpleName());
            }
        }
        LOG.info("notification retention deleted={}", total);
        return total;
    }
}
