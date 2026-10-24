package br.com.ctcore.billing;

import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.mail.TextModel;
import br.com.ctcore.mail.TextModels;
import java.sql.Types;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-COB-17, RN-COB-18, RF-COB-06: o envio de UM lembrete por e-mail, igual para o automático, o aviso de bloqueio e o manual. Chamar dentro da transação
 * da organização, com a linha da cobrança já travada ({@code for update}) e conferida como aberta. Grava o histórico ({@code charge_reminder}); nada de
 * endereço, telefone ou texto vai a log.
 */
@Component
class ReminderSender {
    enum Result { SENT, NO_ADDRESS, EMAIL_PROBLEM }

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final JdbcClient jdbc;
    private final EmailService email;
    private final Clock clock;
    private final TextModels texts;

    ReminderSender(@Lazy JdbcClient jdbc, EmailService email, Clock clock, TextModels texts) {
        this.texts = texts;
        this.jdbc = jdbc;
        this.email = email;
        this.clock = clock;
    }

    /** O modelo pela data: antes, no dia ou depois do vencimento. */
    static EmailTemplate template(LocalDate today, LocalDate dueOn) {
        return today.isBefore(dueOn) ? EmailTemplate.REMINDER_BEFORE : today.isEqual(dueOn) ? EmailTemplate.REMINDER_DUE : EmailTemplate.REMINDER_OVERDUE;
    }

    static String money(Object cents) {
        return NumberFormat.getCurrencyInstance(PT_BR).format(((Number) cents).longValue() / 100.0);
    }

    static String date(Object sqlDate) {
        return ((java.sql.Date) sqlDate).toLocalDate().format(DATE);
    }

    static String describe(Object description, Object referenceMonth) {
        return description != null ? (String) description
                : referenceMonth != null ? "Mensalidade " + ((java.sql.Date) referenceMonth).toLocalDate().format(MONTH) : "Cobrança";
    }

    /**
     * Nulo quando o lembrete automático (tipo e dia) já foi registrado; senão o resultado, já gravado no histórico. {@code days}: dias que faltam, só no
     * aviso de bloqueio.
     */
    Result send(UUID org, UUID chargeId, String kind, Integer offset, EmailTemplate template, UUID by, Integer days) {
        if (!"MANUAL".equals(kind) && jdbc.sql("select 1 from charge_reminder where charge_id = :c and kind = :k and channel = 'EMAIL' "
                + "and coalesce(offset_days, 0) = coalesce(cast(:o as int), 0)").param("c", chargeId).param("k", kind).param("o", offset, Types.INTEGER)
                .query(Integer.class).optional().isPresent()) {
            return null;
        }
        var row = jdbc.sql("select coalesce(ga.email, s.email) as email, c.amount_cents, c.due_on, c.reference_month, c.description, coalesce(ga.name, s.name) as person, u.name as unit_name "
                + "from charge c join student s on s.organization_id = c.organization_id and s.id = c.student_id "
                + "join unit u on u.organization_id = c.organization_id and u.id = c.unit_id left join guardian g on g.organization_id = c.organization_id and g.id = c.payer_guardian_id left join account ga on ga.id = g.account_id "
                + "where c.id = :id and c.organization_id = :org").param("id", chargeId).param("org", org).query().singleRow();
        String to = (String) row.get("email");
        Result result = Result.NO_ADDRESS;
        UUID message = null;
        if (to != null && to.contains("@")) {
            Object[] args = {describe(row.get("description"), row.get("reference_month")), money(row.get("amount_cents")), date(row.get("due_on")), days};
            TextModel model = TextModel.reminder(template);
            var own = model == null ? java.util.Optional.<TextModels.Text>empty() : texts.custom(org, model);
            if (own.isPresent()) {
                // RN-COM-12: texto próprio da organização; {link_pagamento} sai vazio até haver pagamento online (D4)
                Map<String, String> values = Map.of("nome", String.valueOf(row.get("person")), "valor", (String) args[1], "vencimento", (String) args[2],
                        "unidade", String.valueOf(row.get("unit_name")), "descricao", (String) args[0], "link_pagamento", "");
                message = email.sendText(org, to.strip(), template, TextModels.fill(own.get().subject(), values), TextModels.fill(own.get().body(), values));
            } else {
                message = email.send(org, to.strip(), template, days == null ? Arrays.copyOf(args, 3) : args);
            }
            result = "SKIPPED".equals(jdbc.sql("select status from email_message where id = :id").param("id", message).query(String.class).single())
                    ? Result.EMAIL_PROBLEM : Result.SENT;
        }
        jdbc.sql("insert into charge_reminder (id, organization_id, charge_id, kind, offset_days, channel, status, reason, email_message_id, sent_by_account_id, created_at) "
                + "values (:id, :org, :c, :k, :o, 'EMAIL', :st, :reason, :m, :by, :now)").param("id", UUID.randomUUID()).param("org", org).param("c", chargeId)
                .param("k", kind).param("o", offset, Types.INTEGER).param("st", result == Result.SENT ? "SENT" : "SKIPPED")
                .param("reason", result == Result.SENT ? null : result.name(), Types.VARCHAR).param("m", message, Types.OTHER).param("by", by, Types.OTHER)
                .param("now", OffsetDateTime.now(clock)).update();
        return result;
    }
}
