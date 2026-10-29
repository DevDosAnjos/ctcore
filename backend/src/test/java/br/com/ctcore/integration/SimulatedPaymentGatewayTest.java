package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.integration.PaymentStatus;
import br.com.ctcore.integration.simulated.SimulatedPaymentGateway;
import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SimulatedPaymentGatewayTest {

    private final SimulatedPaymentGateway gw = new SimulatedPaymentGateway();

    @Test
    void createIsIdempotentByKey() {
        PaymentCharge a = gw.createCharge("k1", 12345, PaymentMethod.PIX);
        PaymentCharge b = gw.createCharge("k1", 12345, PaymentMethod.PIX);
        assertThat(b.id()).isEqualTo(a.id());
        assertThat(a.amountCents()).isEqualTo(12345);
        assertThat(a.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(gw.createCharge("k2", 12345, PaymentMethod.PIX).id()).isNotEqualTo(a.id());
        assertThatThrownBy(() -> gw.createCharge("k1", 999, PaymentMethod.PIX)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gw.createCharge("k3", 0, PaymentMethod.PIX)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validTransitions() {
        assertThat(gw.approve(pending()).status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(gw.refuse(pending()).status()).isEqualTo(PaymentStatus.REFUSED);
        assertThat(gw.expire(pending()).status()).isEqualTo(PaymentStatus.EXPIRED);
        String r = pending();
        gw.approve(r);
        assertThat(gw.refund(r).status()).isEqualTo(PaymentStatus.REFUNDED);
        String d = pending();
        gw.approve(d);
        assertThat(gw.dispute(d).status()).isEqualTo(PaymentStatus.DISPUTED);
    }

    @Test
    void invalidTransitionsKeepTheState() {
        // refund/dispute from PENDING; approve/refuse/expire/refund/dispute from a final state
        String p = pending();
        assertThatThrownBy(() -> gw.refund(p)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gw.dispute(p)).isInstanceOf(IllegalStateException.class);
        assertThat(gw.resendNotice(p).status()).isEqualTo(PaymentStatus.PENDING);

        String a = pending();
        gw.approve(a);
        for (BiFunction<SimulatedPaymentGateway, String, PaymentCharge> op : java.util.List.<BiFunction<SimulatedPaymentGateway, String, PaymentCharge>>of(
                (g, i) -> g.approve(i), (g, i) -> g.refuse(i), (g, i) -> g.expire(i))) {
            assertThatThrownBy(() -> op.apply(gw, a)).isInstanceOf(IllegalStateException.class);
        }
        gw.refund(a);
        assertThatThrownBy(() -> gw.refund(a)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> gw.dispute(a)).isInstanceOf(IllegalStateException.class);
        assertThat(gw.resendNotice(a).status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void unknownChargeThrowsNoSuchElement() {
        assertThatThrownBy(() -> gw.approve("nope")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> gw.resendNotice("nope")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void resendReturnsTheSameEventWithoutChangingState() {
        PaymentCharge c = gw.createCharge("k", 500, PaymentMethod.PIX);
        assertThat(gw.resendNotice(c.id())).isEqualTo(c);
        PaymentCharge ok = gw.approve(c.id());
        assertThat(gw.resendNotice(c.id())).isEqualTo(ok);
        assertThat(gw.resendNotice(c.id())).isEqualTo(ok);
    }

    @Test
    void consultReturnsTheCurrentStateAndMethodIsRequired() {
        PaymentCharge c = gw.createCharge("kc", 700, PaymentMethod.BOLETO);
        assertThat(gw.consult(c.id())).isEqualTo(c);
        PaymentCharge ok = gw.approve(c.id());
        assertThat(gw.consult(c.id())).isEqualTo(ok);
        assertThatThrownBy(() -> gw.consult("nope")).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> gw.createCharge("kn", 700, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private String pending() {
        return gw.createCharge(java.util.UUID.randomUUID().toString(), 100, PaymentMethod.PIX).id();
    }
}
