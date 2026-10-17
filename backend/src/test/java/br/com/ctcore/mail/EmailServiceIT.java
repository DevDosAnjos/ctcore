package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.integration.EmailSender;
import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.SendResult;
import br.com.ctcore.integration.simulated.SimulatedEmailSender;
import br.com.ctcore.monitoring.OperationalMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;

class EmailServiceIT extends PostgresIT {
    static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

    static class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-17T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Fails by exception, by NOT_SENT or succeeds into the simulated outbox; counts the calls. */
    static class Sender implements EmailSender {
        final Outbox outbox;
        final SimulatedEmailSender real;
        volatile String mode = "ok";
        volatile boolean enabled = true;
        volatile long delayMs;
        final AtomicInteger calls = new AtomicInteger();

        Sender(Outbox outbox) {
            this.outbox = outbox;
            this.real = new SimulatedEmailSender(outbox);
        }

        @Override public boolean isEnabled() { return enabled; }

        @Override
        public SendResult send(String to, String subject, String body) {
            calls.incrementAndGet();
            if (delayMs > 0) {
                try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return switch (mode) {
                case "throw" -> throw new IllegalStateException("smtp down for ana@x.com");
                case "notsent" -> SendResult.NOT_SENT;
                default -> real.send(to, subject, body);
            };
        }
    }

