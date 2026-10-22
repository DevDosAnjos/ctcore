package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-12, RN-COB-16, RN-MAT-06, RNF-12: pagamento no balcão, recibo, permissão, isolamento, auditoria e corrida. */
class PaymentIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like 'pay-%@example.com'");
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "pay-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID charge(Tenant t, long amount, String status) {
        return BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza"), amount, status);
    }

    private static MockHttpServletRequestBuilder pay(UUID charge, String body) {
        return MockMvcRequestBuilders.post("/api/v1/charges/" + charge + "/payments").contentType("application/json").content(body);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        return call(request, who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private int count(String table) {
        return adminJdbc().queryForObject("select count(*) from " + table, Integer.class);
    }

    @Test
    @DisplayName("CA3: Administrador paga a cobrança em aberto: 201, cobrança PAID, pagamento e recibo; com ou sem amountCents")
    void registers() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var charge = charge(t, 15000, "OPEN");
        var response = send(pay(charge, "{\"method\":\"CASH\",\"amountCents\":15000,\"observation\":\" na recepção \"}"), admin);
        assertThat(response.getStatus()).isEqualTo(201);
        var b = body(response);
        assertThat(b.get("amountCents").asLong()).isEqualTo(15000);
        assertThat(b.get("method").asText()).isEqualTo("CASH");
        assertThat(b.get("observation").asText()).isEqualTo("na recepção");
        assertThat(b.get("registeredBy").asText()).isEqualTo(admin.accountId().toString());
        assertThat(b.get("receipt").get("number").asInt()).isEqualTo(1);
        assertThat(b.get("receipt").get("code").asText()).matches("[A-Z2-9]{16}");
        assertThat(OffsetDateTime.parse(b.get("paidAt").asText())).isBeforeOrEqualTo(OffsetDateTime.now());
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge)).isEqualTo("PAID");
        assertThat(adminJdbc().queryForObject("select origin || method from payment where charge_id = ?", String.class, charge)).isEqualTo("MANAGEMENTCASH");
        var other = charge(t, 9000, "OPEN");
        var past = OffsetDateTime.now().minusDays(2).withNano(0);
        var second = send(pay(other, "{\"method\":\"PIX_KEY\",\"paidAt\":\"" + past + "\"}"), admin);
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(OffsetDateTime.parse(body(second).get("paidAt").asText()).toInstant()).isEqualTo(past.toInstant());
        assertThat(body(second).get("amountCents").asLong()).isEqualTo(9000);
    }

    @Test
    @DisplayName("CA3: valor diferente 422; meio inválido, data futura e observação com 201 caracteres 400 com o campo; nada gravado")
    void rejects() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var charge = charge(t, 15000, "OPEN");
        assertThat(send(pay(charge, "{\"method\":\"CASH\",\"amountCents\":14999}"), admin).getStatus()).isEqualTo(422);
        for (String[] bad : new String[][] {{"method", "{\"method\":\"PIX_MP\"}"}, {"method", "{}"}, {"method", "{\"method\":\"BOLETO\"}"},
                {"paidAt", "{\"method\":\"CASH\",\"paidAt\":\"" + OffsetDateTime.now().plusDays(1) + "\"}"},
                {"observation", "{\"method\":\"CASH\",\"observation\":\"" + "x".repeat(201) + "\"}"}}) {
            var r = send(pay(charge, bad[1]), admin);
            assertThat(r.getStatus()).as(bad[1]).isEqualTo(400);
            assertThat(r.getContentAsString()).contains(bad[0]);
        }
        assertThat(count("payment")).isZero();
        assertThat(count("receipt")).isZero();
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge)).isEqualTo("OPEN");
        assertThat(send(pay(charge, "{\"method\":\"CASH\",\"observation\":\"" + "x".repeat(200) + "\"}"), admin).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA4: cobrança já paga, cancelada, renegociada, estornada ou em conferência dá 409 CHARGE_NOT_PAYABLE; continua um pagamento")
    void notPayable() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var charge = charge(t, 15000, "OPEN");
        assertThat(send(pay(charge, "{\"method\":\"CASH\"}"), admin).getStatus()).isEqualTo(201);
        var again = send(pay(charge, "{\"method\":\"CASH\"}"), admin);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(body(again).get("code").asText()).isEqualTo("CHARGE_NOT_PAYABLE");
        assertThat(count("payment")).isEqualTo(1);
        assertThat(count("receipt")).isEqualTo(1);
        for (String status : new String[] {"CANCELED", "RENEGOTIATED", "REFUNDED", "IN_REVIEW"}) {
            assertThat(send(pay(charge(t, 100, status), "{\"method\":\"CASH\"}"), admin).getStatus()).as(status).isEqualTo(409);
        }
        assertThat(count("payment")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4: duas chamadas simultâneas na mesma cobrança dão um 201 e um 409; external_id repetido na unidade é recusado pelo banco")
    void raceAndExternalId() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var charge = charge(t, 15000, "OPEN");
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> send(pay(charge, "{\"method\":\"CASH\"}"), admin).getStatus()));
            Set<Integer> statuses = new HashSet<>();
            for (var f : results) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("payment")).isEqualTo(1);
        var a = charge(t, 100, "OPEN");
        var b = charge(t, 100, "OPEN");
        String insert = "insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at, external_id) "
                + "select gen_random_uuid(), organization_id, id, student_id, unit_id, 100, 'PIX_MP', 'PORTAL', now(), 'ext-1' from charge where id = ?";
        adminJdbc().update(insert, a);
        assertThatThrownBy(() -> adminJdbc().update(insert, b)).hasMessageContaining("payment_external_once");
    }

    @Test
    @DisplayName("CA5: números 1, 2, 3 por unidade, a outra unidade começa em 1; recusa não gasta número; códigos únicos")
    void numbering() throws Exception {
        var t = tenant();
        var other = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var otherAdmin = account(other, Role.ADMINISTRATOR);
        Set<String> codes = new HashSet<>();
        for (int expected = 1; expected <= 3; expected++) {
            var c = charge(t, 100, "OPEN");
            var r = body(send(pay(c, "{\"method\":\"CASH\"}"), admin));
            assertThat(r.get("receipt").get("number").asInt()).isEqualTo(expected);
            codes.add(r.get("receipt").get("code").asText());
            if (expected == 2) assertThat(send(pay(c, "{\"method\":\"CASH\"}"), admin).getStatus()).isEqualTo(409);
        }
        var o = body(send(pay(charge(other, 100, "OPEN"), "{\"method\":\"CASH\"}"), otherAdmin));
        assertThat(o.get("receipt").get("number").asInt()).isEqualTo(1);
        codes.add(o.get("receipt").get("code").asText());
        assertThat(codes).hasSize(4);
    }

    @Test
    @DisplayName("CA5: cinco pagamentos simultâneos na mesma unidade geram cinco números distintos e consecutivos")
    void concurrentNumbers() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        List<UUID> charges = new ArrayList<>();
        for (int i = 0; i < 5; i++) charges.add(charge(t, 100, "OPEN"));
        var pool = Executors.newFixedThreadPool(5);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (UUID c : charges) {
                Callable<Integer> job = () -> body(send(pay(c, "{\"method\":\"CASH\"}"), admin)).get("receipt").get("number").asInt();
                results.add(pool.submit(job));
            }
            Set<Integer> numbers = new HashSet<>();
            for (var f : results) numbers.add(f.get());
            assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("CA7: Professor, Aluno e Responsável 403; sem sessão 401; Administrador de outra unidade 404; Coordenador 201")
    void permissions() throws Exception {
        var t = tenant();
        var charge = charge(t, 100, "OPEN");
        assertThat(send(pay(charge, "{\"method\":\"CASH\"}"), null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(send(pay(charge, "{\"method\":\"CASH\"}"), account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        var elsewhere = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        assertThat(send(pay(charge, "{\"method\":\"CASH\"}"), elsewhere).getStatus()).isEqualTo(404);
        assertThat(count("payment")).isZero();
        assertThat(send(pay(charge, "{\"method\":\"CASH\"}"), account(t, Role.COORDINATOR)).getStatus()).isEqualTo(201);
        assertThat(send(pay(UUID.randomUUID(), "{\"method\":\"CASH\"}"), account(t, Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA8: o Administrador da organização A não paga cobrança da B (404) e a B não muda; RLS nas três tabelas com o papel de execução")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var chargeB = charge(b, 100, "OPEN");
        assertThat(send(pay(chargeB, "{\"method\":\"CASH\"}"), account(a, Role.ADMINISTRATOR)).getStatus()).isEqualTo(404);
        assertThat(send(pay(chargeB, "{\"method\":\"CASH\"}"), account(a, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, chargeB)).isEqualTo("OPEN");
        assertThat(count("payment")).isZero();
        var chargeA = charge(a, 100, "OPEN");
        assertThat(send(pay(chargeA, "{\"method\":\"CASH\"}"), account(a, Role.ADMINISTRATOR)).getStatus()).isEqualTo(201);
        assertThat(send(pay(chargeB, "{\"method\":\"CASH\"}"), account(b, Role.ADMINISTRATOR)).getStatus()).isEqualTo(201);

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            for (String table : new String[] {"charge", "payment", "receipt"}) {
                sql.execute("select set_config('app.organization_id', '', true)");
                try (var rows = sql.executeQuery("select count(*) from " + table)) {
                    rows.next();
                    assertThat(rows.getInt(1)).as(table + " sem contexto").isZero();
                }
                sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
                try (var rows = sql.executeQuery("select count(*), count(distinct organization_id), min(organization_id::text) from " + table)) {
                    rows.next();
                    assertThat(rows.getInt(1)).as(table + " da A").isEqualTo(1);
                    assertThat(rows.getString(3)).isEqualTo(a.orgId().toString());
                }
            }
            var savepoint = app.setSavepoint();
            assertThatThrownBy(() -> sql.executeUpdate("insert into charge (id, organization_id, unit_id, student_id, type, due_on, amount_cents) values ('"
                    + UUID.randomUUID() + "', '" + b.orgId() + "', '" + b.unitId() + "', '" + UUID.randomUUID() + "', 'ONE_OFF', now(), 1)")).hasMessageContaining("row-level security");
            app.rollback(savepoint);
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA9: registrar grava payment.registered com ator, perfil, cobrança, valor, meio e número, sem observação; recusas não auditam")
    void audit() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        var charge = charge(t, 15000, "OPEN");
        send(pay(charge, "{\"method\":\"CARD_MACHINE\",\"observation\":\"segredo\",\"amountCents\":1}"), admin);
        send(pay(charge, "{\"method\":\"nope\"}"), admin);
        send(pay(charge, "{\"method\":\"CASH\"}"), account(t, Role.TEACHER));
        send(pay(UUID.randomUUID(), "{\"method\":\"CASH\"}"), admin);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'payment.registered'", Integer.class, t.orgId())).isZero();
        send(pay(charge, "{\"method\":\"CARD_MACHINE\",\"observation\":\"segredo\"}"), admin);
        send(pay(charge, "{\"method\":\"CASH\"}"), admin);
        var rows = adminJdbc().queryForList("select actor_type, actor_id, actor_profile, unit_id, after_data::text from audit_log where organization_id = ? and action = 'payment.registered'", t.orgId());
        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.get("actor_type")).isEqualTo("ACCOUNT");
        assertThat(row.get("actor_id")).isEqualTo(admin.accountId());
        assertThat(row.get("actor_profile")).isEqualTo("ADMINISTRATOR");
        assertThat(row.get("unit_id")).isEqualTo(t.unitId());
        String after = (String) row.get("after_data");
        assertThat(after).contains(charge.toString(), "15000", "CARD_MACHINE", "\"receiptNumber\"").doesNotContain("segredo").doesNotContain("observation");
    }
}
