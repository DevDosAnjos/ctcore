package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-PAG-10, RN-PAG-11: o aviso do provedor sem sessão (CA6, CA13, CA14, CA20). */
class PaymentWebhookIT extends PaymentOnlineBase {

    String notice(String providerId) {
        return adminJdbc().queryForObject("select result from payment_notice where provider_id = ? order by received_at desc limit 1", String.class, providerId);
    }

    @Test
    @DisplayName("CA13, CA6, CA8: aviso sem sessão baixa pela consulta; corpo que diz aprovado com o provedor pendente não baixa; repetido fica DUPLICATE")
    void appliesByConsultation() throws Exception {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "PIX", c);
        var lie = post("{\"id\":\"" + attempt.providerId() + "\",\"status\":\"APPROVED\",\"card\":\"4111111111111111\",\"token\":\"abc\"}");
        assertThat(lie.getStatus()).isEqualTo(200);
        assertThat(lie.getContentAsString()).isEqualTo("{}");
        assertThat(notice(attempt.providerId())).isEqualTo("IGNORED");
        assertThat(chargeStatus(c)).isEqualTo("OPEN");
        sim().approve(attempt.providerId());
        assertThat(post("{\"id\":\"" + attempt.providerId() + "\"}").getStatus()).isEqualTo(200);
        assertThat(post("{\"id\":\"" + attempt.providerId() + "\"}").getStatus()).isEqualTo(200);
        assertThat(chargeStatus(c)).isEqualTo("PAID");
        assertThat(count("select count(*) from payment where charge_id = ?", c)).isEqualTo(1);
        assertThat(adminJdbc().queryForList("select result from payment_notice where provider_id = ? order by received_at", String.class, attempt.providerId()))
                .containsExactly("IGNORED", "APPLIED", "DUPLICATE");
        assertThat(count("select count(*) from payment_notice where provider_id = ? and processed_at is not null", attempt.providerId())).isEqualTo(3);
        String content = adminJdbc().queryForObject("select string_agg(content::text, '') from payment_notice where provider_id = ?", String.class, attempt.providerId());
        assertThat(content).doesNotContain("4111").doesNotContain("token").doesNotContain("APPROVED");
    }

    @Test
    @DisplayName("CA13: id desconhecido responde 200 e fica UNKNOWN; corpo ausente, sem id, vazio ou com id de 101 caracteres é 400; GET é 405")
    void unknownAndInvalid() throws Exception {
        String unknown = "nope-" + UUID.randomUUID();
        assertThat(post("{\"id\":\"" + unknown + "\"}").getStatus()).isEqualTo(200);
        assertThat(notice(unknown)).isEqualTo("UNKNOWN");
        assertThat(count("select count(*) from payment_notice where provider_id = ? and organization_id is null", unknown)).isEqualTo(1);
        assertThat(post(null).getStatus()).isEqualTo(400);
        assertThat(post("{}").getStatus()).isEqualTo(400);
        assertThat(post("{\"id\":\"\"}").getStatus()).isEqualTo(400);
        assertThat(post("{\"id\":\" \"}").getStatus()).isEqualTo(400);
        assertThat(post("{\"id\":null}").getStatus()).isEqualTo(400);
        assertThat(post("{\"id\":\"" + "x".repeat(101) + "\"}").getStatus()).isEqualTo(400);
        assertThat(post("{\"id\":\"" + "x".repeat(100) + "\"}").getStatus()).isEqualTo(200);
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/v1/webhooks/payments").secure(true)).andReturn().getResponse().getStatus()).isEqualTo(405);
    }

    @Test
    @DisplayName("CA14: erro na consulta ao provedor: 500, aviso FAILED, alerta WEBHOOK_FAILURE em aberto (sem repetir) e a tentativa continua Criada")
    void failure() throws Exception {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "PIX", c);
        sim().approve(attempt.providerId());
        doThrow(new IllegalStateException("provider down 4111111111111111")).when(gateway).consult(attempt.providerId());
        assertThat(post("{\"id\":\"" + attempt.providerId() + "\"}").getStatus()).isEqualTo(500);
        assertThat(post("{\"id\":\"" + attempt.providerId() + "\"}").getStatus()).isEqualTo(500);
        assertThat(adminJdbc().queryForList("select result from payment_notice where provider_id = ?", String.class, attempt.providerId())).containsExactly("FAILED", "FAILED");
        var alerts = adminJdbc().queryForList("select kind, detail from payment_alert where attempt_id = ? and resolved_at is null", attempt.id());
        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0)).containsEntry("kind", "WEBHOOK_FAILURE");
        assertThat(alerts.get(0).get("detail").toString()).doesNotContain("4111");
        assertThat(attemptStatus(attempt.id())).isEqualTo("CREATED");
        assertThat(chargeStatus(c)).isEqualTo("OPEN");
    }
}
