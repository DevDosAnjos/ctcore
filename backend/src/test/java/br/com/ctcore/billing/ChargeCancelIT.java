package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

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

/** RN-COB-10, RN-COB-08 (CA5, CA6, CA7 cancelar, CA11 cancelar): cancelar cobrança em aberto, motivo, situação, auditoria e corrida. */
class ChargeCancelIT extends ChargeActionsBase {
    @Test
    @DisplayName("CA5: cobrança vencida há 90 dias de R$ 2.000,00 é cancelada com motivo, auditada, e a lista passa a mostrá-la CANCELED")
    void cancels() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        UUID id = charge(t, t.unitId(), s, LocalDate.now().minusDays(90), 200000, "OPEN");

        JsonNode d = ok(send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"  Mudou de cidade  \"}", admin), 200);
        assertThat(d.get("charge").get("status").asText()).isEqualTo("CANCELED");
        assertThat(d.get("reason").asText()).isEqualTo("Mudou de cidade");
        assertThat(d.get("canceledBy").get("id").asText()).isEqualTo(admin.accountId().toString());
        assertThat(d.get("actions").get("cancel").asBoolean()).isFalse();
        var row = adminJdbc().queryForMap("select status, reason, canceled_at, canceled_by_account_id from charge where id = ?", id);
        assertThat(row.get("canceled_at")).isNotNull();
        assertThat(row.get("canceled_by_account_id")).isEqualTo(admin.accountId());
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
        var audit = adminJdbc().queryForMap("select before_data::text b, after_data::text a, unit_id, entity_type from audit_log where organization_id = ? and action = 'charge.canceled'",
                t.orgId());
        assertThat(audit.get("b").toString()).contains("OPEN");
        assertThat(audit.get("a").toString()).contains("CANCELED");
        assertThat(audit.get("unit_id")).isEqualTo(t.unitId());
        assertThat(audit.get("entity_type")).isEqualTo("charge");
        JsonNode list = ok(send(HttpMethod.GET, "/charges?status=CANCELED", null, admin), 200);
        assertThat(list.get("items")).hasSize(1);
        assertThat(list.get("items").get(0).get("status").asText()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("CA6: motivo ausente, em branco ou com 201 caracteres dá 400 no campo reason e nada muda; com 200 é aceito")
    void reason() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.COORDINATOR);
        UUID id = open(t, student(t), 1000);
        for (String body : new String[] {"{}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "x".repeat(201) + "\"}"}) {
            var r = send(HttpMethod.POST, "/charges/" + id + "/cancel", body, admin);
            assertThat(r.getStatus()).as(body).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("reason");
        }
        assertThat(status(id)).isEqualTo("OPEN");
        assertThat(audits(t, "charge.canceled")).isZero();
        ok(send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"" + "x".repeat(200) + "\"}", admin), 200);
        assertThat(status(id)).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("CA7: paga, estornada, renegociada ou em conferência dá 409 e fica intacta; cancelar a cancelada dá 200 sem nova auditoria")
    void states() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID s = student(t);
        for (String st : new String[] {"PAID", "REFUNDED", "RENEGOTIATED", "IN_REVIEW"}) {
            UUID id = charge(t, t.unitId(), s, LocalDate.now(), 1000, st);
            assertThat(send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"x\"}", admin).getStatus()).as(st).isEqualTo(409);
            assertThat(status(id)).isEqualTo(st);
        }
        assertThat(audits(t, "charge.canceled")).isZero();
        UUID id = open(t, s, 1000);
        ok(send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"primeira\"}", admin), 200);
        JsonNode again = ok(send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"segunda\"}", admin), 200);
        assertThat(again.get("reason").asText()).isEqualTo("primeira");
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA11: duas chamadas simultâneas de cancelar dão 200 e 200, com uma só linha de auditoria")
    void race() throws Exception {
        Tenant t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID id = open(t, student(t), 1000);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(pool.submit(() -> send(HttpMethod.POST, "/charges/" + id + "/cancel", "{\"reason\":\"corrida\"}", admin).getStatus()));
            Set<Integer> statuses = new HashSet<>();
            for (var f : results) statuses.add(f.get());
            assertThat(statuses).containsExactly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
        assertThat(status(id)).isEqualTo("CANCELED");
    }
}
