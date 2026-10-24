package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RF-COB-06 (#79): lista de cobranças em atraso. */
class OverdueChargesIT extends ChargeActionsBase {
    private UUID named(Tenant t, String name) {
        return BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), name, "1990-01-01");
    }

    private UUID overdue(Tenant t, UUID student, int daysAgo, long amount) {
        return charge(t, t.unitId(), student, today().minusDays(daysAgo), amount, "OPEN");
    }

    private JsonNode items(String query, br.com.ctcore.identity.AccountPrincipal who) throws Exception {
        return ok(send(HttpMethod.GET, "/charges/overdue" + query, null, who), 200).get("items");
    }

    @Test
    @DisplayName("CA13: só as 3 em atraso, com dias de atraso, valor gravado e pagador do menor; ordem padrão por dias de atraso decrescente")
    void onlyOverdue() throws Exception {
        Tenant t = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID adult = named(t, "adulto"), minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Menor Sintetico", "2018-01-01");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        UUID a = overdue(t, adult, 3, 10000), b = overdue(t, minor, 12, 25000), c = overdue(t, adult, 1, 5000);
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, b);
        charge(t, t.unitId(), adult, today().plusDays(2), 100, "OPEN");
        for (String status : new String[] {"PAID", "CANCELED", "IN_REVIEW"}) charge(t, t.unitId(), adult, today().minusDays(5), 100, status);

        JsonNode list = ok(send(HttpMethod.GET, "/charges/overdue", null, coordinator), 200);
        assertThat(list.get("total").asInt()).isEqualTo(3);
        JsonNode items = list.get("items");
        assertThat(items).hasSize(3);
        assertThat(items.get(0).get("id").asText()).isEqualTo(b.toString());
        assertThat(items.get(1).get("id").asText()).isEqualTo(a.toString());
        assertThat(items.get(2).get("id").asText()).isEqualTo(c.toString());
        assertThat(items.get(0).get("daysLate").asInt()).isEqualTo(12);
        assertThat(items.get(0).get("amountCents").asLong()).isEqualTo(25000);
        assertThat(items.get(0).get("payer").get("name").asText()).isEqualTo("Responsável");
        assertThat(items.get(0).get("payer").get("phone").asText()).isEqualTo("11987654321");
        assertThat(items.get(0).get("student").get("name").asText()).isEqualTo("Menor Sintetico");
        assertThat(items.get(0).get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(items.get(1).get("payer").get("name").asText()).isEqualTo("adulto");
        assertThat(items.get(1).get("payer").get("phone").isNull()).isTrue();
        assertThat(items.get(0).get("lastReminder").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA14: dias até o bloqueio: 5 aos 25 dias, 0 aos 40, nulo para avulsa e para unidade sem conta conectada")
    void daysToBlock() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = named(t, "bloqueio");
        UUID near = overdue(t, s, 25, 100), far = overdue(t, s, 40, 200), oneOff = overdue(t, s, 30, 300);
        adminJdbc().update("update charge set type = 'MONTHLY' where id in (?, ?)", near, far);
        adminJdbc().update("update unit_settings set payment_account_reference = 'conta-sintetica' where unit_id = ?", t.unitId());
        JsonNode items = items("?sort=amount,asc", admin);
        assertThat(items.get(0).get("daysToBlock").asInt()).isEqualTo(5);
        assertThat(items.get(1).get("daysToBlock").asInt()).isZero();
        assertThat(items.get(2).get("daysToBlock").isNull()).as(oneOff.toString()).isTrue();
        adminJdbc().update("update unit_settings set payment_account_reference = null where unit_id = ?", t.unitId());
        assertThat(items("?sort=amount,asc", admin).get(0).get("daysToBlock").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA15: lastReminder traz o mais recente enviado e ignora os registrados como pulados")
    void lastReminder() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID with = overdue(t, named(t, "comlembrete"), 4, 100), without = overdue(t, named(t, "semlembrete"), 4, 200);
        String insert = "insert into charge_reminder (id, organization_id, charge_id, kind, channel, status, reason, created_at) values (?, ?, ?, 'MANUAL', ?, ?, ?, ?::timestamptz)";
        adminJdbc().update("insert into charge_reminder (id, organization_id, charge_id, kind, offset_days, channel, status, created_at) values (?, ?, ?, 'SCHEDULED', 3, 'EMAIL', 'SENT', '2026-10-01T12:00:00Z')",
                UUID.randomUUID(), t.orgId(), with);
        adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), with, "SMS", "SENT", null, "2026-10-02T12:00:00Z");
        adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), with, "SMS", "SKIPPED", "SMS_LIMIT", "2026-10-03T12:00:00Z");
        JsonNode items = items("?sort=amount,asc", admin);
        assertThat(items.get(0).get("id").asText()).isEqualTo(with.toString());
        assertThat(items.get(0).get("lastReminder").get("channel").asText()).isEqualTo("SMS");
        assertThat(items.get(0).get("lastReminder").get("at").asText()).startsWith("2026-10-02");
        assertThat(items.get(1).get("id").asText()).isEqualTo(without.toString());
        assertThat(items.get(1).get("lastReminder").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA16: filtros por unidade e nome sem acento nem caixa; unidade de outra organização dá 404; ordem e página; sort inválido dá 400")
    void filtersAndOrder() throws Exception {
        Tenant t = tenant(), other = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID anna = named(t, "ana paula"), bruno = named(t, "bruno");
        UUID second = adminJdbc().queryForObject("select gen_random_uuid()", UUID.class);
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Segunda', ?)", second, t.orgId(), "seg-" + second);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), second);
        UUID a = overdue(t, anna, 10, 300), b = overdue(t, bruno, 20, 100);
        UUID c = charge(t, second, bruno, today().minusDays(15), 200, "OPEN");

        assertThat(items("?q=ANÁ", coordinator)).hasSize(1).first().satisfies(i -> assertThat(i.get("id").asText()).isEqualTo(a.toString()));
        assertThat(items("?unitId=" + second, coordinator)).hasSize(1).first().satisfies(i -> assertThat(i.get("id").asText()).isEqualTo(c.toString()));
        assertThat(send(HttpMethod.GET, "/charges/overdue?unitId=" + other.unitId(), null, coordinator).getStatus()).isEqualTo(404);
        assertThat(items("?sort=amount,asc", coordinator)).extracting(i -> i.get("id").asText()).containsExactly(b.toString(), c.toString(), a.toString());
        assertThat(items("?sort=student,asc", coordinator).get(0).get("id").asText()).isEqualTo(a.toString());
        assertThat(items("?sort=daysLate,asc", coordinator).get(0).get("id").asText()).isEqualTo(a.toString());
        JsonNode paged = ok(send(HttpMethod.GET, "/charges/overdue?size=25&page=2", null, coordinator), 200);
        assertThat(paged.get("items")).isEmpty();
        assertThat(paged.get("total").asInt()).isEqualTo(3);
        assertThat(send(HttpMethod.GET, "/charges/overdue?sort=dueOn,asc", null, coordinator).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Administrador só vê as unidades dele; sem unidade no alcance a lista é vazia")
    void administratorReach() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Segunda', ?)", second, t.orgId(), "seg-" + second);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), second);
        UUID s = named(t, "alcance");
        UUID mine = overdue(t, s, 5, 100);
        charge(t, second, s, today().minusDays(6), 200, "OPEN");
        assertThat(items("", admin)).extracting(i -> i.get("id").asText()).containsExactly(mine.toString());
        assertThat(items("?unitId=" + second, admin)).isEmpty();
        var nobody = new br.com.ctcore.identity.AccountPrincipal(admin.accountId(), admin.sessionId(), admin.activeLinkId(), t.orgId(), Role.ADMINISTRATOR, java.util.Set.of());
        assertThat(items("", nobody)).isEmpty();
    }
}
