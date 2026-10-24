package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-04 (D2, D7): e-mail "Pagamento confirmado" com o link do PDF, destinatário vindo do cadastro, e falha do envio sem desfazer o pagamento. */
class ReceiptEmailIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean EmailService email;

    @AfterEach
    void clean() {
        reset(email);
        BillingFixtures.clean(adminJdbc());
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from account where email like 'pay-%@example.com'");
        adminJdbc().update("delete from email_message where template = 'PAYMENT_CONFIRMED'");
    }

    private AccountPrincipal admin(Tenant t) {
        AccountPrincipal p = who(t, Role.ADMINISTRATOR);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "pay-" + p.accountId() + "@example.com");
        return p;
    }

    private JsonNode pay(AccountPrincipal who, UUID charge, String body) throws Exception {
        var r = call(MockMvcRequestBuilders.post("/api/v1/charges/" + charge + "/payments").contentType("application/json").content(body), who).getResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        return json.readTree(r.getContentAsString());
    }

    private UUID chargeOf(Tenant t, UUID student) {
        return BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 15000, "OPEN");
    }

    private int messages() {
        return adminJdbc().queryForObject("select count(*) from email_message where template = 'PAYMENT_CONFIRMED'", Integer.class);
    }

    @Test
    @DisplayName("CA9: aluno adulto com e-mail recebe a mensagem com o link do PDF e a resposta traz SENT; o menor manda ao responsável financeiro")
    void sent() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID adult = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set email = ? where id = ?", "pay-aluno@example.com", adult);
        var paid = pay(admin, chargeOf(t, adult), "{\"method\":\"CASH\",\"sendReceipt\":true}");
        assertThat(paid.get("receiptEmail").asText()).isEqualTo("SENT");
        var row = adminJdbc().queryForMap("select to_email, subject from email_message where template = 'PAYMENT_CONFIRMED'");
        assertThat(row.get("to_email")).isEqualTo("pay-aluno@example.com");
        assertThat(row.get("subject")).isEqualTo("Pagamento confirmado");
        org.mockito.Mockito.verify(email).send(eq(t.orgId()), eq("pay-aluno@example.com"), eq(EmailTemplate.PAYMENT_CONFIRMED),
                org.mockito.ArgumentMatchers.<Object>argThat(a -> String.valueOf(a).endsWith("/api/v1/public/receipts/" + paid.get("receipt").get("code").asText() + "/pdf")),
                any(), any());

        UUID minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pedro Souza", "2018-01-01");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        adminJdbc().update("update student set email = ? where id = ?", "pay-menor@example.com", minor);
        UUID charge = chargeOf(t, minor);
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, charge);
        pay(admin, charge, "{\"method\":\"CASH\",\"sendReceipt\":true}");
        var to = adminJdbc().queryForList("select to_email from email_message where template = 'PAYMENT_CONFIRMED' and to_email like 'gen-%'", String.class);
        assertThat(to).hasSize(1);
        assertThat(messages()).isEqualTo(2);
        // CA15: a auditoria diz que o envio foi pedido, sem e-mail nem CPF.
        var audit = adminJdbc().queryForList("select after_data::text from audit_log where organization_id = ? and action = 'payment.registered'", String.class, t.orgId());
        assertThat(audit).hasSize(2).allSatisfy(a -> assertThat(a).contains("\"receiptRequested\": true").doesNotContain("@", "cpf"));
    }

    @Test
    @DisplayName("CA10: sem e-mail, NO_ADDRESS e nenhuma mensagem; sem pedir, NOT_REQUESTED; e-mail inválido também é NO_ADDRESS")
    void noAddress() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        assertThat(pay(admin, chargeOf(t, student), "{\"method\":\"CASH\",\"sendReceipt\":true,\"observation\":\"  \"}").get("receiptEmail").asText()).isEqualTo("NO_ADDRESS");
        adminJdbc().update("update student set email = 'sem-arroba' where id = ?", student);
        assertThat(pay(admin, chargeOf(t, student), "{\"method\":\"CASH\",\"sendReceipt\":true}").get("receiptEmail").asText()).isEqualTo("NO_ADDRESS");
        adminJdbc().update("update student set email = 'pay-aluno@example.com' where id = ?", student);
        assertThat(pay(admin, chargeOf(t, student), "{\"method\":\"CASH\",\"sendReceipt\":false}").get("receiptEmail").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(pay(admin, chargeOf(t, student), "{\"method\":\"CASH\"}").get("receiptEmail").asText()).isEqualTo("NOT_REQUESTED");
        assertThat(messages()).isZero();
    }

    @Test
    @DisplayName("CA10: envio que lança ou fica pendente dá FAILED e o pagamento continua registrado")
    void failure() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set email = 'pay-aluno@example.com' where id = ?", student);
        doThrow(new IllegalStateException("fora do ar")).when(email).send(any(UUID.class), anyString(), eq(EmailTemplate.PAYMENT_CONFIRMED), any(), any(), any());
        UUID first = chargeOf(t, student);
        assertThat(pay(admin, first, "{\"method\":\"CASH\",\"sendReceipt\":true}").get("receiptEmail").asText()).isEqualTo("FAILED");
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, first)).isEqualTo("PAID");
        reset(email);
        doAnswer(inv -> {
            UUID id = UUID.randomUUID();
            adminJdbc().update("insert into email_message (id, to_email, template, subject, body, status, next_attempt_at) values (?, 'pay-aluno@example.com', 'PAYMENT_CONFIRMED', 's', 'b', 'PENDING', now())", id);
            return id;
        }).when(email).send(any(UUID.class), anyString(), eq(EmailTemplate.PAYMENT_CONFIRMED), any(), any(), any());
        UUID second = chargeOf(t, student);
        assertThat(pay(admin, second, "{\"method\":\"CASH\",\"sendReceipt\":true}").get("receiptEmail").asText()).isEqualTo("FAILED");
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, second)).isEqualTo("PAID");
    }

    @Test
    @DisplayName("CA15 (D7): endereço marcado \"Com problema\" não recebe o recibo: NO_ADDRESS, mensagem SKIPPED, pagamento registrado")
    void emailProblem() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set email = 'pay-aluno@example.com', email_problem_at = now() where id = ?", student);
        UUID charge = chargeOf(t, student);
        assertThat(pay(admin, charge, "{\"method\":\"CASH\",\"sendReceipt\":true}").get("receiptEmail").asText()).isEqualTo("NO_ADDRESS");
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge)).isEqualTo("PAID");
        assertThat(adminJdbc().queryForList("select status || ':' || last_error from email_message where template = 'PAYMENT_CONFIRMED'", String.class))
                .containsExactly("SKIPPED:EMAIL_PROBLEM");
    }
}
