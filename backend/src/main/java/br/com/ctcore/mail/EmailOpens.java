package br.com.ctcore.mail;

import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-COB-17 (D4): o provedor real chama {@link #register} quando o e-mail é aberto (Sprint 22); até lá nada o chama. */
@Service
public class EmailOpens {
    private final JdbcClient jdbc;
    private final Clock clock;

    public EmailOpens(@Lazy JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Idempotente: só a primeira abertura grava. */
    public void register(UUID messageId) {
        jdbc.sql("update email_message set opened_at = :now where id = :id and opened_at is null").param("now", java.time.OffsetDateTime.now(clock)).param("id", messageId).update();
    }
}
