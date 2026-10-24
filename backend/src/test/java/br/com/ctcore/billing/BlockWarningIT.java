package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.SendResult;
import br.com.ctcore.integration.SmsSender;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-COB-17 e RN-COB-20 (#79): aviso de bloqueio 5 dias antes (prazo de 30 dias), por e-mail e SMS, com o limite de 3 SMS por mês. Hoje: 2026-10-05. */
class BlockWarningIT extends ReminderBase {
    @Autowired Outbox outbox;

    @BeforeEach
    void clear() {
        outbox.clear();
    }

    /** Mensalidade vencida em 2026-09-10 (bloqueio em 2026-10-10) de unidade com conta conectada. */
    private UUID monthly(Tenant t, UUID student) {
        adminJdbc().update("update unit_settings set payment_account_reference = 'conta-sintetica' where unit_id = ?", t.unitId());
        UUID c = charge(t, student, "2026-09-10", "OPEN");
        adminJdbc().update("update charge set type = 'MONTHLY' where id = ?", c);
        return c;
    }

    private long sms() {
        return outbox.list().stream().filter(m -> m.type() == Outbox.Type.SMS).count();
    }

    private void addSms(Tenant t, UUID student, String at) {
        adminJdbc().update("insert into sms_log (id, organization_id, student_id, kind, created_at) values (?, ?, ?, 'BLOCK_WARNING', ?::timestamptz)", UUID.randomUUID(), t.orgId(), student, at);
    }

    @Test
    @DisplayName("CA9, CA10, CA24: BLOCK_WARNING por e-mail e SMS ao pagador; não repete; sai com os lembretes desligados; log sem endereço, telefone nem texto")
    void warns() {
        var t = tenant();
        UUID s = adult(t, "rem-bloqueio@example.com");
        UUID c = monthly(t, s);
        adminJdbc().update("update unit_settings set reminders_enabled = false where unit_id = ?", t.unitId());
        var logs = captureLogs();
        try {
            assertThat(job().runOnce()).isEqualTo(1);
            assertThat(job().runOnce()).isZero();
        } finally {
            release(logs);
        }
        assertThat(mails("rem-bloqueio%")).singleElement().satisfies(m -> assertThat(m).containsEntry("template", "BLOCK_WARNING"));
        assertThat(history(c)).hasSize(2).anySatisfy(h -> assertThat(h).containsEntry("kind", "BLOCK_WARNING").containsEntry("channel", "EMAIL").containsEntry("status", "SENT"))
                .anySatisfy(h -> assertThat(h).containsEntry("kind", "BLOCK_WARNING").containsEntry("channel", "SMS").containsEntry("status", "SENT"));
        assertThat(outbox.list()).filteredOn(m -> m.type() == Outbox.Type.SMS).singleElement().satisfies(m -> {
            assertThat(m.recipient()).isEqualTo("11912345678");
            assertThat(m.content()).contains("5 dias").contains("10/09/2026");
        });
        assertThat(adminJdbc().queryForObject("select count(*) from sms_log where student_id = ?", Integer.class, s)).isEqualTo(1);
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("rem-bloqueio") || e.getFormattedMessage().contains("11912345678")
                || e.getFormattedMessage().contains("bloqueado"));
    }

    @Test
    @DisplayName("CA9: avulsa, taxa de matrícula e unidade sem conta conectada não recebem")
    void notCovered() {
        var t = tenant();
        UUID s = adult(t, "rem-fora@example.com");
        UUID oneOff = charge(t, s, "2026-09-10", "OPEN"), fee = charge(t, s, "2026-09-10", "OPEN");
        adminJdbc().update("update charge set type = 'ENROLLMENT_FEE' where id = ?", fee);
        adminJdbc().update("update unit_settings set payment_account_reference = 'conta-sintetica' where unit_id = ?", t.unitId());
        assertThat(job().runOnce()).isZero();
        assertThat(history(oneOff)).isEmpty();
        assertThat(history(fee)).isEmpty();

        UUID disconnected = charge(t, s, "2026-09-10", "OPEN");
        adminJdbc().update("update charge set type = 'MONTHLY' where id = ?", disconnected);
        adminJdbc().update("update unit_settings set payment_account_reference = null where unit_id = ?", t.unitId());
        assertThat(job().runOnce()).isZero();
        assertThat(history(disconnected)).isEmpty();
        assertThat(sms()).isZero();
    }

    @Test
    @DisplayName("CA10: e-mail de lembrete anterior aberto: sem SMS; sem telefone: NO_PHONE; menor: o SMS vai ao telefone do responsável")
    void smsRules() {
        var t = tenant();
        UUID opened = monthly(t, adult(t, "rem-aberto@example.com"));
        UUID message = UUID.randomUUID();
        adminJdbc().update("insert into email_message (id, to_email, template, subject, status, created_at, opened_at) values (?, 'rem-aberto@example.com', 'REMINDER_OVERDUE', 's', 'SENT', now(), now())", message);
        adminJdbc().update("insert into charge_reminder (id, organization_id, charge_id, kind, offset_days, channel, status, email_message_id, created_at) "
                + "values (?, ?, ?, 'SCHEDULED', 3, 'EMAIL', 'SENT', ?, now())", UUID.randomUUID(), t.orgId(), opened, message);

        UUID s = adult(t, "rem-semfone@example.com");
        adminJdbc().update("update student set phone = null where id = ?", s);
        UUID noPhone = monthly(t, s);

        UUID minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pedro Souza", "2018-01-01");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        UUID minorCharge = monthly(t, minor);
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, minorCharge);

        job().runOnce();
        assertThat(history(opened)).extracting(h -> h.get("channel")).containsExactly("EMAIL", "EMAIL");
        assertThat(history(noPhone)).anySatisfy(h -> assertThat(h).containsEntry("channel", "SMS").containsEntry("status", "SKIPPED").containsEntry("reason", "NO_PHONE"));
        assertThat(outbox.list()).filteredOn(m -> m.type() == Outbox.Type.SMS).singleElement().satisfies(m -> assertThat(m.recipient()).isEqualTo("11987654321"));
    }

    @Test
    @DisplayName("CA12: com 3 SMS no mês o e-mail sai e o SMS não (SMS_LIMIT); SMS do mês anterior não conta")
    void monthlyLimit() {
        var t = tenant();
        UUID s = adult(t, "rem-limite@example.com");
        UUID c = monthly(t, s);
        for (String at : new String[] {"2026-10-01T12:00:00Z", "2026-10-02T12:00:00Z", "2026-10-03T12:00:00Z"}) addSms(t, s, at);
        job().runOnce();
        assertThat(mails("rem-limite%")).hasSize(1);
        assertThat(sms()).isZero();
        assertThat(history(c)).anySatisfy(h -> assertThat(h).containsEntry("channel", "SMS").containsEntry("status", "SKIPPED").containsEntry("reason", "SMS_LIMIT"));

        UUID previous = adult(t, "rem-mesanterior@example.com");
        UUID c2 = monthly(t, previous);
        for (String at : new String[] {"2026-09-28T12:00:00Z", "2026-09-29T12:00:00Z", "2026-09-30T12:00:00Z", "2026-10-01T02:00:00Z"}) addSms(t, previous, at);
        job().runOnce();
        assertThat(history(c2)).anySatisfy(h -> assertThat(h).containsEntry("channel", "SMS").containsEntry("status", "SENT"));
    }

    @Test
    @DisplayName("SMS recusado pela porta não grava sms_log nem histórico de SMS, e o e-mail segue registrado")
    void refused() {
        var t = tenant();
        UUID s = adult(t, "rem-recusa@example.com");
        UUID c = monthly(t, s);
        SmsSender refusing = (to, text) -> SendResult.NOT_SENT;
        job(NOON, refusing).runOnce();
        assertThat(adminJdbc().queryForObject("select count(*) from sms_log where student_id = ?", Integer.class, s)).isZero();
        assertThat(history(c)).singleElement().satisfies(h -> assertThat(h).containsEntry("channel", "EMAIL"));
    }
}
