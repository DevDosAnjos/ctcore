package br.com.ctcore.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;

/** Sem banco: com a propriedade desligada não há agendamento nem LockProvider. */
@SpringBootTest(properties = {
        "ctcore.scheduler.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
@Import(SchedulerDisabledTest.Tasks.class)
class SchedulerDisabledTest {

    static final AtomicInteger ticks = new AtomicInteger();

    @TestConfiguration
    static class Tasks {
        @Bean
        TickTask tickTask() {
            return new TickTask();
        }
    }

    static class TickTask {
        @Scheduled(cron = "* * * * * *")
        public void run() {
            ticks.incrementAndGet();
        }
    }

    @Autowired ApplicationContext ctx;

    @Test
    @DisplayName("CA1: com ctcore.scheduler.enabled=false a tarefa não executa e não há LockProvider")
    void disabled() throws Exception {
        Thread.sleep(2500);
        assertThat(ticks.get()).isZero();
        assertThat(ctx.getBeanNamesForType(LockProvider.class)).isEmpty();
    }
}
