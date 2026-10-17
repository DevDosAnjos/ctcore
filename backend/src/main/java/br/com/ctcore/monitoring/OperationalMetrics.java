package br.com.ctcore.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** D7: métricas de negócio lidas pelo monitoramento (#193). Valem 0 até a fila de e-mail (#19) e o webhook (#96) chamarem. */
@Component
public class OperationalMetrics {

    private final AtomicLong emailQueue = new AtomicLong();
    private final Counter webhookFailures;
    private final Counter emailFailures;

    public OperationalMetrics(MeterRegistry registry) {
        registry.gauge("ctcore.email.queue.pending", emailQueue);
        webhookFailures = registry.counter("ctcore.webhook.failures");
        emailFailures = registry.counter("ctcore.email.failures");
    }

    /** Informa quantos e-mails aguardam envio. */
    public void emailQueue(long pending) {
        emailQueue.set(pending);
    }

    /** Conta uma tentativa de envio de e-mail que falhou. */
    public void emailFailure() {
        emailFailures.increment();
    }

    /** Conta uma falha de entrega de webhook. */
    public void webhookFailed() {
        webhookFailures.increment();
    }
}
