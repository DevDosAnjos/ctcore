package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import br.com.ctcore.integration.SmsSender;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.TextModels;
import java.time.Clock;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-COB-05 (RN-COB-19, RN-INA-02, D5): cobrança em conferência sai de lembrete, atraso e aviso de bloqueio; a pausa adia a data de bloqueio. */
class ProofBlockDateIT extends ChargeActionsBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired EmailService email;
    @Autowired TextModels texts;
    @Autowired SmsSender sms;
    @Autowired MessageSource messages;

    /** Mensalidade (prazo de bloqueio de 30 dias) de unidade com conta conectada, vencida há {@code daysAgo} dias. */
    private UUID monthly(Tenant t, UUID student, int daysAgo, String status, int paused) {
        adminJdbc().update("update unit_settings set payment_account_reference = 'conta-sintetica' where unit_id = ?", t.unitId());
        UUID c = charge(t, t.unitId(), student, today().minusDays(daysAgo), 15000, status);
        adminJdbc().update("update charge set type = 'MONTHLY', notified_at = now(), block_pause_days = ? where id = ?", paused, c);
        return c;
    }

    private int run() {
        Clock clock = Clock.fixed(today().atTime(15, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant(), ZoneId.of("UTC"));
        return new ReminderJob(transactions, jdbc, new ReminderSender(jdbc, email, clock, texts), new BlockWarningSms(jdbc, sms, messages, clock), clock).runOnce();
    }

    private int history(UUID charge) {
        return count("select count(*) from charge_reminder where charge_id = ?", charge);
    }

    @Test
    @DisplayName("CA5: em conferência não recebe lembrete automático nem aviso de bloqueio, não aparece em atraso, o manual dá NOT_OPEN e cancelar, renegociar e pagar dão 409")
    void inReviewIsLeftAlone() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID review = monthly(t, s, 25, "IN_REVIEW", 0), control = monthly(t, s, 25, "OPEN", 0);
        UUID scheduledReview = monthly(t, s, 3, "IN_REVIEW", 0), scheduledControl = monthly(t, s, 3, "OPEN", 0);

        run();

        assertThat(history(control)).isPositive();
        assertThat(history(scheduledControl)).isPositive();
        assertThat(history(review)).isZero();
        assertThat(history(scheduledReview)).isZero();

        var ids = ok(send(HttpMethod.GET, "/charges/overdue?sort=amount,asc", null, admin), 200).get("items").findValuesAsText("id");
        assertThat(ids).contains(control.toString(), scheduledControl.toString()).doesNotContain(review.toString(), scheduledReview.toString());
        var manual = ok(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(review) + "}", admin), 200);
        assertThat(manual.get("results").get(0).get("result").asText()).isEqualTo("NOT_OPEN");
        assertThat(history(review)).isZero();

        assertThat(send(HttpMethod.POST, "/charges/" + review + "/cancel", "{\"reason\":\"Engano\"}", admin).getStatus()).isEqualTo(409);
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(review), null, today().plusDays(10).toString(), 1, "Acordo"), admin).getStatus()).isEqualTo(409);
        assertThat(send(HttpMethod.POST, "/charges/" + review + "/payments", "{\"method\":\"CASH\"}", admin).getStatus()).isEqualTo(409);
        assertThat(status(review)).isEqualTo("IN_REVIEW");
    }

    @Test
    @DisplayName("CA10: os dias pausados somam à data de bloqueio: dias até o bloqueio e o aviso de 5 dias andam junto")
    void pauseMovesBlockDate() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID plain = monthly(t, s, 20, "OPEN", 0), paused = monthly(t, s, 20, "OPEN", 4);

        var items = ok(send(HttpMethod.GET, "/charges/overdue", null, admin), 200).get("items");
        for (var item : items) {
            int expected = item.get("id").asText().equals(paused.toString()) ? 14 : 10;
            assertThat(item.get("daysToBlock").asInt()).isEqualTo(expected);
        }

        // Vencida há 28 dias: sem pausa o bloqueio seria em 2 dias (o aviso de 5 dias já passou); com 3 dias pausados é hoje o dia do aviso.
        UUID late = monthly(t, s, 28, "OPEN", 0), latePaused = monthly(t, s, 28, "OPEN", 3);
        run();
        assertThat(history(latePaused)).isPositive();
        assertThat(count("select count(*) from charge_reminder where charge_id = ? and kind = 'BLOCK_WARNING'", late)).isZero();
        assertThat(count("select count(*) from charge_reminder where charge_id = ? and kind = 'BLOCK_WARNING'", latePaused)).isPositive();
    }
}
