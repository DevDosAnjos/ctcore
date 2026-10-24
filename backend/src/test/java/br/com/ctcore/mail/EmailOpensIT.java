package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.db.PostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-COB-17 (D4): o gancho de abertura de e-mail preenche opened_at uma vez, só na mensagem pedida. */
class EmailOpensIT extends PostgresIT {
    @Autowired EmailOpens opens;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from email_message where to_email like 'opens-%'");
    }

    private UUID message(String to) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into email_message (id, to_email, template, subject, status, created_at) values (?, ?, 'REMINDER_DUE', 's', 'SENT', now())", id, to);
        return id;
    }

    private Object openedAt(UUID id) {
        return adminJdbc().queryForObject("select opened_at from email_message where id = ?", Object.class, id);
    }

    @Test
    @DisplayName("CA11: registrar preenche opened_at uma vez, repetir não muda e outra mensagem não é afetada")
    void register() {
        UUID a = message("opens-a@example.com"), b = message("opens-b@example.com");
        opens.register(a);
        Object first = openedAt(a);
        assertThat(first).isNotNull();
        opens.register(a);
        assertThat(openedAt(a)).isEqualTo(first);
        assertThat(openedAt(b)).isNull();
    }
}
