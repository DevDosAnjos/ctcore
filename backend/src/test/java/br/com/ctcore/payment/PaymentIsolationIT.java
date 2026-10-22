package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-04: numeração do recibo por unidade sob concorrência, ações no painel e isolamento do PDF entre organizações (código e banco). */
class PaymentIsolationIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from account where email like 'pay-%@example.com'");
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "pay-" + p.accountId() + "@example.com");
        return p;
    }

    private int pay(AccountPrincipal who, UUID charge) throws Exception {
        var r = call(MockMvcRequestBuilders.post("/api/v1/charges/" + charge + "/payments").contentType("application/json").content("{\"method\":\"CASH\"}"), who).getResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        return json.readTree(r.getContentAsString()).get("receipt").get("number").asInt();
    }

    private UUID charge(Tenant t) {
        return BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza"), 100, "OPEN");
    }

    @Test
    @DisplayName("CA8: 20 pagamentos em paralelo na unidade A e 3 na B dão 1 a 20 e 1 a 3, sem repetir nem pular")
    void numbersPerUnit() throws Exception {
        var a = tenant();
        var b = tenant();
        var adminA = account(a, Role.ADMINISTRATOR);
        var adminB = account(b, Role.ADMINISTRATOR);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> fa = new ArrayList<>(), fb = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                UUID c = charge(a);
                Callable<Integer> job = () -> pay(adminA, c);
                fa.add(pool.submit(job));
            }
            for (int i = 0; i < 3; i++) {
                UUID c = charge(b);
                Callable<Integer> job = () -> pay(adminB, c);
                fb.add(pool.submit(job));
            }
            Set<Integer> na = new HashSet<>(), nb = new HashSet<>();
            for (var f : fa) na.add(f.get());
            for (var f : fb) nb.add(f.get());
            assertThat(na).hasSize(20).containsExactlyInAnyOrder(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toArray(Integer[]::new));
            assertThat(nb).containsExactlyInAnyOrder(1, 2, 3);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("CA14, CA12: o PDF de cada organização traz só a sua unidade; o papel de execução não vê recibo da outra mesmo sem filtro; cobrança aberta habilita registrar")
    void pdfAndRls() throws Exception {
        var a = tenant();
        var b = tenant();
        adminJdbc().update("update unit set name = 'Unidade Alfa' where id = ?", a.unitId());
        adminJdbc().update("update unit set name = 'Unidade Beta' where id = ?", b.unitId());
        UUID chargeA = charge(a);
        var adminA = account(a, Role.ADMINISTRATOR);
        var detail = json.readTree(call(MockMvcRequestBuilders.get("/api/v1/charges/" + chargeA), adminA).getResponse().getContentAsString());
        assertThat(detail.get("actions").get("registerPayment").asBoolean()).isTrue();
        pay(adminA, chargeA);
        pay(account(b, Role.ADMINISTRATOR), charge(b));
        var after = json.readTree(call(MockMvcRequestBuilders.get("/api/v1/charges/" + chargeA), adminA).getResponse().getContentAsString());
        assertThat(after.get("actions").get("registerPayment").asBoolean()).isFalse();

        String codeA = adminJdbc().queryForObject("select code from receipt where organization_id = ?", String.class, a.orgId());
        var pdf = mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/receipts/" + codeA + "/pdf").secure(true)).andReturn().getResponse();
        try (var doc = org.apache.pdfbox.Loader.loadPDF(pdf.getContentAsByteArray())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(doc);
            assertThat(text).contains("Unidade Alfa").doesNotContain("Unidade Beta");
        }
        try (var app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); var sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from receipt where code = '" + codeA + "'")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
            app.rollback();
        }
    }
}
