package br.com.ctcore.payment;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.integration.PaymentGateway;
import br.com.ctcore.organization.OrganizationBase;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Base dos testes do pagamento online (#96): unidade com conta de pagamento conectada, cobranças em aberto e o simulador como provedor. */
abstract class PaymentOnlineBase extends OrganizationBase {
    @MockitoSpyBean PaymentGateway gateway;
    @Autowired PaymentAttempts attempts;
    @Autowired PaymentConfirmations confirmations;

    @AfterEach
    void cleanOnline() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from notification");
        cleanLinked("payon-");
        adminJdbc().update("delete from student");
    }

    Tenant connected() {
        var t = tenant();
        adminJdbc().update("update unit_settings set payment_account_reference = 'acct-test' where unit_id = ?", t.unitId());
        return t;
    }

    UUID charge(Tenant t, String name, long cents, String status) {
        return BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), name), cents, status);
    }

    PaymentAttempts.Attempt start(Tenant t, String method, UUID... charges) {
        return attempts.start(t.orgId(), t.unitId(), List.of(charges), method, UUID.randomUUID());
    }

    int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    String attemptStatus(UUID attempt) {
        return adminJdbc().queryForObject("select status from payment_attempt where id = ?", String.class, attempt);
    }

    String chargeStatus(UUID charge) {
        return adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge);
    }

    org.springframework.mock.web.MockHttpServletResponse post(String body) throws Exception {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/webhooks/payments").secure(true);
        if (body != null) request.contentType("application/json").content(body);
        return mvc.perform(request).andReturn().getResponse();
    }

    br.com.ctcore.integration.simulated.SimulatedPaymentGateway sim() {
        return (br.com.ctcore.integration.simulated.SimulatedPaymentGateway) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(gateway);
    }
}
