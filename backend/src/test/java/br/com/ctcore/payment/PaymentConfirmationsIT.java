package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import br.com.ctcore.identity.Role;
import br.com.ctcore.integration.PaymentCharge;
import br.com.ctcore.integration.PaymentStatus;
import br.com.ctcore.payment.PaymentConfirmations.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-PAG-10, RN-PAG-11, RN-PAG-13, RN-COB-15: baixa sem pessoa, idempotência, duplicidade, recusa e demais estados (CA1 a CA12, CA18). */
class PaymentConfirmationsIT extends PaymentOnlineBase {


    private Result apply(Tenant t, PaymentAttempts.Attempt a) {
        return confirmations.apply(t.orgId(), a.providerId());
    }

    @Test
    @DisplayName("CA5, CA7, CA10: aprovado, cada cobrança fica paga com pagamento do portal, recibo 5 da unidade, auditoria do sistema e aviso à gestão")
    void approves() {
        var t = connected();
        var admin = linked(t.orgId(), Role.ADMINISTRATOR, "payon-", t.unitId());
        adminJdbc().update("insert into receipt_counter (unit_id, organization_id, last_number) values (?, ?, 4)", t.unitId(), t.orgId());
        UUID a = charge(t, "Maria Souza", 10000, "OPEN");
        UUID b = charge(t, "João Lima", 8000, "OPEN");
        var attempt = attempts.start(t.orgId(), t.unitId(), List.of(a, b), "PIX", UUID.randomUUID());
        sim().approve(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(attemptStatus(attempt.id())).isEqualTo("APPROVED");
        for (UUID c : List.of(a, b)) {
            assertThat(chargeStatus(c)).isEqualTo("PAID");
            var p = adminJdbc().queryForMap("select method, origin, registered_by_account_id, external_id, status from payment where charge_id = ?", c);
            assertThat(p).containsEntry("method", "PIX_MP").containsEntry("origin", "PORTAL").containsEntry("registered_by_account_id", null)
                    .containsEntry("external_id", attempt.providerId() + ":" + c).containsEntry("status", "CONFIRMED");
        }
        assertThat(adminJdbc().queryForList("select number from receipt where unit_id = ? order by number", Integer.class, t.unitId())).containsExactly(5, 6);
        var audits = adminJdbc().queryForList("select actor_type, after_data::text as after from audit_log where action = 'payment.registered' and organization_id = ?", t.orgId());
        assertThat(audits).hasSize(2).allSatisfy(r -> assertThat(r).containsEntry("actor_type", "SYSTEM"));
        assertThat(audits.get(0).get("after").toString()).doesNotContain("card");
        var notes = adminJdbc().queryForList("select params::text as params from notification where link_id = ? and type = 'PAYMENT_RECEIVED'", admin.activeLinkId());
        assertThat(notes).hasSize(2);
        assertThat(notes.stream().map(n -> n.get("params").toString()).reduce("", String::concat)).contains("Maria Souza").contains("10000").contains("João Lima");
    }

    @Test
    @DisplayName("CA6, CA8: pendente no provedor não baixa; repetir a confirmação não cria segundo pagamento nem recibo")
    void pendingAndRepeat() {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "CREDIT_CARD", c);
        assertThat(apply(t, attempt)).isEqualTo(Result.IGNORED);
        assertThat(chargeStatus(c)).isEqualTo("OPEN");
        sim().approve(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(apply(t, attempt)).isEqualTo(Result.DUPLICATE);
        assertThat(count("select count(*) from payment where charge_id = ?", c)).isEqualTo(1);
        assertThat(count("select count(*) from receipt where unit_id = ?", t.unitId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select method from payment where charge_id = ?", String.class, c)).isEqualTo("CREDIT_CARD");
    }

    @Test
    @DisplayName("CA9: cobrança já paga no balcão não recebe segundo pagamento; vira duplicidade aberta, e a outra cobrança da tentativa recebe a baixa")
    void duplicate() {
        var t = connected();
        UUID paid = charge(t, "Maria Souza", 10000, "OPEN");
        UUID other = charge(t, "João Lima", 8000, "OPEN");
        var attempt = start(t, "PIX", paid, other);
        adminJdbc().update("update charge set status = 'PAID' where id = ?", paid);
        adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) "
                + "select gen_random_uuid(), organization_id, id, student_id, unit_id, amount_cents, 'CASH', 'MANAGEMENT', now() from charge where id = ?", paid);
        sim().approve(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(attemptStatus(attempt.id())).isEqualTo("APPROVED");
        assertThat(count("select count(*) from payment where charge_id = ?", paid)).isEqualTo(1);
        assertThat(count("select count(*) from receipt where unit_id = ?", t.unitId())).isEqualTo(1);
        var d = adminJdbc().queryForMap("select amount_cents, method, status, provider_id from payment_duplicate where charge_id = ?", paid);
        assertThat(d).containsEntry("amount_cents", 10000L).containsEntry("method", "PIX").containsEntry("status", "OPEN").containsEntry("provider_id", attempt.providerId());
        assertThat(chargeStatus(other)).isEqualTo("PAID");
        assertThat(apply(t, attempt)).isEqualTo(Result.DUPLICATE);
        assertThat(count("select count(*) from payment_duplicate")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA11: recusa deixa a cobrança em aberto, audita, avisa a gestão uma vez; segundo aviso não repete")
    void refuses() {
        var t = connected();
        var admin = linked(t.orgId(), Role.ADMINISTRATOR, "payon-", t.unitId());
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "DEBIT_CARD", c);
        sim().refuse(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(apply(t, attempt)).isEqualTo(Result.DUPLICATE);
        assertThat(attemptStatus(attempt.id())).isEqualTo("REFUSED");
        assertThat(chargeStatus(c)).isEqualTo("OPEN");
        assertThat(count("select count(*) from audit_log where action = 'payment_attempt.refused' and organization_id = ?", t.orgId())).isEqualTo(1);
        assertThat(count("select count(*) from notification where link_id = ? and type = 'PAYMENT_REFUSED'", admin.activeLinkId())).isEqualTo(1);
        assertThat(count("select count(*) from payment")).isZero();
    }

    @Test
    @DisplayName("CA12: expirada, estornada e contestada só mudam a tentativa; transição inexistente não altera nada")
    void otherStates() {
        var t = connected();
        UUID c1 = charge(t, "Maria Souza", 10000, "OPEN");
        var expired = start(t, "BOLETO", c1);
        sim().expire(expired.providerId());
        assertThat(apply(t, expired)).isEqualTo(Result.APPLIED);
        assertThat(attemptStatus(expired.id())).isEqualTo("EXPIRED");
        assertThat(chargeStatus(c1)).isEqualTo("OPEN");

        UUID c2 = charge(t, "João Lima", 5000, "OPEN");
        var refunded = start(t, "PIX", c2);
        sim().approve(refunded.providerId());
        apply(t, refunded);
        sim().refund(refunded.providerId());
        assertThat(apply(t, refunded)).isEqualTo(Result.APPLIED);
        assertThat(attemptStatus(refunded.id())).isEqualTo("REFUNDED");
        assertThat(chargeStatus(c2)).isEqualTo("PAID");

        UUID c3 = charge(t, "Ana Reis", 5000, "OPEN");
        var disputed = start(t, "CREDIT_CARD", c3);
        sim().approve(disputed.providerId());
        apply(t, disputed);
        sim().dispute(disputed.providerId());
        assertThat(apply(t, disputed)).isEqualTo(Result.APPLIED);
        assertThat(attemptStatus(disputed.id())).isEqualTo("DISPUTED");

        doReturn(new PaymentCharge(disputed.providerId(), 5000, PaymentStatus.EXPIRED)).when(gateway).consult(disputed.providerId());
        assertThat(apply(t, disputed)).isEqualTo(Result.IGNORED);
        assertThat(attemptStatus(disputed.id())).isEqualTo("DISPUTED");
        assertThat(apply(t, disputed)).isEqualTo(Result.IGNORED);
        assertThat(confirmations.apply(t.orgId(), "unknown")).isEqualTo(Result.UNKNOWN);
        assertThat(confirmations.apply(connected().orgId(), disputed.providerId())).isEqualTo(Result.UNKNOWN);
    }

    @Test
    @DisplayName("CA18: provedor aprovou cobrança cancelada: alerta, sem baixa nem duplicidade; valor diferente: alerta único e nenhuma baixa")
    void divergences() {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "PIX", c);
        adminJdbc().update("update charge set status = 'CANCELED' where id = ?", c);
        sim().approve(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(count("select count(*) from payment")).isZero();
        assertThat(count("select count(*) from payment_duplicate")).isZero();
        var alert = adminJdbc().queryForMap("select kind, detail, unit_id from payment_alert where attempt_id = ?", attempt.id());
        assertThat(alert).containsEntry("kind", "DIVERGENCE").containsEntry("unit_id", t.unitId());
        assertThat(alert.get("detail").toString()).contains("CANCELED");

        UUID c2 = charge(t, "João Lima", 5000, "OPEN");
        var other = start(t, "PIX", c2);
        doReturn(new PaymentCharge(other.providerId(), 4999, PaymentStatus.APPROVED)).when(gateway).consult(other.providerId());
        assertThat(apply(t, other)).isEqualTo(Result.IGNORED);
        assertThat(apply(t, other)).isEqualTo(Result.IGNORED);
        assertThat(count("select count(*) from payment_alert where attempt_id = ?", other.id())).isEqualTo(1);
        assertThat(chargeStatus(c2)).isEqualTo("OPEN");
        assertThat(attemptStatus(other.id())).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("CA1: o código só usa a interface do provedor; tentativa e confirmação funcionam com uma implementação que só responde por ela")
    void worksThroughTheInterfaceOnly() {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "PIX", c);
        doReturn(new PaymentCharge(attempt.providerId(), 10000, PaymentStatus.APPROVED)).when(gateway).consult(attempt.providerId());
        assertThat(apply(t, attempt)).isEqualTo(Result.APPLIED);
        assertThat(chargeStatus(c)).isEqualTo("PAID");
        verify(gateway, never()).approve(any());
        verify(gateway, never()).refund(any());
    }
}
