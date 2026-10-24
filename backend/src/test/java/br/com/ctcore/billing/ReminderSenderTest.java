package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.mail.EmailTemplate;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-COB-17 (#79): modelo pela data e texto da cobrança, sem banco. */
class ReminderSenderTest {
    @Test
    @DisplayName("O modelo segue a data: antes, no dia ou depois do vencimento")
    void template() {
        LocalDate due = LocalDate.parse("2026-10-05");
        assertThat(ReminderSender.template(due.minusDays(1), due)).isEqualTo(EmailTemplate.REMINDER_BEFORE);
        assertThat(ReminderSender.template(due, due)).isEqualTo(EmailTemplate.REMINDER_DUE);
        assertThat(ReminderSender.template(due.plusDays(1), due)).isEqualTo(EmailTemplate.REMINDER_OVERDUE);
    }

    @Test
    @DisplayName("A descrição vem do texto, do mês de referência ou do padrão")
    void describe() {
        assertThat(ReminderSender.describe("Kimono", java.sql.Date.valueOf("2026-10-01"))).isEqualTo("Kimono");
        assertThat(ReminderSender.describe(null, java.sql.Date.valueOf("2026-10-01"))).isEqualTo("Mensalidade 10/2026");
        assertThat(ReminderSender.describe(null, null)).isEqualTo("Cobrança");
    }
}
