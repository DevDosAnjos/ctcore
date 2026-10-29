package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CA19: o aviso só afeta a organização dona da tentativa e não revela se ela existe. */
class PaymentWebhookIsolationIT extends PaymentOnlineBase {

    @Test
    @DisplayName("CA19: aviso com o id da tentativa da A muda só dados da A; para a B (não confirmada) e para id inexistente o status e o corpo são iguais")
    void onlyTheOwnerChanges() throws Exception {
        var a = connected();
        var b = connected();
        UUID ca = charge(a, "Maria Souza", 10000, "OPEN");
        UUID cb = charge(b, "João Lima", 10000, "OPEN");
        var attemptA = start(a, "PIX", ca);
        var attemptB = start(b, "PIX", cb);
        var forB = post("{\"id\":\"" + attemptB.providerId() + "\"}");
        var forNobody = post("{\"id\":\"inexistente-" + UUID.randomUUID() + "\"}");
        assertThat(forB.getStatus()).isEqualTo(forNobody.getStatus()).isEqualTo(200);
        assertThat(forB.getContentAsString()).isEqualTo(forNobody.getContentAsString());
        sim().approve(attemptA.providerId());
        post("{\"id\":\"" + attemptA.providerId() + "\"}");
        assertThat(chargeStatus(ca)).isEqualTo("PAID");
        assertThat(chargeStatus(cb)).isEqualTo("OPEN");
        assertThat(attemptStatus(attemptB.id())).isEqualTo("CREATED");
        assertThat(count("select count(*) from payment where organization_id = ?", b.orgId())).isZero();
        assertThat(count("select count(*) from payment_notice where organization_id = ? and provider_id = ?", b.orgId(), attemptA.providerId())).isZero();
        assertThat(count("select count(*) from payment_notice where organization_id = ? and provider_id = ?", a.orgId(), attemptA.providerId())).isEqualTo(1);
    }
}
