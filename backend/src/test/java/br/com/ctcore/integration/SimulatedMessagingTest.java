package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import br.com.ctcore.integration.simulated.SimulatedEmailSender;
import br.com.ctcore.integration.simulated.SimulatedPushSender;
import br.com.ctcore.integration.simulated.SimulatedSmsSender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SimulatedMessagingTest {

    private static final Instant NOW = Instant.parse("2026-03-04T05:06:07Z");
    private final Outbox outbox = new Outbox(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void emailSmsAndPushLandInTheOutbox() {
        assertThat(new SimulatedEmailSender(outbox).send("a@b.c", "Oi", "corpo")).isEqualTo(SendResult.SENT);
        assertThat(new SimulatedSmsSender(outbox).send("+5511999990000", "Codigo 123456")).isEqualTo(SendResult.SENT);
        assertThat(new SimulatedPushSender(outbox).send("tok", "Titulo", "texto")).isEqualTo(SendResult.SENT);

        var l = outbox.list();
        assertThat(l).extracting(Outbox.Message::type)
                .containsExactly(Outbox.Type.EMAIL, Outbox.Type.SMS, Outbox.Type.PUSH);
        assertThat(l).extracting(Outbox.Message::at).containsOnly(NOW);
        assertThat(l.get(0)).satisfies(m -> {
            assertThat(m.recipient()).isEqualTo("a@b.c");
            assertThat(m.subject()).isEqualTo("Oi");
            assertThat(m.content()).isEqualTo("corpo");
        });
        assertThat(l.get(1).content()).contains("123456");
        assertThat(l.get(2).subject()).isEqualTo("Titulo");
    }

    @Test
    void infoLogHasTypeAndIdButNotTheText() {
        Logger log = (Logger) LoggerFactory.getLogger(SimulatedSmsSender.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.addAppender(appender);
        Level old = log.getLevel();
        log.setLevel(Level.INFO);
        try {
            new SimulatedSmsSender(outbox).send("+5511999990000", "cartao 4111 1111 1111 1111");
            assertThat(appender.list).hasSize(1);
            String msg = appender.list.getFirst().getFormattedMessage();
            assertThat(msg).contains("type=SMS", "id=").doesNotContain("4111").doesNotContain("cartao");
        } finally {
            log.detachAppender(appender);
            log.setLevel(old);
        }
    }
}
