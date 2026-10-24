package br.com.ctcore.billing;

import br.com.ctcore.integration.SendResult;
import br.com.ctcore.integration.SmsSender;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-COB-17, RN-COB-20 (D4): o SMS do aviso de bloqueio, só quando nenhum e-mail de lembrete da cobrança foi aberto e o pagador tem telefone, e no máximo 3
 * por aluno por mês civil (fuso da unidade, todos os tipos, pela {@code sms_log}). O que não sai fica no histórico com o motivo. Chamar dentro da transação
 * da organização, depois do e-mail do aviso.
 */
@Component
class BlockWarningSms {
    static final int MONTHLY_LIMIT = 3;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private final JdbcClient jdbc;
    private final SmsSender sms;
    private final MessageSource messages;
    private final Clock clock;

    BlockWarningSms(@Lazy JdbcClient jdbc, SmsSender sms, MessageSource messages, Clock clock) {
        this.jdbc = jdbc;
        this.sms = sms;
        this.messages = messages;
        this.clock = clock;
    }

    void send(UUID org, UUID chargeId, String zone, int days) {
        boolean opened = jdbc.sql("select exists (select 1 from charge_reminder r join email_message m on m.id = r.email_message_id "
                + "where r.charge_id = :c and r.channel = 'EMAIL' and m.opened_at is not null)").param("c", chargeId).query(Boolean.class).single();
        if (opened) return;
        var row = jdbc.sql("select s.id as student, case when c.payer_guardian_id is null then s.phone else g.phone end as phone, c.amount_cents, c.due_on, "
                + "c.reference_month, c.description from charge c join student s on s.organization_id = c.organization_id and s.id = c.student_id "
                + "left join guardian g on g.organization_id = c.organization_id and g.id = c.payer_guardian_id where c.id = :id and c.organization_id = :org")
                .param("id", chargeId).param("org", org).query().singleRow();
        UUID student = (UUID) row.get("student");
        String phone = (String) row.get("phone");
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (phone == null || phone.isBlank()) {
            record(org, chargeId, "SKIPPED", "NO_PHONE", now);
            return;
        }
        long month = jdbc.sql("select count(*) from sms_log where organization_id = :org and student_id = :s "
                + "and to_char(created_at at time zone :tz, 'YYYYMM') = to_char(cast(:now as timestamptz) at time zone :tz, 'YYYYMM')")
                .param("org", org).param("s", student).param("tz", zone).param("now", now).query(Long.class).single();
        if (month >= MONTHLY_LIMIT) {
            record(org, chargeId, "SKIPPED", "SMS_LIMIT", now);
            return;
        }
        String text = messages.getMessage("sms.block-warning", new Object[] {ReminderSender.describe(row.get("description"), row.get("reference_month")),
                ReminderSender.money(row.get("amount_cents")), ReminderSender.date(row.get("due_on")), String.valueOf(days)}, PT_BR);
        // ponytail: envio que o fornecedor recusa não grava nada e não é repetido (o e-mail do aviso já está no histórico); retentativa só se a especificação a pedir.
        if (sms.send(phone.strip(), text) != SendResult.SENT) return;
        jdbc.sql("insert into sms_log (id, organization_id, student_id, kind, created_at) values (:id, :org, :s, 'BLOCK_WARNING', :now)")
                .param("id", UUID.randomUUID()).param("org", org).param("s", student).param("now", now).update();
        record(org, chargeId, "SENT", null, now);
    }

    private void record(UUID org, UUID chargeId, String status, String reason, OffsetDateTime now) {
        jdbc.sql("insert into charge_reminder (id, organization_id, charge_id, kind, channel, status, reason, created_at) "
                + "values (:id, :org, :c, 'BLOCK_WARNING', 'SMS', :st, :reason, :now)").param("id", UUID.randomUUID()).param("org", org).param("c", chargeId)
                .param("st", status).param("reason", reason, Types.VARCHAR).param("now", now).update();
    }
}
