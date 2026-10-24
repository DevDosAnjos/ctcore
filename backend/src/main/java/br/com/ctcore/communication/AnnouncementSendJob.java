package br.com.ctcore.communication;

import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * R7, D9: envia os comunicados agendados que venceram, uma vez cada. Uma transação por comunicado: o erro de um vai ao log, sem dado pessoal, e não
 * impede os outros. Sem auditoria: é efeito de sistema (D10).
 */
@Component
public class AnnouncementSendJob {
    private static final Logger LOG = LoggerFactory.getLogger(AnnouncementSendJob.class);

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final AnnouncementSender sender;
    private final Clock clock;

    public AnnouncementSendJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, AnnouncementSender sender, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.sender = sender;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    @SchedulerLock(name = "communication-announcement-send", lockAtMostFor = "PT10M")
    public void run() {
        runOnce();
    }

    /** Número de comunicados enviados nesta execução. */
    public int runOnce() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int done = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            List<UUID> due = transactions.provision(org, () -> jdbc.sql("select id from announcement where organization_id = :org and sent_at is null and scheduled_for <= :now "
                    + "order by scheduled_for, id").param("org", org).param("now", now).query(UUID.class).list());
            for (UUID id : due) {
                try {
                    if (transactions.provision(org, () -> sender.dispatch(org, id, now)) >= 0) done++;
                } catch (RuntimeException e) {
                    LOG.error("announcement send failed org={} announcement={}", org, id, e);
                }
            }
        }
        return done;
    }
}
