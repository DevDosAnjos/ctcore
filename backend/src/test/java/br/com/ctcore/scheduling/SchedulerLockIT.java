package br.com.ctcore.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import br.com.ctcore.db.PostgresIT;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Import(SchedulerLockIT.Tasks.class)
class SchedulerLockIT extends PostgresIT {

    static final AtomicInteger ticks = new AtomicInteger();
    static final AtomicInteger failing = new AtomicInteger();

    @TestConfiguration
    static class Tasks {
        @Bean
        TickTask tickTask() {
            return new TickTask();
        }

        @Bean
        FailingTask failingTask() {
            return new FailingTask();
        }
    }

    static class TickTask {
        @Scheduled(cron = "* * * * * *", zone = "UTC")
        @SchedulerLock(name = "it-tick", lockAtMostFor = "PT10M")
        public void run() {
            ticks.incrementAndGet();
        }
    }

    static class FailingTask {
        @Scheduled(cron = "* * * * * *", zone = "UTC")
        @SchedulerLock(name = "it-failing", lockAtMostFor = "PT10M")
        public void run() {
            if (failing.incrementAndGet() == 1) throw new IllegalStateException("boom");
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired LockProvider lockProvider;

    private LockProvider secondInstance() {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource)).usingDbTime().build());
    }

    private LockConfiguration config(String name) {
        return new LockConfiguration(Instant.now(), name, Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    @DisplayName("CA1: com o agendador ligado, a tarefa @Scheduled executa")
    void scheduledTaskRuns() {
        await().atMost(Duration.ofSeconds(10)).until(() -> ticks.get() >= 1);
    }

    @Test
    @DisplayName("CA2: duas instâncias contra o mesmo banco, só uma obtém a trava")
    void onlyOneInstanceGetsLock() {
        LockProvider other = secondInstance();
        SimpleLock first = lockProvider.lock(config("it-two")).orElseThrow();
        assertThat(other.lock(config("it-two"))).isEmpty();
        first.unlock();
        assertThat(other.lock(config("it-two"))).isPresent();
    }

    @Test
    @DisplayName("CA3: trava deixada por servidor caído expira e a tarefa volta a executar")
    void staleLockExpires() {
        lockProvider.lock(config("it-stale")).orElseThrow(); // "servidor caído": nunca solta
        assertThat(secondInstance().lock(config("it-stale"))).isEmpty();

        jdbc.update("update shedlock set lock_until = now() - interval '1 minute' where name = 'it-stale'");

        assertThat(secondInstance().lock(config("it-stale"))).isPresent();
    }

    @Test
    @DisplayName("CA4: falha na tarefa não derruba o agendador nem prende a trava")
    void failureReleasesLock() {
        await().atMost(Duration.ofSeconds(10)).until(() -> failing.get() >= 2);
    }
}