    @Autowired JdbcClient jdbc;
    @Autowired MessageSource messages;
    @Autowired PlatformTransactionManager manager;
    JdbcTemplate admin = adminJdbc();
    TestClock clock = new TestClock();
    Outbox outbox = new Outbox(clock);
    Sender sender = new Sender(outbox);
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    OperationalMetrics metrics;
    EmailService service;
    EmailRetryJob job;
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setup() {
        admin.update("delete from email_message");
        metrics = new OperationalMetrics(registry);
        service = new EmailService(jdbc, sender, messages, clock, metrics, 5, BACKOFF);
        job = new EmailRetryJob(service, jdbc, manager, clock);
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void clean() {
        root.detachAppender(logs);
        admin.update("delete from email_message");
    }

    Map<String, Object> row(UUID id) {
        return admin.queryForMap("select * from email_message where id = ?", id);
    }

    String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("CA10: os 4 modelos saem em uma coluna de 600 px, 18 px, botão largo só com link e argumentos escapados")
    void layout() {
        String evil = "https://x/convite/a<b>&\"c";
        service.send("ana@x.com", EmailTemplate.INVITE, evil);
        service.send("ana@x.com", EmailTemplate.PASSWORD_RESET, evil);
        service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        service.send("ana@x.com", EmailTemplate.NEW_ACCESS, "<b>Dev</b>", "A&B", "hoje");
        var mails = outbox.list();
        assertThat(mails).hasSize(4);
        for (var m : mails) {
            assertThat(m.content()).contains("max-width:600px", "font-size:18px").doesNotContain("<b>");
        }
        for (var m : mails.subList(0, 2)) {
            assertThat(m.content()).contains("width:100%", "https://x/convite/a&lt;b&gt;&amp;&quot;c\n</p>", "href=\"https://x/convite/a&lt;b&gt;&amp;&quot;c\"");
        }
        assertThat(mails.get(2).content()).doesNotContain("width:100%");
        assertThat(mails.get(3).content()).doesNotContain("width:100%").contains("&lt;b&gt;Dev&lt;/b&gt;", "A&amp;B");
    }

    @Test
    @DisplayName("CA1: sender que lança exceção não derruba send; linha PENDING com attempts=1 e last_error sem endereço")
    void failureKeepsPending() {
        sender.mode = "throw";
        UUID id = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        var r = row(id);
        assertThat(r.get("status")).isEqualTo("PENDING");
        assertThat(r.get("attempts")).isEqualTo(1);
        assertThat((String) r.get("last_error")).isEqualTo("IllegalStateException").doesNotContain("ana@x.com");
        assertThat(r.get("body")).isNotNull();
    }

    @Test
    @DisplayName("CA2: sucesso grava SENT com sent_at, apaga o texto e a Caixa de saída recebe a mensagem")
    void success() {
        UUID id = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        var r = row(id);
        assertThat(r.get("status")).isEqualTo("SENT");
        assertThat(r.get("sent_at")).isNotNull();
        assertThat(r.get("body")).isNull();
        assertThat(outbox.list()).hasSize(1);
        assertThat(outbox.list().get(0).recipient()).isEqualTo("ana@x.com");
    }

    @Test
    @DisplayName("CA3: reenvio só depois de next_attempt_at; a 5ª falha deixa FAILED; sucesso no meio deixa SENT")
    void retries() {
        sender.mode = "notsent";
        UUID id = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        clock.now = clock.now.plusSeconds(30);
        job.runOnce();
        assertThat(row(id).get("attempts")).isEqualTo(1);
        for (Duration wait : BACKOFF) {
            clock.now = clock.now.plus(wait);
            job.runOnce();
        }
        var r = row(id);
        assertThat(r.get("status")).isEqualTo("FAILED");
        assertThat(r.get("attempts")).isEqualTo(5);
        assertThat(r.get("next_attempt_at")).isNull();
        job.runOnce();
        assertThat(sender.calls.get()).isEqualTo(5);

        UUID ok = service.send("bia@x.com", EmailTemplate.PASSWORD_CHANGED);
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        sender.mode = "ok";
        job.runOnce();
        assertThat(row(ok).get("status")).isEqualTo("SENT");
        assertThat(row(ok).get("attempts")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA4: duas execuções simultâneas da tarefa não enviam a mesma mensagem duas vezes")
    void concurrentRuns() throws Exception {
        sender.mode = "notsent";
        UUID id = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        sender.mode = "ok";
        sender.delayMs = 300;
        sender.calls.set(0);
        clock.now = clock.now.plus(Duration.ofMinutes(2));
        CountDownLatch go = new CountDownLatch(1);
        Runnable run = () -> {
            try { go.await(); } catch (InterruptedException e) { return; }
            job.runOnce();
        };
        Thread a = new Thread(run);
        Thread b = new Thread(run);
        a.start();
        b.start();
        go.countDown();
        a.join();
        b.join();
        assertThat(sender.calls.get()).isEqualTo(1);
        assertThat(row(id).get("status")).isEqualTo("SENT");
    }

    @Test
    @DisplayName("CA5: falha soma ctcore.email.failures, escreve WARN sem endereço nem texto e o medidor bate com os PENDING")
    void alertsAndGauge() {
        sender.mode = "throw";
        service.send("ana@x.com", EmailTemplate.INVITE, "https://x/convite/SEGREDO123");
        service.send("bia@x.com", EmailTemplate.PASSWORD_CHANGED);
        assertThat(registry.get("ctcore.email.failures").counter().count()).isEqualTo(2);
        assertThat(registry.get("ctcore.email.queue.pending").gauge().value()).isEqualTo(2);
        var warns = logs.list.stream().filter(e -> e.getLevel().toString().equals("WARN")).toList();
        assertThat(warns).hasSize(2);
        assertThat(warns.get(0).getFormattedMessage()).contains("template=INVITE", "attempt=1");
        assertThat(allLogs()).doesNotContain("ana@x.com", "bia@x.com", "SEGREDO123");
        sender.mode = "ok";
        clock.now = clock.now.plus(Duration.ofMinutes(1));
        job.runOnce();
        assertThat(registry.get("ctcore.email.queue.pending").gauge().value()).isZero();
    }

    @Test
    @DisplayName("CA6: requeue de FAILED sem segredo volta a PENDING e é enviada; com segredo (texto apagado) é recusada")
    void requeue() {
        sender.mode = "notsent";
        UUID plain = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        UUID secret = service.send("bia@x.com", EmailTemplate.INVITE, "https://x/convite/COD");
        for (Duration wait : BACKOFF) {
            clock.now = clock.now.plus(wait);
            job.runOnce();
        }
        assertThat(row(plain).get("status")).isEqualTo("FAILED");
        assertThat(row(secret).get("status")).isEqualTo("FAILED");
        assertThat(row(secret).get("body")).isNull();
        assertThat(row(plain).get("body")).isNotNull();

        assertThatThrownBy(() -> service.requeue(secret)).isInstanceOf(IllegalStateException.class);
        sender.mode = "ok";
        service.requeue(plain);
        assertThat(row(plain).get("status")).isEqualTo("SENT");
    }

    @Test
    @DisplayName("CA7: cada modelo gera assunto e texto em português; convite e nova senha trazem o link com o código")
    void templates() {
        service.send("a@x.com", EmailTemplate.INVITE, "https://x/convite/C1");
        service.send("a@x.com", EmailTemplate.PASSWORD_RESET, "https://x/redefinir-senha/C2");
        service.send("a@x.com", EmailTemplate.PASSWORD_CHANGED);
        service.send("a@x.com", EmailTemplate.NEW_ACCESS, "Chrome em Windows", "local não identificado", "17/10/2026 09:00");
        var sent = outbox.list();
        assertThat(sent).hasSize(4);
        assertThat(sent.get(0).subject()).isEqualTo("Você recebeu um convite para o CTCore");
        assertThat(sent.get(0).content()).contains("https://x/convite/C1");
        assertThat(sent.get(1).content()).contains("https://x/redefinir-senha/C2");
        assertThat(sent.get(2).subject()).isEqualTo("Sua senha foi alterada");
        assertThat(sent.get(3).subject()).isEqualTo("Novo acesso à sua conta");
        assertThat(sent.get(3).content()).contains("Chrome em Windows", "local não identificado", "17/10/2026 09:00");
    }

    @Test
    @DisplayName("CA9: depois do envio o banco não guarda o código; logs sem e-mail nem código")
    void noSecretLeft() {
        UUID id = service.send("ana@x.com", EmailTemplate.INVITE, "https://x/convite/SEGREDO456");
        assertThat(admin.queryForObject("select count(*) from email_message where body like '%SEGREDO456%'", Long.class)).isZero();
        assertThat(row(id).get("body")).isNull();
        assertThat(allLogs()).doesNotContain("ana@x.com", "SEGREDO456");
    }

    @Test
    @DisplayName("CA10: com a conexão desligada a mensagem fica DISABLED, sem chamar o sender, sem falha e sem reenvio")
    void disabled() {
        sender.enabled = false;
        UUID id = service.send("ana@x.com", EmailTemplate.PASSWORD_CHANGED);
        clock.now = clock.now.plus(Duration.ofHours(1));
        job.runOnce();
        var r = row(id);
        assertThat(r.get("status")).isEqualTo("DISABLED");
        assertThat(r.get("attempts")).isEqualTo(0);
        assertThat(r.get("body")).isNull();
        assertThat(sender.calls.get()).isZero();
        assertThat(registry.get("ctcore.email.failures").counter().count()).isZero();
    }
}
