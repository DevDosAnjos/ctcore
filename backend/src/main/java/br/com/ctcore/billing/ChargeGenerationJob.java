package br.com.ctcore.billing;

import br.com.ctcore.isolation.OrganizationTransactions;
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
 * RN-COB-02: tarefa diária (05:00 UTC) que cria as mensalidades que vencem nos próximos 10 dias. Uma transação por unidade (tudo ou nada, RNF-13);
 * a unidade com falha vai para o log e não impede as demais.
 */
@Component
public class ChargeGenerationJob {
    private static final Logger LOG = LoggerFactory.getLogger(ChargeGenerationJob.class);

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final MonthlyGeneration generation;

    public ChargeGenerationJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, MonthlyGeneration generation) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.generation = generation;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "UTC")
    @SchedulerLock(name = "billing-monthly-generation", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    public MonthlyGeneration.Run runOnce() {
        MonthlyGeneration.Run total = new MonthlyGeneration.Run(0, 0, 0, 0);
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            List<UUID> units = transactions.provision(org, () -> jdbc.sql("select id from unit where organization_id = :org and active order by id")
                    .param("org", org).query(UUID.class).list());
            for (UUID unit : units) {
                try {
                    total = total.plus(transactions.provision(org, () -> generation.runUnit(org, unit)));
                } catch (RuntimeException e) {
                    LOG.error("charge generation failed org={} unit={}", org, unit, e);
                    total = total.plus(new MonthlyGeneration.Run(0, 0, 0, 1));
                }
            }
        }
        return total;
    }
}
