package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RF-COB-02 (CA1, CA2, CA3 leitura): o painel da cobrança e o filtro por aluno de GET /charges. */
class ChargePanelIT extends ChargeActionsBase {
    @Test
    @DisplayName("CA1: mensalidade paga em dinheiro traz composição, pagamento com quem lançou, recibo e lembretes vazios")
    void paid() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 15000);
        UUID plan = adminJdbc().queryForObject("select plan_id from enrollment_modality where id = ?", UUID.class, row);
        UUID benefit = BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), s, row, "PERCENT", 10, "2030-01-01", "ACTIVE");
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, plan_id, benefit_id, reference_month, due_on, "
                + "plan_cents, benefit_cents, amount_cents, status) values (?, ?, ?, ?, 'MONTHLY', ?, ?, ?, date '2026-10-01', date '2026-10-10', 15000, 1500, 13500, 'OPEN')",
                id, t.orgId(), t.unitId(), s, row, plan, benefit);
        assertThat(ok(send(HttpMethod.POST, "/charges/" + id + "/payments", "{\"method\":\"CASH\",\"observation\":\"balcão\"}", admin), 201).get("receipt")).isNotNull();

        JsonNode d = ok(send(HttpMethod.GET, "/charges/" + id, null, admin), 200);
        assertThat(d.get("charge").get("status").asText()).isIn("PAID", "PAID_LATE");
        assertThat(d.get("composition").get("planCents").asLong()).isEqualTo(15000);
        assertThat(d.get("composition").get("benefitCents").asLong()).isEqualTo(1500);
        assertThat(d.get("composition").get("benefitType").asText()).isEqualTo("PERCENT");
        assertThat(d.get("composition").get("amountCents").asLong()).isEqualTo(13500);
        assertThat(d.get("composition").get("plan").get("id").asText()).isEqualTo(plan.toString());
        assertThat(d.get("payer").get("kind").asText()).isEqualTo("STUDENT");
        assertThat(d.get("payments")).hasSize(1);
        JsonNode p = d.get("payments").get(0);
        assertThat(p.get("method").asText()).isEqualTo("CASH");
        assertThat(p.get("registeredBy").get("name").asText()).isEqualTo("Gestora Sintética");
        assertThat(p.get("receipt").get("number").asInt()).isEqualTo(1);
        assertThat(p.get("receipt").get("code").asText()).hasSize(16);
        assertThat(d.get("reminders")).isEmpty();
        assertThat(d.get("origin").isNull()).isTrue();
        assertThat(d.get("replacedBy")).isEmpty();
        assertThat(d.get("actions").get("cancel").asBoolean()).isFalse();
        assertThat(d.get("actions").get("registerPayment").asBoolean()).isFalse();
        assertThat(d.get("canceledBy").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA2: parcela traz a origem; a original renegociada traz as parcelas e o motivo; responsável financeiro aparece como pagador")
    void origin() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), s, true);
        UUID original = open(t, s, 10000);
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, original);
        String first = LocalDate.now().plusDays(3).toString();
        JsonNode created = ok(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(original), null, first, 2, "Dificuldade"), admin), 201);
        UUID parcela = UUID.fromString(created.get(0).get("id").asText());

        JsonNode o = ok(send(HttpMethod.GET, "/charges/" + original, null, admin), 200);
        assertThat(o.get("charge").get("status").asText()).isEqualTo("RENEGOTIATED");
        assertThat(o.get("reason").asText()).isEqualTo("Dificuldade");
        assertThat(o.get("replacedBy")).hasSize(2);
        assertThat(o.get("replacedBy").get(0).get("status").asText()).isEqualTo("OPEN");
        assertThat(o.get("payer").get("kind").asText()).isEqualTo("GUARDIAN");
        assertThat(o.get("payer").get("name").asText()).isEqualTo("Responsável");
        JsonNode p = ok(send(HttpMethod.GET, "/charges/" + parcela, null, admin), 200);
        assertThat(p.get("origin").get("id").asText()).isEqualTo(original.toString());
        assertThat(p.get("charge").get("type").asText()).isEqualTo("INSTALLMENT");
        assertThat(p.get("replacedBy")).isEmpty();
        assertThat(p.get("actions").get("renegotiate").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Cancelada traz quem cancelou, quando e o motivo; GET /charges filtra por aluno e o aluno inexistente dá 404")
    void canceledAndFilter() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t), other = student(t);
        UUID id = open(t, s, 5000);
        open(t, other, 7000);
        send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"Mudou\"}", admin);
        JsonNode d = ok(send(HttpMethod.GET, "/charges/" + id, null, admin), 200);
        assertThat(d.get("canceledBy").get("name").asText()).isEqualTo("Gestora Sintética");
        assertThat(d.get("canceledAt").isNull()).isFalse();
        assertThat(d.get("reason").asText()).isEqualTo("Mudou");

        JsonNode list = ok(send(HttpMethod.GET, "/charges?studentId=" + s, null, admin), 200);
        assertThat(list.get("items")).hasSize(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(id.toString());
        assertThat(send(HttpMethod.GET, "/charges?studentId=" + UUID.randomUUID(), null, admin).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/charges/" + UUID.randomUUID(), null, admin).getStatus()).isEqualTo(404);
    }
}
