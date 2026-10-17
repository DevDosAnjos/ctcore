package br.com.ctcore.mail;

import br.com.ctcore.integration.EmailSender;
import br.com.ctcore.integration.SendResult;
import br.com.ctcore.monitoring.OperationalMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RNF-14: every message is stored first, sent at once and, on failure, retried by {@link EmailRetryJob}.
 * Neither the address nor the text goes to logs, metrics or {@code last_error}.
 */
@Service
public class EmailService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JdbcClient jdbc;
    private final EmailSender sender;
    private final MessageSource messages;
    private final Clock clock;
    private final OperationalMetrics metrics;
    private final int maxAttempts;
    private final List<Duration> backoff;

    public EmailService(@Lazy JdbcClient jdbc, EmailSender sender, MessageSource messages, Clock clock, OperationalMetrics metrics,
            @Value("${ctcore.email.max-attempts}") int maxAttempts, @Value("${ctcore.email.backoff}") List<Duration> backoff) {
        this.jdbc = jdbc;
        this.sender = sender;
        this.messages = messages;
        this.clock = clock;
        this.metrics = metrics;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Stores and sends; never throws because of the sender, so the caller's operation is not undone. */
    public UUID send(String to, EmailTemplate template, Object... args) {
        String subject = messages.getMessage(template.key + ".subject", null, PT_BR);
        String body = messages.getMessage(template.key + ".body", args, PT_BR);
        boolean on = sender.isEnabled();
        OffsetDateTime now = now();
        UUID id = jdbc.sql("insert into email_message (to_email, template, subject, body, status, next_attempt_at, created_at) "
                + "values (:to, :t, :s, :b, :st, :now, :now) returning id")
                .param("to", to).param("t", template.name()).param("s", subject).param("b", on ? body : null)
                .param("st", on ? "PENDING" : "DISABLED").param("now", now).query(UUID.class).single();
        if (on) {
            attempt(id);
        }
        return id;
    }

    /** One delivery try of a PENDING message; updates the row and counts the failure. Does nothing for any other status. */
    public void attempt(UUID id) {
        var rows = jdbc.sql("select to_email, template, subject, body, attempts from email_message where id = :id and status = 'PENDING'")
                .param("id", id).query().listOfRows();
        if (rows.isEmpty()) {
            return;
        }
        var r = rows.get(0);
        EmailTemplate template = EmailTemplate.valueOf((String) r.get("template"));
        int attempts = (Integer) r.get("attempts") + 1;
        String error = null;
        try {
            if (sender.send((String) r.get("to_email"), (String) r.get("subject"), (String) r.get("body")) != SendResult.SENT) {
                error = "NOT_SENT";
            }
        } catch (RuntimeException e) {
            error = e.getClass().getSimpleName();
        }
        OffsetDateTime now = now();
        if (error == null) {
            jdbc.sql("update email_message set status = 'SENT', attempts = :n, body = null, last_error = null, next_attempt_at = null, sent_at = :now where id = :id")
                    .param("n", attempts).param("now", now).param("id", id).update();
        } else {
            boolean last = attempts >= maxAttempts;
            metrics.emailFailure();
            LOG.warn("email failed id={} template={} attempt={}", id, template, attempts);
            jdbc.sql("update email_message set status = :st, attempts = :n, last_error = :e, next_attempt_at = :next, "
                    + "body = case when :clear then null else body end where id = :id")
                    .param("st", last ? "FAILED" : "PENDING").param("n", attempts).param("e", error)
                    .param("next", last ? null : now.plus(backoff.get(Math.min(attempts, backoff.size()) - 1)))
                    .param("clear", last && template.secret).param("id", id).update();
        }
        refreshQueue();
    }

    /** Manual resend (R4) of a final failure that still holds its text; templates with a code were cleared (D3). */
    public void requeue(UUID id) {
        int rows = jdbc.sql("update email_message set status = 'PENDING', attempts = 0, next_attempt_at = :now "
                + "where id = :id and status = 'FAILED' and body is not null")
                .param("now", now()).param("id", id).update();
        if (rows == 0) {
            throw new IllegalStateException("Message is not a final failure that still holds its text");
        }
        attempt(id);
    }

    void refreshQueue() {
        metrics.emailQueue(jdbc.sql("select count(*) from email_message where status = 'PENDING'").query(Long.class).single());
    }
}
