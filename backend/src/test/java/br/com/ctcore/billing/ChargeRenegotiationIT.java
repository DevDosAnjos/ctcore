package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RN-COB-09 (CA7 renegociar, CA8, CA9, CA10, CA11 renegociar, CA12): renegociar em parcelas mensais, validações, travas e auditoria. */
class ChargeRenegotiationIT extends ChargeActionsBase {
    private JsonNode renegotiate(String body, AccountPrincipal who, int status) throws Exception {
        return ok(send(HttpMethod.POST, "/charges/renegotiate", body, who), status);
    }

    private int bad(String body, AccountPrincipal who, String field) throws Exception {
        var r = send(HttpMethod.POST, "/charges/renegotiate", body, who);
        assertThat(r.getStatus()).as(body).isEqualTo(400);
        assertThat(r.getContentAsString()).as(body).contains(field);
        return r.getStatus();
    }

    @Test
    @DisplayName("CA8, CA12: R$ 100,00 em 3 parcelas a partir de 31/01 dá 3334, 3333 e 3333 em 31/01, 28/02 e 31/03, herdando a modalidade e o mês; audita")
    void installments() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 10000);
        UUID original = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, reference_month, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'MONTHLY', ?, date '2028-10-01', date '2028-10-10', 10000, 10000, 'OPEN')", original, t.orgId(), t.unitId(), s, row);

        JsonNode created = renegotiate(renegotiation(ids(original), null, "2029-01-31", 3, "  Desemprego  "), admin, 201);
        assertThat(created).hasSize(3);
        long[] amounts = {3334, 3333, 3333};
        String[] dues = {"2029-01-31", "2029-02-28", "2029-03-31"};
        for (int i = 0; i < 3; i++) {
            assertThat(created.get(i).get("amountCents").asLong()).isEqualTo(amounts[i]);
            assertThat(created.get(i).get("dueOn").asText()).isEqualTo(dues[i]);
            assertThat(created.get(i).get("type").asText()).isEqualTo("INSTALLMENT");
            assertThat(created.get(i).get("status").asText()).isEqualTo("PENDING");
            assertThat(created.get(i).get("modality")).isNotNull();
            assertThat(created.get(i).get("referenceMonth").asText()).isEqualTo("2028-10-01");
        }
        assertThat(status(original)).isEqualTo("RENEGOTIATED");
        assertThat(adminJdbc().queryForObject("select reason from charge where id = ?", String.class, original)).isEqualTo("Desemprego");
        assertThat(count("select count(*) from charge where origin_charge_id = ? and status = 'OPEN' and enrollment_modality_id = ? and tolerance_days = 0", original, row)).isEqualTo(3);

        assertThat(audits(t, "charge.renegotiated")).isEqualTo(1);
        String trail = adminJdbc().queryForObject("select before_data::text || after_data::text || coalesce(reason, '') from audit_log where organization_id = ? and action = 'charge.renegotiated'",
                String.class, t.orgId());
        assertThat(trail).contains(original.toString(), created.get(0).get("id").asText(), created.get(2).get("id").asText(), "Desemprego", "RENEGOTIATED");
    }

    @Test
    @DisplayName("CA9: duas cobranças do mesmo aluno em 2 parcelas somam 15000; amountCents diferente com as duas dá 400; com uma só vale o novo total")
    void totals() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.COORDINATOR);
        UUID s = student(t);
        String first = LocalDate.now().plusDays(7).toString();
        UUID a = open(t, s, 10000), b = open(t, s, 5000);
        bad(renegotiation(ids(a, b), 14000, first, 2, "x"), admin, "amountCents");
        assertThat(status(a)).isEqualTo("OPEN");
        JsonNode both = renegotiate(renegotiation(ids(a, b), 15000, first, 2, "Junto"), admin, 201);
        assertThat(both.get(0).get("amountCents").asLong() + both.get(1).get("amountCents").asLong()).isEqualTo(15000);
        assertThat(status(a) + status(b)).isEqualTo("RENEGOTIATEDRENEGOTIATED");
        assertThat(count("select count(*) from charge where renegotiation_id = (select renegotiation_id from charge where id = ?)", a)).isEqualTo(4);
        assertThat(count("select count(*) from charge where origin_charge_id in (?, ?)", a, b)).isEqualTo(2);

        UUID c = open(t, s, 10000), d = open(t, s, 2000);
        JsonNode sum = renegotiate(renegotiation(ids(c, d), null, first, 2, "Sem total"), admin, 201);
        assertThat(sum.get(0).get("amountCents").asLong() + sum.get(1).get("amountCents").asLong()).isEqualTo(12000);

        UUID e = open(t, s, 10000);
        JsonNode one = renegotiate(renegotiation(ids(e), 8000, first, 1, "Desconto"), admin, 201);
        assertThat(one).hasSize(1);
        assertThat(one.get(0).get("amountCents").asLong()).isEqualTo(8000);
        assertThat(one.get(0).get("dueOn").asText()).isEqualTo(first);
    }

    @Test
    @DisplayName("CA10, CA6: parcelas, data, total, lista, aluno, unidade e motivo inválidos dão 400 com o campo e nada muda")
    void invalid() throws Exception {
        Tenant t = tenant();
        UUID second = UUID.randomUUID();
        UUID s = student(t), other = student(t);
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", second, t.orgId(), "u" + second.toString().substring(0, 12));
        var admin = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(t.unitId(), second));
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora', 'ACTIVE')", admin.accountId(), "act-" + admin.accountId() + "@example.com");
        UUID a = open(t, s, 10000), otherStudent = open(t, other, 10000), otherUnit = charge(t, second, s, LocalDate.now().plusDays(5), 10000, "OPEN");
        String first = LocalDate.now().plusDays(7).toString();
        bad(renegotiation(ids(a), null, first, 0, "x"), admin, "installments");
        bad(renegotiation(ids(a), null, first, 7, "x"), admin, "installments");
        bad(renegotiation(ids(a), null, "2026-13-45", 2, "x"), admin, "firstDueOn");
        bad(renegotiation(ids(a), null, null, 2, "x"), admin, "firstDueOn");
        bad(renegotiation(ids(a), null, LocalDate.now().minusDays(1).toString(), 2, "x"), admin, "firstDueOn");
        bad(renegotiation(ids(a), 0, first, 1, "x"), admin, "amountCents");
        bad(renegotiation(ids(a), 2, first, 3, "x"), admin, "installments");
        bad(renegotiation("[]", null, first, 2, "x"), admin, "chargeIds");
        bad(renegotiation("[null]", null, first, 2, "x"), admin, "chargeIds");
        bad(renegotiation(ids(a, a), null, first, 2, "x"), admin, "chargeIds");
        bad("{\"firstDueOn\":\"" + first + "\",\"installments\":2,\"reason\":\"x\"}", admin, "chargeIds");
        bad(renegotiation(ids(a, otherStudent), null, first, 2, "x"), admin, "chargeIds");
        bad(renegotiation(ids(a, otherUnit), null, first, 2, "x"), admin, "chargeIds");
        bad(renegotiation(ids(a), null, first, 2, null), admin, "reason");
        bad(renegotiation(ids(a), null, first, 2, " "), admin, "reason");
        bad(renegotiation(ids(a), null, first, 2, "x".repeat(201)), admin, "reason");
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", "{\"chargeIds\":" + ids(a) + ",\"firstDueOn\":\"" + first + "\",\"installments\":2}", admin).getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from charge")).isEqualTo(3);
        assertThat(status(a)).isEqualTo("OPEN");
        assertThat(audits(t, "charge.renegotiated")).isZero();
        assertThat(renegotiate(renegotiation(ids(a), null, LocalDate.now().toString(), 6, "x".repeat(200)), admin, 201)).hasSize(6);
    }

    @Test
    @DisplayName("CA7: paga, cancelada, renegociada, estornada ou em conferência dá 409 e fica intacta, sem auditoria nem parcelas")
    void states() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        String first = LocalDate.now().plusDays(7).toString();
        for (String st : new String[] {"PAID", "CANCELED", "RENEGOTIATED", "REFUNDED", "IN_REVIEW"}) {
            UUID id = charge(t, t.unitId(), s, LocalDate.now(), 1000, st);
            assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(id), null, first, 2, "x"), admin).getStatus()).as(st).isEqualTo(409);
            assertThat(status(id)).isEqualTo(st);
        }
        UUID open = open(t, s, 1000), paid = charge(t, t.unitId(), s, LocalDate.now(), 1000, "PAID");
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(open, paid), null, first, 2, "x"), admin).getStatus()).isEqualTo(409);
        assertThat(status(open)).isEqualTo("OPEN");
        assertThat(count("select count(*) from charge")).isEqualTo(7);
        assertThat(audits(t, "charge.renegotiated")).isZero();
    }

    @Test
    @DisplayName("CA11: duas renegociações simultâneas da mesma cobrança dão um 201 e um 409, sem parcelas duplicadas")
    void race() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID id = open(t, student(t), 9000);
        String body = renegotiation(ids(id), null, LocalDate.now().plusDays(7).toString(), 3, "corrida");
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> send(HttpMethod.POST, "/charges/renegotiate", body, admin).getStatus()));
            Set<Integer> statuses = new HashSet<>();
            for (var f : results) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from charge where origin_charge_id = ?", id)).isEqualTo(3);
        assertThat(audits(t, "charge.renegotiated")).isEqualTo(1);
    }
}
