package br.com.ctcore.notification;

import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-NOT-04: convite de acesso sem aceite (nem cancelamento) há 7 dias avisa os Coordenadores, uma vez por convite (a deduplicação é do
 * {@link Notifications#publish}). Cada organização roda no seu escopo; uma com falha não impede as demais.
 */
@Component
public class InvitePendingJob {
    private static final Logger LOG = LoggerFactory.getLogger(InvitePendingJob.class);
    static final int DAYS = 7;

    private record Pending(UUID id, String name) {}

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final Notifications notifications;
    private final Clock clock;

    public InvitePendingJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, Notifications notifications, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "UTC")
    @SchedulerLock(name = "invite-pending-notice", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    /** Devolve quantos convites pendentes foram vistos (os já avisados não geram linha nova). */
    public int runOnce() {
        OffsetDateTime limit = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(DAYS);
        int total = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            try {
                List<Pending> pending = transactions.provision(org, () -> jdbc.sql("select i.id, a.name from account_invite i join account a on a.id = i.account_id "
                        + "where i.organization_id = :org and i.used_at is null and i.canceled_at is null and i.created_at <= :limit order by i.id")
                        .param("org", org).param("limit", limit).query((rs, n) -> new Pending(rs.getObject(1, UUID.class), rs.getString(2))).list());
                for (Pending p : pending) {
                    notifications.publish(org, null, NotificationType.INVITE_PENDING, "invite", p.id(), p.name() == null ? Map.of() : Map.of("personName", p.name()));
                }
                total += pending.size();
            } catch (RuntimeException e) {
                LOG.error("invite pending notice failed org={} cause={}", org, e.getClass().getSimpleName());
            }
        }
        return total;
    }
}
