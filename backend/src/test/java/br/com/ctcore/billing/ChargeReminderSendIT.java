package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

/** RF-COB-06, RF-COB-02 (#79): lembrete manual por e-mail e histórico no painel. */
class ChargeReminderSendIT extends ChargeActionsBase {
    @AfterEach
    void cleanMails() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from email_message where to_email like 'man-%' or to_email like 'gen-%'");
    }

    private UUID student(Tenant t, String address) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), address == null ? "sem-email" : address, "1990-01-01");
        adminJdbc().update("update student set email = ?, phone = '11912345678' where id = ?", address == null ? null : address + "@example.com", s);
        return s;
    }

    private UUID charge(Tenant t, UUID student, int daysFromToday, String status) {
        return charge(t, t.unitId(), student, today().plusDays(daysFromToday), 10000, status);
    }

    private String body(UUID... ids) {
        return "{\"chargeIds\":" + ids(ids) + "}";
    }

    private List<String> templates(String like) {
        return adminJdbc().queryForList("select template from email_message where to_email like ? order by to_email", String.class, like);
    }

    @Test
    @DisplayName("CA17, CA20, CA24: SENT, SENT e NOT_OPEN; o modelo segue a data; histórico MANUAL com quem enviou; uma auditoria por enviada; log sem endereço")
    void send() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID late = charge(t, student(t, "man-a"), -4, "OPEN"), dueToday = charge(t, student(t, "man-b"), 0, "OPEN"), future = charge(t, student(t, "man-c"), 6, "OPEN");
        UUID review = charge(t, student(t, "man-d"), -1, "IN_REVIEW");
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logs);
        JsonNode r;
        try {
            r = ok(send(HttpMethod.POST, "/charges/reminders", body(late, dueToday, review), admin), 200).get("results");
        } finally {
            ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
        }
        assertThat(r).hasSize(3);
        assertThat(r.get(0).get("chargeId").asText()).isEqualTo(late.toString());
        assertThat(r.get(0).get("result").asText()).isEqualTo("SENT");
        assertThat(r.get(1).get("result").asText()).isEqualTo("SENT");
        assertThat(r.get(2).get("result").asText()).isEqualTo("NOT_OPEN");
        ok(send(HttpMethod.POST, "/charges/reminders", body(future), admin), 200);
        assertThat(templates("man-a%")).containsExactly("REMINDER_OVERDUE");
        assertThat(templates("man-b%")).containsExactly("REMINDER_DUE");
        assertThat(templates("man-c%")).containsExactly("REMINDER_BEFORE");
        assertThat(templates("man-d%")).isEmpty();
        assertThat(adminJdbc().queryForList("select kind, channel, status, sent_by_account_id from charge_reminder where charge_id = ?", late))
                .singleElement().satisfies(h -> assertThat(h).containsEntry("kind", "MANUAL").containsEntry("channel", "EMAIL").containsEntry("status", "SENT")
                        .containsEntry("sent_by_account_id", admin.accountId()));
        assertThat(audits(t, "charge.reminder_sent")).isEqualTo(3);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'charge.reminder_sent' and (to_jsonb(audit_log)::text like '%@%' "
                + "or to_jsonb(audit_log)::text like '%11912345678%' or to_jsonb(audit_log)::text like '%vencimento%')", t.orgId())).isZero();
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("man-") || e.getFormattedMessage().contains("11912345678"));
    }

    @Test
    @DisplayName("CA18: lembrete manual de 2 horas atrás dá RECENT e nada sai; 25 horas depois dá SENT; RECENT não audita")
    void recent() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID c = charge(t, student(t, "man-recente"), -2, "OPEN");
        adminJdbc().update("insert into charge_reminder (id, organization_id, charge_id, kind, channel, status, created_at) values (?, ?, ?, 'MANUAL', 'EMAIL', 'SENT', now() - interval '2 hours')",
                UUID.randomUUID(), t.orgId(), c);
        assertThat(ok(send(HttpMethod.POST, "/charges/reminders", body(c), admin), 200).get("results").get(0).get("result").asText()).isEqualTo("RECENT");
        assertThat(templates("man-recente%")).isEmpty();
        assertThat(audits(t, "charge.reminder_sent")).isZero();
        adminJdbc().update("update charge_reminder set created_at = now() - interval '25 hours' where charge_id = ?", c);
        assertThat(ok(send(HttpMethod.POST, "/charges/reminders", body(c), admin), 200).get("results").get(0).get("result").asText()).isEqualTo("SENT");
        assertThat(audits(t, "charge.reminder_sent")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA19: lista vazia, com mais de 100, repetida ou fora do formato dá 400 e nada sai; sem endereço dá NO_ADDRESS e as demais seguem")
    void validation() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID good = charge(t, student(t, "man-valida"), -2, "OPEN"), none = charge(t, student(t, null), -2, "OPEN");
        UUID id = UUID.randomUUID();
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 101; i++) many.add(UUID.randomUUID());
        for (String body : new String[] {"{\"chargeIds\":[]}", "{\"chargeIds\":" + ids(many.toArray(new UUID[0])) + "}", "{\"chargeIds\":" + ids(id, id) + "}", "{\"chargeIds\":null}"}) {
            var response = send(HttpMethod.POST, "/charges/reminders", body, admin);
            assertThat(response.getStatus()).as(body).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("chargeIds");
        }
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":[\"nao-e-uuid\"]}", admin).getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from charge_reminder")).isZero();

        JsonNode r = ok(send(HttpMethod.POST, "/charges/reminders", body(none, good), admin), 200).get("results");
        assertThat(r.get(0).get("result").asText()).isEqualTo("NO_ADDRESS");
        assertThat(r.get(1).get("result").asText()).isEqualTo("SENT");
        assertThat(templates("man-valida%")).hasSize(1);
        assertThat(audits(t, "charge.reminder_sent")).isEqualTo(2);
    }

    @Test
    @DisplayName("Endereço marcado Com problema: EMAIL_PROBLEM no item")
    void problem() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t, "man-problema");
        adminJdbc().update("update student set email_problem_at = now() where id = ?", s);
        UUID c = charge(t, s, -2, "OPEN");
        assertThat(ok(send(HttpMethod.POST, "/charges/reminders", body(c), admin), 200).get("results").get(0).get("result").asText()).isEqualTo("EMAIL_PROBLEM");
    }

    @Test
    @DisplayName("CA21: o painel traz o automático e o manual, mais recente primeiro, com quem enviou; sendReminder só na aberta")
    void panel() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t, "man-painel");
        UUID c = charge(t, s, -2, "OPEN");
        adminJdbc().update("insert into charge_reminder (id, organization_id, charge_id, kind, offset_days, channel, status, created_at) values (?, ?, ?, 'SCHEDULED', 3, 'EMAIL', 'SENT', now() - interval '1 day')",
                UUID.randomUUID(), t.orgId(), c);
        ok(send(HttpMethod.POST, "/charges/reminders", body(c), admin), 200);
        JsonNode d = ok(send(HttpMethod.GET, "/charges/" + c, null, admin), 200);
        assertThat(d.get("reminders")).hasSize(2);
        assertThat(d.get("reminders").get(0).get("kind").asText()).isEqualTo("MANUAL");
        assertThat(d.get("reminders").get(0).get("sentBy").get("name").asText()).isEqualTo("Gestora Sintética");
        assertThat(d.get("reminders").get(0).get("channel").asText()).isEqualTo("EMAIL");
        assertThat(d.get("reminders").get(0).get("status").asText()).isEqualTo("SENT");
        assertThat(d.get("reminders").get(0).get("createdAt").asText()).isNotBlank();
        assertThat(d.get("reminders").get(1).get("kind").asText()).isEqualTo("SCHEDULED");
        assertThat(d.get("reminders").get(1).get("sentBy").isNull()).isTrue();
        assertThat(d.get("actions").get("sendReminder").asBoolean()).isTrue();
        for (String status : new String[] {"PAID", "CANCELED", "RENEGOTIATED", "IN_REVIEW"}) {
            UUID other = charge(t, s, -2, status);
            assertThat(ok(send(HttpMethod.GET, "/charges/" + other, null, admin), 200).get("actions").get("sendReminder").asBoolean()).as(status).isFalse();
        }
    }
}
