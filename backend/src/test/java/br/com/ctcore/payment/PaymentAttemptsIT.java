package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.integration.PaymentMethod;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-PAG-03, RN-PAG-04: criação da tentativa de pagamento online (CA2, CA3, CA4). */
class PaymentAttemptsIT extends PaymentOnlineBase {

    private void refused(Tenant t, ErrorCode code, UUID... charges) {
        int before = count("select count(*) from payment_attempt");
        assertThatThrownBy(() -> start(t, "PIX", charges)).isInstanceOfSatisfying(ApiStatusException.class, e -> assertThat(e.code).isEqualTo(code));
        assertThat(count("select count(*) from payment_attempt")).isEqualTo(before);
        verify(gateway, never()).createCharge(any(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("CA3: duas cobranças abertas viram uma tentativa de Pix de 18000 centavos com duas ligações; a mesma chave não cria outra")
    void createsAndIsIdempotent() {
        var t = connected();
        UUID a = charge(t, "Maria Souza", 10000, "OPEN");
        UUID b = charge(t, "João Lima", 8000, "OPEN");
        UUID key = UUID.randomUUID();
        var first = attempts.start(t.orgId(), t.unitId(), List.of(a, b), "PIX", key);
        assertThat(first.status()).isEqualTo("CREATED");
        assertThat(first.amountCents()).isEqualTo(18000);
        assertThat(first.providerId()).isNotBlank();
        assertThat(count("select count(*) from payment_attempt_charge where attempt_id = ?", key)).isEqualTo(2);
        assertThat(attempts.start(t.orgId(), t.unitId(), List.of(a, b), "PIX", key)).isEqualTo(first);
        assertThat(count("select count(*) from payment_attempt where organization_id = ?", t.orgId())).isEqualTo(1);
        assertThat(gateway.consult(first.providerId()).amountCents()).isEqualTo(18000);
        assertThat(start(t, "BOLETO", a).method()).isEqualTo("BOLETO");
    }

    @Test
    @DisplayName("CA2: meio fora da lista, sem cobranças ou repetidas é recusado como entrada inválida e nada é gravado")
    void invalidInput() {
        var t = connected();
        UUID a = charge(t, "Maria Souza", 10000, "OPEN");
        for (String method : new String[] {"CASH", null}) {
            assertThatThrownBy(() -> start(t, method, a)).isInstanceOf(ApiValidationException.class);
        }
        assertThatThrownBy(() -> attempts.start(t.orgId(), t.unitId(), List.of(), "PIX", UUID.randomUUID())).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> attempts.start(t.orgId(), t.unitId(), null, "PIX", UUID.randomUUID())).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> start(t, "PIX", a, a)).isInstanceOf(ApiValidationException.class);
        assertThat(count("select count(*) from payment_attempt")).isZero();
        verify(gateway, never()).createCharge(any(), org.mockito.ArgumentMatchers.anyLong(), any(PaymentMethod.class));
    }

    @Test
    @DisplayName("CA2: o mês gerado e o aviso de vencimento não criam tentativa de boleto")
    void noBoletoByItself() {
        var t = connected();
        charge(t, "Maria Souza", 10000, "OPEN");
        assertThat(count("select count(*) from payment_attempt where method = 'BOLETO'")).isZero();
    }

    @Test
    @DisplayName("CA4: cobrança paga, em conferência, cancelada ou renegociada não é pagável")
    void notPayable() {
        var t = connected();
        for (String status : new String[] {"PAID", "IN_REVIEW", "CANCELED", "RENEGOTIATED"}) {
            refused(t, ErrorCode.CHARGE_NOT_PAYABLE, charge(t, "Maria " + status, 10000, status));
        }
    }

    @Test
    @DisplayName("CA4: inexistente, de outra organização, de outra unidade, valor zero, sem conta conectada ou conexão desligada")
    void otherRefusals() {
        var t = connected();
        UUID open = charge(t, "Maria Souza", 10000, "OPEN");
        refused(t, ErrorCode.NOT_FOUND, UUID.randomUUID());
        refused(t, ErrorCode.NOT_FOUND, charge(connected(), "Outra Org", 10000, "OPEN"));
        refused(t, ErrorCode.UNPROCESSABLE, charge(t, "Gratuito", 0, "OPEN"));
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Unidade B', ?)", second, t.orgId(), "b-" + second);
        UUID elsewhere = br.com.ctcore.billing.BillingFixtures.oneOff(adminJdbc(), t.orgId(), second,
                br.com.ctcore.billing.BillingFixtures.student(adminJdbc(), t.orgId(), second, "Outra Unidade"), 10000, "OPEN");
        refused(t, ErrorCode.CONFLICT, open, elsewhere);
        refused(t, ErrorCode.NOT_FOUND, open, UUID.randomUUID());
        assertThatThrownBy(() -> attempts.start(t.orgId(), UUID.randomUUID(), List.of(open), "PIX", UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiStatusException.class, e -> assertThat(e.code).isEqualTo(ErrorCode.NOT_FOUND));
        adminJdbc().update("update unit_settings set payment_account_reference = null where unit_id = ?", t.unitId());
        refused(t, ErrorCode.PAYMENT_ACCOUNT_NOT_CONNECTED, open);
        adminJdbc().update("update unit_settings set payment_account_reference = ' ' where unit_id = ?", t.unitId());
        refused(t, ErrorCode.PAYMENT_ACCOUNT_NOT_CONNECTED, open);
        adminJdbc().update("update unit_settings set payment_account_reference = 'acct-test' where unit_id = ?", t.unitId());
        org.mockito.Mockito.doReturn(false).when(gateway).isEnabled();
        refused(t, ErrorCode.PAYMENT_UNAVAILABLE, open);
    }
}
