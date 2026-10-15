package br.com.ctcore.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationalMetricsTest {

    @Test
    @DisplayName("CA10: valem 0 ao iniciar; webhookFailed() soma 1 e emailQueue(3) fixa 3")
    void metrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics m = new OperationalMetrics(registry);
        assertThat(registry.get("ctcore.email.queue.pending").gauge().value()).isZero();
        assertThat(registry.get("ctcore.webhook.failures").counter().count()).isZero();
        m.webhookFailed();
        m.emailQueue(3);
        assertThat(registry.get("ctcore.email.queue.pending").gauge().value()).isEqualTo(3);
        assertThat(registry.get("ctcore.webhook.failures").counter().count()).isEqualTo(1);
    }
}
