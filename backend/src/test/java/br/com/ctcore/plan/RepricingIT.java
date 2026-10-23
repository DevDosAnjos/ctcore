package br.com.ctcore.plan;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-PLA-04, RN-PLA-05: contagens reais do plano, reajuste das cobranças em aberto e diferença das pagas futuras, tudo ou nada e auditoria. */
class RepricingIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from plan_value_history");
        BillingFixtures.cleanAll(adminJdbc());
    }

    /** Plano de R$ 200,00 com três alunos ativos nele. */
    private record Setup(Tenant t, UUID plan, UUID[] rows) {}

    private Setup setup() {
        var t = tenant();
        UUID[] rows = new UUID[3];
        for (int i = 0; i < 3; i++) {
            rows[i] = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01"), 10, 20000);
        }
        UUID plan = adminJdbc().queryForObject("select plan_id from enrollment_modality where id = ?", UUID.class, rows[0]);
        adminJdbc().update("update enrollment_modality set plan_id = ? where id in (?, ?)", plan, rows[1], rows[2]);
        adminJdbc().update("update plan set value_cents = 20000 where id = ?", plan);
        return new Setup(t, plan, rows);
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private UUID charge(Setup s, UUID row, LocalDate due, LocalDate ref, long amount, String status, UUID benefit, long benefitCents) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, plan_id, benefit_id, reference_month, due_on, "
                + "plan_cents, benefit_cents, amount_cents, status) values (?, ?, ?, ?, 'MONTHLY', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, s.t().orgId(), s.t().unitId(), student(row), row, s.plan(), benefit, ref, due, amount + benefitCents, benefitCents, amount, status);
        return id;
    }

    private static LocalDate ref(int monthsAhead) {
        return LocalDate.now().withDayOfMonth(1).plusMonths(monthsAhead);
    }

    private MockHttpServletResponse call(Setup s, String method, String path, Object body) throws Exception {
        var req = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api/v1/plans" + path);
        if (body != null) req = req.contentType("application/json").content(json.writeValueAsString(body));
        return call(req, coordinator(s)).getResponse();
    }

    private Map<String, Object> reprice(Setup s, long value, String unpaid, boolean diff) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("unitId", s.t().unitId());
        b.put("modalityId", adminJdbc().queryForObject("select modality_id from plan where id = ?", UUID.class, s.plan()));
        b.put("name", adminJdbc().queryForObject("select name from plan where id = ?", String.class, s.plan()));
        b.put("valueCents", value);
        b.put("weeklyFrequency", 2);
        b.put("validOtherUnits", false);
        b.put("unpaidCharges", unpaid);
        b.put("paidFutureDifference", diff);
        return b;
    }

    private br.com.ctcore.identity.AccountPrincipal coordinator(Setup s) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, "gen-" + account + "@example.com");
        return new br.com.ctcore.identity.AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), s.t().orgId(), Role.COORDINATOR, java.util.Set.of(s.t().unitId()));
    }

    private long amount(UUID charge) {
        return adminJdbc().queryForObject("select amount_cents from charge where id = ?", Long.class, charge);
    }

    private int audits(Setup s, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, s.t().orgId(), action);
    }

    private JsonNode json(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    @Test
    @DisplayName("CA24: impacto e resumo contam alunos ativos no plano, abertas futuras e pagas de mês futuro; vencida e encerrada não contam")
    void counts() throws Exception {
        var s = setup();
        LocalDate future = LocalDate.now().plusDays(20);
        charge(s, s.rows()[0], future, ref(1), 20000, "OPEN", null, 0);
        charge(s, s.rows()[1], future, ref(2), 20000, "OPEN", null, 0);
        charge(s, s.rows()[1], LocalDate.now().minusDays(40), ref(-1), 20000, "OPEN", null, 0);
        charge(s, s.rows()[2], future, ref(3), 20000, "PAID", null, 0);
        charge(s, s.rows()[2], LocalDate.now(), ref(0), 20000, "PAID", null, 0);
        UUID ended = BillingFixtures.modality(adminJdbc(), s.t().orgId(), s.t().unitId(), BillingFixtures.student(adminJdbc(), s.t().orgId(), s.t().unitId(), "Ex", "1990-01-01"), 10, 1);
        adminJdbc().update("update enrollment_modality set plan_id = ?, status = 'ENDED', ended_at = now() where id = ?", s.plan(), ended);

        var impact = json(call(s, "GET", "/" + s.plan() + "/value-impact?valueCents=25000", null));
        assertThat(impact.get("studentsAffected").asInt()).isEqualTo(3);
        assertThat(impact.get("unpaidCharges").asInt()).isEqualTo(2);
        assertThat(impact.get("paidFutureCharges").asInt()).isEqualTo(1);
        assertThat(json(call(s, "GET", "/summary", null)).get("plans").get("studentsUsing").asInt()).isEqualTo(3);
        assertThat(json(call(s, "GET", "", null)).get("items").findValues("studentsUsing").stream().mapToInt(JsonNode::asInt).sum()).isEqualTo(3);
    }

    @Test
    @DisplayName("CA25: ADJUST recalcula as abertas futuras com o benefício delas; KEEP, a paga e a vencida não mudam")
    void adjust() throws Exception {
        var s = setup();
        UUID benefit = BillingFixtures.benefit(adminJdbc(), s.t().orgId(), s.t().unitId(), student(s.rows()[1]), s.rows()[1], "PERCENT", 25, null, "ACTIVE");
        LocalDate future = LocalDate.now().plusDays(20);
        UUID plain = charge(s, s.rows()[0], future, ref(1), 20000, "OPEN", null, 0);
        UUID discounted = charge(s, s.rows()[1], future, ref(1), 15000, "OPEN", benefit, 5000);
        UUID overdue = charge(s, s.rows()[2], LocalDate.now().minusDays(40), ref(-1), 20000, "OPEN", null, 0);
        UUID paid = charge(s, s.rows()[2], future, ref(0), 20000, "PAID", null, 0);

        assertThat(call(s, "PUT", "/" + s.plan(), reprice(s, 22000, "KEEP", false)).getStatus()).isEqualTo(200);
        assertThat(amount(plain)).isEqualTo(20000);
        assertThat(audits(s, "plan.repriced")).isEqualTo(1);

        var r = call(s, "PUT", "/" + s.plan(), reprice(s, 25000, "ADJUST", false));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(amount(plain)).isEqualTo(25000);
        assertThat(amount(discounted)).isEqualTo(18750);
        var composition = adminJdbc().queryForMap("select plan_cents, benefit_cents from charge where id = ?", discounted);
        assertThat(composition.get("plan_cents")).isEqualTo(25000L);
        assertThat(composition.get("benefit_cents")).isEqualTo(6250L);
        assertThat(amount(overdue)).isEqualTo(20000);
        assertThat(amount(paid)).isEqualTo(20000);
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.repriced' order by occurred_at desc limit 1",
                String.class, s.t().orgId());
        assertThat(after).contains(plain.toString(), discounted.toString()).doesNotContain(overdue.toString());

        assertThat(call(s, "PUT", "/" + s.plan(), reprice(s, 25000, "ADJUST", false)).getStatus()).isEqualTo(200);
        assertThat(audits(s, "plan.repriced")).isEqualTo(2); // sem mudar o valor não audita de novo
    }

    @Test
    @DisplayName("CA26: diferença só da parte que falta, uma vez por reajuste, vencendo em 7 dias; redução e padrão falso não geram")
    void difference() throws Exception {
        var s = setup();
        LocalDate ref = ref(2);
        UUID paid = charge(s, s.rows()[0], LocalDate.now().plusDays(20), ref, 20000, "PAID", null, 0);
        charge(s, s.rows()[1], LocalDate.now().plusDays(20), ref(0), 20000, "PAID", null, 0);

        call(s, "PUT", "/" + s.plan(), reprice(s, 25000, "KEEP", false));
        assertThat(adminJdbc().queryForObject("select count(*) from charge where type = 'READJUSTMENT_DIFF'", Integer.class)).isZero();

        var rr = call(s, "PUT", "/" + s.plan(), reprice(s, 26000, "KEEP", true)); assertThat(rr.getStatus()).as(rr.getContentAsString()).isEqualTo(200);
        var diff = adminJdbc().queryForMap("select * from charge where type = 'READJUSTMENT_DIFF'");
        assertThat(diff.get("amount_cents")).isEqualTo(6000L);
        assertThat(diff.get("origin_charge_id")).isEqualTo(paid);
        assertThat(diff.get("due_on").toString()).isEqualTo(adminJdbc().queryForObject("select ((now() at time zone timezone)::date + 7)::text from unit where id = ?", String.class, s.t().unitId()));
        assertThat(diff.get("description")).asString().startsWith("Diferença de reajuste, referência ").endsWith("/" + ref.getYear());
        assertThat(diff.get("status")).isEqualTo("OPEN");

        call(s, "PUT", "/" + s.plan(), reprice(s, 27000, "KEEP", true));
        assertThat(adminJdbc().queryForList("select amount_cents from charge where type = 'READJUSTMENT_DIFF' order by amount_cents", Long.class)).containsExactly(1000L, 6000L);

        call(s, "PUT", "/" + s.plan(), reprice(s, 19000, "KEEP", true));
        assertThat(adminJdbc().queryForObject("select count(*) from charge where type = 'READJUSTMENT_DIFF'", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA27: falha na terceira diferença desfaz valor, ajustes e diferenças; auditoria com as escolhas")
    void allOrNothing() throws Exception {
        var s = setup();
        UUID open = charge(s, s.rows()[0], LocalDate.now().plusDays(20), ref(1), 20000, "OPEN", null, 0);
        for (int i = 1; i <= 3; i++) charge(s, s.rows()[i - 1], LocalDate.now().plusDays(20), ref(1 + i), 20000, "PAID", null, 0);
        var admin = adminJdbc();
        admin.execute("create or replace function fail_third() returns trigger language plpgsql as $f$ begin if (select count(*) from charge where type = 'READJUSTMENT_DIFF') >= 2 "
                + "then raise exception 'forced'; end if; return new; end $f$");
        admin.execute("create trigger fail_third before insert on charge for each row when (new.type = 'READJUSTMENT_DIFF') execute function fail_third()");
        try {
            assertThat(call(s, "PUT", "/" + s.plan(), reprice(s, 25000, "ADJUST", true)).getStatus()).isEqualTo(500);
        } finally {
            admin.execute("drop trigger fail_third on charge");
            admin.execute("drop function fail_third()");
        }
        assertThat(adminJdbc().queryForObject("select value_cents from plan where id = ?", Long.class, s.plan())).isEqualTo(20000L);
        assertThat(amount(open)).isEqualTo(20000);
        assertThat(adminJdbc().queryForObject("select count(*) from charge where type = 'READJUSTMENT_DIFF'", Integer.class)).isZero();

        assertThat(call(s, "PUT", "/" + s.plan(), reprice(s, 25000, "ADJUST", true)).getStatus()).isEqualTo(200);
        String updated = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.updated' order by occurred_at desc limit 1",
                String.class, s.t().orgId());
        assertThat(updated).contains("ADJUST", "\"adjustedCharges\": 1", "\"createdCharges\": 3");
        assertThat(audits(s, "plan.repriced")).isEqualTo(1);
    }
}
