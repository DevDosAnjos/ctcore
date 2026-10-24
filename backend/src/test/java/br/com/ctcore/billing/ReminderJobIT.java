package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.mail.EmailTemplate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** RN-COB-17 a RN-COB-19 (#79): lembretes automáticos, pelo relógio fixo de 2026-10-05 (9h na unidade, que é a hora padrão). */
class ReminderJobIT extends ReminderBase {
    @Test
    @DisplayName("CA1, CA24: vence em 3 dias: REMINDER_BEFORE ao aluno e uma linha SCHEDULED; rodar de novo não repete; log sem endereço")
    void before() {
        var t = tenant();
        UUID c = charge(t, adult(t, "rem-antes@example.com"), "2026-10-08", "OPEN");
        var logs = captureLogs();
        try {
            assertThat(job().runOnce()).isEqualTo(1);
            assertThat(job().runOnce()).isZero();
        } finally {
            release(logs);
        }
        assertThat(mails("rem-antes%")).singleElement().satisfies(m -> assertThat(m).containsEntry("template", "REMINDER_BEFORE").containsEntry("organization_id", t.orgId()));
        assertThat(history(c)).singleElement().satisfies(h -> assertThat(h).containsEntry("kind", "SCHEDULED").containsEntry("offset_days", -3)
                .containsEntry("channel", "EMAIL").containsEntry("status", "SENT").containsEntry("reason", null).containsEntry("sent_by_account_id", null));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("rem-antes"));
    }

    @Test
    @DisplayName("CA2: no dia, 3 e 10 dias depois, um modelo cada; 2 dias antes (sem dia configurado) não envia")
    void defaultDays() {
        var t = tenant();
        UUID s = adult(t, "rem-dias@example.com");
        UUID due = charge(t, s, "2026-10-05", "OPEN"), three = charge(t, s, "2026-10-02", "OPEN"), ten = charge(t, s, "2026-09-25", "OPEN"), none = charge(t, s, "2026-10-07", "OPEN");
        assertThat(job().runOnce()).isEqualTo(3);
        assertThat(mails("rem-dias%")).extracting(m -> m.get("template")).containsExactlyInAnyOrder("REMINDER_DUE", "REMINDER_OVERDUE", "REMINDER_OVERDUE");
        assertThat(history(due)).hasSize(1);
        assertThat(history(three)).singleElement().satisfies(h -> assertThat(h).containsEntry("offset_days", 3));
        assertThat(history(ten)).singleElement().satisfies(h -> assertThat(h).containsEntry("offset_days", 10));
        assertThat(history(none)).isEmpty();
    }

    @Test
    @DisplayName("CA3: reminder_days {-1, 5} no lugar dos padrões")
    void customDays() {
        var t = tenant();
        UUID s = adult(t, "rem-custom@example.com");
        adminJdbc().update("update unit_settings set reminder_days = '{-1,5}' where unit_id = ?", t.unitId());
        UUID minusOne = charge(t, s, "2026-10-06", "OPEN"), five = charge(t, s, "2026-09-30", "OPEN"), standard = charge(t, s, "2026-10-08", "OPEN");
        job().runOnce();
        assertThat(history(minusOne)).hasSize(1);
        assertThat(history(five)).hasSize(1);
        assertThat(history(standard)).isEmpty();
    }

    @Test
    @DisplayName("CA4: antes da hora da unidade nada sai; na hora sai; com hora 18 só a partir das 21:00 UTC")
    void hour() {
        var t = tenant();
        UUID c = charge(t, adult(t, "rem-hora@example.com"), "2026-10-08", "OPEN");
        assertThat(job("2026-10-05T11:59:00Z").runOnce()).isZero();
        assertThat(history(c)).isEmpty();
        assertThat(job("2026-10-05T12:00:00Z").runOnce()).isEqualTo(1);

        UUID late = charge(t, adult(t, "rem-hora2@example.com"), "2026-10-08", "OPEN");
        adminJdbc().update("delete from charge_reminder where charge_id = ?", c);
        adminJdbc().update("update charge set status = 'CANCELED' where id = ?", c);
        adminJdbc().update("update unit_settings set reminders_hour = 18 where unit_id = ?", t.unitId());
        assertThat(job("2026-10-05T20:59:00Z").runOnce()).isZero();
        assertThat(job("2026-10-05T21:00:00Z").runOnce()).isEqualTo(1);
        assertThat(history(late)).hasSize(1);
    }

    @Test
    @DisplayName("CA5: reminders_enabled falso: nenhum automático sai")
    void disabled() {
        var t = tenant();
        UUID c = charge(t, adult(t, "rem-off@example.com"), "2026-10-08", "OPEN");
        adminJdbc().update("update unit_settings set reminders_enabled = false where unit_id = ?", t.unitId());
        assertThat(job().runOnce()).isZero();
        assertThat(history(c)).isEmpty();
        assertThat(mails("rem-off%")).isEmpty();
    }

    @Test
    @DisplayName("CA6: menor com responsável financeiro: vai ao responsável e não ao aluno")
    void minor() {
        var t = tenant();
        UUID minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pedro Souza", "2018-01-01");
        adminJdbc().update("update student set email = 'rem-menor@example.com' where id = ?", minor);
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        UUID c = charge(t, minor, "2026-10-08", "OPEN");
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, c);
        job().runOnce();
        assertThat(mails("rem-menor%")).isEmpty();
        assertThat(mails("gen-%")).singleElement().satisfies(m -> assertThat(m).containsEntry("template", "REMINDER_BEFORE"));
    }

    @Test
    @DisplayName("CA6: sem endereço registra NO_ADDRESS sem mensagem; com endereço marcado Com problema a mensagem fica SKIPPED e o histórico EMAIL_PROBLEM")
    void noAddressAndProblem() {
        var t = tenant();
        UUID none = charge(t, BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Sem Email"), "2026-10-08", "OPEN");
        UUID s = adult(t, "rem-problema@example.com");
        adminJdbc().update("update student set email_problem_at = now() where id = ?", s);
        UUID problem = charge(t, s, "2026-10-08", "OPEN");
        assertThat(job().runOnce()).isZero();
        assertThat(history(none)).singleElement().satisfies(h -> assertThat(h).containsEntry("status", "SKIPPED").containsEntry("reason", "NO_ADDRESS"));
        assertThat(history(problem)).singleElement().satisfies(h -> assertThat(h).containsEntry("status", "SKIPPED").containsEntry("reason", "EMAIL_PROBLEM"));
        assertThat(mails("rem-problema%")).singleElement().satisfies(m -> assertThat(m).containsEntry("status", "SKIPPED"));
    }

    @Test
    @DisplayName("CA7: paga, cancelada, renegociada, estornada ou em conferência não recebe")
    void notOpen() {
        var t = tenant();
        UUID s = adult(t, "rem-fechada@example.com");
        for (String status : new String[] {"PAID", "CANCELED", "RENEGOTIATED", "REFUNDED", "IN_REVIEW"}) charge(t, s, "2026-10-08", status);
        assertThat(job().runOnce()).isZero();
        assertThat(mails("rem-fechada%")).isEmpty();
        assertThat(adminJdbc().queryForObject("select count(*) from charge_reminder", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Erro em uma cobrança vai ao log só com a classe, sem endereço, e não impede as outras")
    void errorIsolated() {
        var t = tenant();
        UUID bad = charge(t, adult(t, "rem-erro@example.com"), "2026-10-08", "OPEN");
        UUID good = charge(t, adult(t, "rem-boa@example.com"), "2026-10-08", "OPEN");
        var clock = java.time.Clock.fixed(java.time.Instant.parse(NOON), java.time.ZoneOffset.UTC);
        var real = new ReminderSender(jdbc, email, clock, texts);
        var failing = Mockito.spy(real);
        Mockito.doThrow(new IllegalStateException("rem-erro@example.com")).when(failing).send(ArgumentMatchers.any(), ArgumentMatchers.eq(bad),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(EmailTemplate.class), ArgumentMatchers.any(), ArgumentMatchers.any());
        var logs = captureLogs();
        try {
            new ReminderJob(transactions, jdbc, failing, new BlockWarningSms(jdbc, sms, messages, clock), clock).runOnce();
        } finally {
            release(logs);
        }
        assertThat(history(bad)).isEmpty();
        assertThat(history(good)).hasSize(1);
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("IllegalStateException")).noneMatch(e -> e.getFormattedMessage().contains("rem-erro"));
    }
}
