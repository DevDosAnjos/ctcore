package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-08, RN-COB-12: recebimentos do dia no fuso da unidade, por meio e por lançador, permissão e entrada. */
class DailyReceiptsIT extends OrganizationBase {
    private static final AtomicInteger NUMBER = new AtomicInteger();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like 'day-%@example.com'");
    }

    /** 2026-10-20 12:00 em São Paulo. */
    private DailyReceipts service() {
        return new DailyReceipts(jdbc, transactions, permissions, Clock.fixed(Instant.parse("2026-10-20T15:00:00Z"), ZoneOffset.UTC));
    }

    private DailyReceipts.Day day(AccountPrincipal who, UUID unit, String date) {
        try (var ignored = OrganizationContext.open(who)) {
            return service().day(who, unit, date);
        }
    }

    private AccountPrincipal account(Tenant t, Role role, String name) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, ?, 'ACTIVE')", p.accountId(), "day-" + p.accountId() + "@example.com", name);
        return p;
    }

    /** Pagamento (confirmado ou não) com recibo, direto no banco. */
    static void pay(JdbcTemplate db, Tenant t, String student, long amount, String method, String origin, String paidAt, UUID by, String status) {
        UUID charge = BillingFixtures.oneOff(db, t.orgId(), t.unitId(), BillingFixtures.student(db, t.orgId(), t.unitId(), student), amount, "PAID");
        UUID payment = UUID.randomUUID();
        db.update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at, registered_by_account_id, status) "
                + "select ?, organization_id, id, student_id, unit_id, amount_cents, ?, ?, ?::timestamptz, ?, ? from charge where id = ?", payment, method, origin, paidAt, by, status, charge);
        StringBuilder code = new StringBuilder();
        long n = UUID.randomUUID().getLeastSignificantBits();
        for (int i = 0; i < 16; i++) code.append(ALPHABET.charAt((int) Math.floorMod(n >>> (i * 3), 32L)));
        db.update("insert into receipt (id, organization_id, payment_id, unit_id, number, code) values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), t.orgId(), payment, t.unitId(),
                NUMBER.incrementAndGet(), code.toString());
    }

    @Test
    @DisplayName("CA1, CA4: totais por meio e por lançador, e a lista do mais recente ao mais antigo com hora, aluno, referência, meio, lançador, valor e recibo")
    void totalsAndList() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR, "Ana Admin");
        var coord = account(t, Role.COORDINATOR, "");
        pay(adminJdbc(), t, "Aluno A", 10000, "CASH", "MANAGEMENT", "2026-10-20T12:00:00Z", admin.accountId(), "CONFIRMED");
        pay(adminJdbc(), t, "Aluno B", 5000, "CASH", "MANAGEMENT", "2026-10-20T13:00:00Z", admin.accountId(), "CONFIRMED");
        pay(adminJdbc(), t, "Aluno C", 20000, "PIX_KEY", "MANAGEMENT", "2026-10-20T14:00:00Z", coord.accountId(), "CONFIRMED");
        String coordEmail = adminJdbc().queryForObject("select email from account where id = ?", String.class, coord.accountId());

        var d = day(admin, t.unitId(), null);

        assertThat(d.date()).isEqualTo(LocalDate.parse("2026-10-20"));
        assertThat(d.totalCents()).isEqualTo(35000);
        assertThat(d.count()).isEqualTo(3);
        assertThat(d.byMethod()).extracting(DailyReceipts.ByMethod::method, DailyReceipts.ByMethod::count, DailyReceipts.ByMethod::totalCents)
                .containsExactlyInAnyOrder(tuple("CASH", 2, 15000L), tuple("PIX_KEY", 1, 20000L));
        assertThat(d.byRegistrar()).extracting(r -> r.registeredBy().name(), DailyReceipts.ByRegistrar::count, DailyReceipts.ByRegistrar::totalCents)
                .containsExactlyInAnyOrder(tuple("Ana Admin", 2, 15000L), tuple(coordEmail, 1, 20000L));
        assertThat(d.payments()).extracting(l -> l.student().name()).containsExactly("Aluno C", "Aluno B", "Aluno A");
        var first = d.payments().get(0);
        assertThat(first.reference()).isEqualTo("Kimono");
        assertThat(first.method()).isEqualTo("PIX_KEY");
        assertThat(first.amountCents()).isEqualTo(20000);
        assertThat(first.receiptNumber()).isPositive();
        assertThat(first.paidAt().toInstant()).isEqualTo(Instant.parse("2026-10-20T14:00:00Z"));
        assertThat(first.registeredBy().id()).isEqualTo(coord.accountId());
    }

    @Test
    @DisplayName("CA4: mensalidade mostra modalidade e mês")
    void monthlyReference() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR, "Ana Admin");
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Mensalista");
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), student, 10, 20000);
        UUID charge = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student, row, LocalDate.parse("2026-11-01"), null, 20000, "PAID");
        UUID payment = UUID.randomUUID();
        adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at, registered_by_account_id) "
                + "select ?, organization_id, id, student_id, unit_id, amount_cents, 'CASH', 'MANAGEMENT', '2026-10-20T13:00:00Z', ? from charge where id = ?", payment, admin.accountId(), charge);
        adminJdbc().update("insert into receipt (id, organization_id, payment_id, unit_id, number, code) values (?, ?, ?, ?, 1, 'ABCDEFGH23456789')", UUID.randomUUID(), t.orgId(), payment,
                t.unitId());

        var d = day(admin, t.unitId(), "2026-10-20");

        assertThat(d.payments().get(0).reference()).matches("[^·]+ · nov/2026");
    }

    @Test
    @DisplayName("CA2: estornado, ontem e amanhã ficam fora; 23h30 locais conta no dia mesmo caindo no dia seguinte em UTC")
    void localDay() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR, "Ana Admin");
        pay(adminJdbc(), t, "Estornado", 100, "CASH", "MANAGEMENT", "2026-10-20T12:00:00Z", admin.accountId(), "REFUNDED");
        pay(adminJdbc(), t, "Ontem", 200, "CASH", "MANAGEMENT", "2026-10-19T12:00:00Z", admin.accountId(), "CONFIRMED");
        pay(adminJdbc(), t, "Amanha", 400, "CASH", "MANAGEMENT", "2026-10-21T12:00:00Z", admin.accountId(), "CONFIRMED");
        pay(adminJdbc(), t, "Noite", 800, "CASH", "MANAGEMENT", "2026-10-21T02:30:00Z", admin.accountId(), "CONFIRMED");
        pay(adminJdbc(), t, "Madrugada", 1600, "CASH", "MANAGEMENT", "2026-10-20T02:59:00Z", admin.accountId(), "CONFIRMED");

        assertThat(day(admin, t.unitId(), null).payments()).extracting(l -> l.student().name()).containsExactly("Noite");
        assertThat(day(admin, t.unitId(), "2026-10-19").payments()).extracting(l -> l.student().name()).containsExactlyInAnyOrder("Ontem", "Madrugada");
    }

    @Test
    @DisplayName("CA3: pagamento do portal sem lançador conta no total e no meio e vira linha de lançador nula")
    void withoutRegistrar() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR, "Ana Admin");
        pay(adminJdbc(), t, "Online", 3000, "PIX_MP", "PORTAL", "2026-10-20T13:00:00Z", null, "CONFIRMED");
        pay(adminJdbc(), t, "Balcao", 1000, "CASH", "MANAGEMENT", "2026-10-20T14:00:00Z", admin.accountId(), "CONFIRMED");

        var d = day(admin, t.unitId(), null);

        assertThat(d.totalCents()).isEqualTo(4000);
        assertThat(d.byMethod()).extracting(DailyReceipts.ByMethod::method).containsExactlyInAnyOrder("PIX_MP", "CASH");
        assertThat(d.byRegistrar()).extracting(DailyReceipts.ByRegistrar::registeredBy, DailyReceipts.ByRegistrar::totalCents)
                .containsExactlyInAnyOrder(tuple(null, 3000L), tuple(new DailyReceipts.Person(admin.accountId(), "Ana Admin"), 1000L));
        assertThat(d.payments().stream().filter(l -> l.registeredBy() == null)).hasSize(1);
    }

    @Test
    @DisplayName("CA5, CA6: entrada inválida é 400 com o campo; perfis sem acesso 403; sem sessão 401; unidade de fora vem vazia; data em branco usa hoje")
    void httpRules() throws Exception {
        var t = tenant();
        var other = tenant();
        var admin = account(t, Role.ADMINISTRATOR, "Ana Admin");
        pay(adminJdbc(), t, "Hoje", 500, "CASH", "MANAGEMENT", Instant.now().toString(), admin.accountId(), "CONFIRMED");
        String url = "/api/v1/billing/daily-receipts?unitId=" + t.unitId();

        for (String bad : new String[] {"/api/v1/billing/daily-receipts", "/api/v1/billing/daily-receipts?unitId=x", url + "&date=31/12", url + "&date=2999-01-01"}) {
            var r = call(MockMvcRequestBuilders.get(bad), admin).getResponse();
            assertThat(r.getStatus()).as(bad).isEqualTo(400);
            assertThat(r.getContentAsString()).contains(bad.contains("date=") ? "date" : "unitId");
        }
        assertThat(call(MockMvcRequestBuilders.get(url), null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(MockMvcRequestBuilders.get(url), account(t, role, "X")).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        JsonNode ok = json.readTree(call(MockMvcRequestBuilders.get(url + "&date="), admin).getResponse().getContentAsString());
        assertThat(ok.get("count").asInt()).isEqualTo(1);
        assertThat(call(MockMvcRequestBuilders.get(url), account(t, Role.COORDINATOR, "C")).getResponse().getStatus()).isEqualTo(200);

        var foreign = json.readTree(call(MockMvcRequestBuilders.get("/api/v1/billing/daily-receipts?unitId=" + other.unitId() + "&date=2026-01-01"), admin).getResponse().getContentAsString());
        assertThat(foreign.get("count").asInt()).isZero();
        assertThat(foreign.get("date").asText()).isEqualTo("2026-01-01");
        assertThat(day(admin, other.unitId(), null).date()).isEqualTo(LocalDate.parse("2026-10-20"));
    }
}
