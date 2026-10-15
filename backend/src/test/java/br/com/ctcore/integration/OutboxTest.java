package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OutboxTest {

    private final Outbox outbox = new Outbox(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void the1001stDropsTheOldest() {
        for (int i = 0; i < Outbox.LIMIT; i++) outbox.add(Outbox.Type.EMAIL, "r" + i, "s", "c");
        assertThat(outbox.list()).hasSize(999);
        outbox.add(Outbox.Type.EMAIL, "r1000", "s", "c");
        assertThat(outbox.list()).hasSize(1000);
        assertThat(outbox.list().getFirst().recipient()).isEqualTo("r1");
        assertThat(outbox.list().getLast().recipient()).isEqualTo("r1000");
    }

    @Test
    void concurrentSendsKeepTheLimitWithoutDuplicates() throws Exception {
        int threads = 8, each = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            int tt = t;
            pool.submit(() -> {
                go.await();
                for (int i = 0; i < each; i++) outbox.add(Outbox.Type.SMS, tt + "-" + i, null, "c");
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(outbox.list()).hasSize(1000);
        assertThat(outbox.list().stream().map(Outbox.Message::id).collect(Collectors.toSet())).hasSize(1000);
    }
}
