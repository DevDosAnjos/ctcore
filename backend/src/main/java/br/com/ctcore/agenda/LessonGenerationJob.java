package br.com.ctcore.agenda;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** RN-AGE-01: tarefa diária que mantém as 8 semanas de aulas; cada organização roda no seu próprio escopo. */
@Component
public class LessonGenerationJob {
    private static final Logger LOG = LoggerFactory.getLogger(LessonGenerationJob.class);
    static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);

    private final LessonGenerator generator;
    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final Clock clock;

    public LessonGenerationJob(LessonGenerator generator, @Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, Clock clock) {
        this.generator = generator;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @SchedulerLock(name = "lesson-generation", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    /** Devolve o total criado; uma organização com falha não impede as demais. */
    public int runOnce() {
        List<UUID> orgs = jdbc.sql("select active_organization_ids()").query(UUID.class).list();
        int total = 0;
        for (UUID org : orgs) {
            try {
                total += transactions.provision(org, () -> generator.forOrganization(org, clock.instant(), SYSTEM, "DAILY_JOB"));
            } catch (RuntimeException e) {
                LOG.error("lesson generation failed org={}", org, e);
            }
        }
        return total;
    }
}
