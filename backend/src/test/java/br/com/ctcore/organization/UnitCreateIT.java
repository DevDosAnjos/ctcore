package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.integration.Outbox;
import br.com.ctcore.isolation.Scopes;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-ORG-03: criação da unidade pela rota, com o banco real. */
class UnitCreateIT extends UnitBase {
    @Autowired UnitShortNames shortNames;
    @Autowired Outbox outbox;

    /** Coordenador com conta de verdade, porque o convite grava quem o enviou. */
    private AccountPrincipal coordinator(Tenant t) {
        var principal = who(t, Role.COORDINATOR);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Coord', 'ACTIVE')", principal.accountId(),
                principal.accountId() + "@synthetic.example");
        return principal;
    }

    private static String unique() {
        return "Academia " + UUID.randomUUID();
    }

    @Test
    @DisplayName("CA1 / CA3 / CA7: cria com os campos do contrato, na organização do chamador, com unit_settings e fuso padrão; aparece na lista")
    void creates() throws Exception {
        var a = tenant();
        var b = tenant();
        String name = unique();
        var response = call(create("{\"name\":\"" + name + "\",\"organizationId\":\"" + b.orgId() + "\"}"), who(a, Role.COORDINATOR)).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        var result = body(response);
        var unit = result.path("unit");
        assertThat(unit.path("name").asText()).isEqualTo(name);
        assertThat(unit.path("active").asBoolean()).isTrue();
        assertThat(unit.path("shortNameAdjusted").asBoolean()).isFalse();
        assertThat(unit.path("administrators")).isEmpty();
        assertThat(unit.path("activeStudents").asInt()).isZero();
        assertThat(unit.path("shortName").asText()).isNotBlank();
        assertThat(result.path("invite").asText()).isEqualTo("NONE");
        UUID id = UUID.fromString(unit.path("id").asText());
        assertThat(adminJdbc().queryForObject("select organization_id from unit where id = ?", UUID.class, id)).isEqualTo(a.orgId());
        assertThat(unitCount(b)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_settings where unit_id = ? and organization_id = ?", Integer.class, id, a.orgId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select timezone from unit where id = ?", String.class, id)).isEqualTo("America/Sao_Paulo");
        var listed = body(call(list(), coordinator(a)).getResponse());
        assertThat(listed.findValuesAsText("id")).contains(id.toString());
    }

    @Test
    @DisplayName("CA4: entrada inválida responde 400 com o campo e nada é criado nem auditado")
    void invalid() throws Exception {
        var a = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        for (var entry : List.of(
                List.of("name", "{\"name\":\"   \"}"),
                List.of("name", "{\"name\":\"" + "x".repeat(121) + "\"}"),
                List.of("email", "{\"name\":\"A\",\"email\":\"ruim\"}"),
                List.of("postalCode", "{\"name\":\"A\",\"postalCode\":\"12\"}"),
                List.of("latitude", "{\"name\":\"A\",\"latitude\":91,\"longitude\":0}"),
                List.of("longitude", "{\"name\":\"A\",\"latitude\":0,\"longitude\":181}"),
                List.of("administrator.email", "{\"name\":\"A\",\"administrator\":{\"name\":\"Ana\"}}"),
                List.of("administrator.name", "{\"name\":\"A\",\"administrator\":{\"email\":\"a@b.com\"}}"))) {
            var response = call(create(entry.get(1)), coordinator).getResponse();
            assertThat(response.getStatus()).as(entry.get(1)).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("VALIDATION_ERROR").contains("\"field\":\"" + entry.get(0) + "\"");
        }
        assertThat(unitCount(a)).isEqualTo(1);
        assertThat(audit(a, "unit.create")).isEmpty();
    }

    @Test
    @DisplayName("CA5: nome curto gerado e sem acento, com sufixo na colisão, no histórico como atual e resolvido por UnitShortNames")
    void shortName() throws Exception {
        var a = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        var first = body(call(create("{\"name\":\"Academia Leão — Tijuca\"}"), coordinator).getResponse()).path("unit");
        String base = first.path("shortName").asText();
        assertThat(base).startsWith("academia-leao-tijuca");
        var second = body(call(create("{\"name\":\"Academia Leão — Tijuca\"}"), coordinator).getResponse()).path("unit");
        String next = second.path("shortName").asText();
        assertThat(next).isNotEqualTo(base).startsWith("academia-leao-tijuca-");
        var history = adminJdbc().queryForList("select replaced_at from unit_short_name_history where short_name = ?", base);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).get("replaced_at")).isNull();
        try (var ignored = Scopes.open(a.orgId(), null)) {
            assertThat(shortNames.resolve(next)).contains(next);
        }
    }

    @Test
    @DisplayName("CA5: duas organizações com o mesmo nome recebem nomes curtos diferentes (a RLS esconde o da outra)")
    void sameNameAcrossOrganizations() throws Exception {
        var a = tenant();
        var b = tenant();
        String name = unique();
        String first = body(call(create("{\"name\":\"" + name + "\"}"), who(a, Role.COORDINATOR)).getResponse()).path("unit").path("shortName").asText();
        var response = call(create("{\"name\":\"" + name + "\"}"), who(b, Role.COORDINATOR)).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        assertThat(body(response).path("unit").path("shortName").asText()).isNotEqualTo(first).startsWith(first + "-");
    }

    @Test
    @DisplayName("CA6: CEP conhecido ganha as coordenadas da porta; CEP desconhecido cria sem coordenadas; coordenadas enviadas valem")
    void coordinates() throws Exception {
        var a = tenant();
        var coordinator = who(a, Role.COORDINATOR);
        var known = body(call(create("{\"name\":\"" + unique() + "\",\"postalCode\":\"01001-000\"}"), coordinator).getResponse()).path("unit").path("id").asText();
        var row = adminJdbc().queryForMap("select postal_code, latitude::float8 as lat, longitude::float8 as lon from unit where id = ?::uuid", known);
        assertThat(row.get("postal_code")).isEqualTo("01001000");
        assertThat((double) row.get("lat")).isEqualTo(-23.5505);
        assertThat((double) row.get("lon")).isEqualTo(-46.6333);
        var unknown = call(create("{\"name\":\"" + unique() + "\",\"postalCode\":\"99999999\"}"), coordinator).getResponse();
        assertThat(unknown.getStatus()).isEqualTo(201);
        var empty = adminJdbc().queryForMap("select latitude, longitude from unit where id = ?::uuid", body(unknown).path("unit").path("id").asText());
        assertThat(empty.get("latitude")).isNull();
        assertThat(empty.get("longitude")).isNull();
        var sent = body(call(create("{\"name\":\"" + unique() + "\",\"postalCode\":\"01001000\",\"latitude\":-1.5,\"longitude\":2.5}"), coordinator).getResponse())
                .path("unit").path("id").asText();
        var manual = adminJdbc().queryForMap("select latitude::float8 as lat, longitude::float8 as lon from unit where id = ?::uuid", sent);
        assertThat((double) manual.get("lat")).isEqualTo(-1.5);
        assertThat((double) manual.get("lon")).isEqualTo(2.5);
    }

    @Test
    @DisplayName("CA8: com 10 unidades a 11ª responde 409 UNIT_LIMIT_REACHED e nada é criado ou auditado")
    void limit() throws Exception {
        var a = tenant();
        for (int i = 0; i < 8; i++) addUnit(a, "U" + i, "u" + i + "-" + a.orgId(), null);
        var coordinator = who(a, Role.COORDINATOR);
        assertThat(call(create("{\"name\":\"" + unique() + "\"}"), coordinator).getResponse().getStatus()).isEqualTo(201);
        assertThat(unitCount(a)).isEqualTo(10);
        var response = call(create("{\"name\":\"" + unique() + "\"}"), coordinator).getResponse();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("UNIT_LIMIT_REACHED").contains("10 unidades");
        assertThat(unitCount(a)).isEqualTo(10);
        assertThat(audit(a, "unit.create")).hasSize(1);
    }

    @Test
    @DisplayName("CA8: duas criações simultâneas com 9 unidades criam só uma")
    void concurrent() throws Exception {
        var a = tenant();
        for (int i = 0; i < 8; i++) addUnit(a, "U" + i, "u" + i + "-" + a.orgId(), null);
        var coordinator = who(a, Role.COORDINATOR);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> call(create("{\"name\":\"" + unique() + "\"}"), coordinator).getResponse().getStatus();
            var futures = pool.invokeAll(List.of(task, task));
            assertThat(List.of(futures.get(0).get(), futures.get(1).get())).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(unitCount(a)).isEqualTo(10);
    }

    @Test
    @DisplayName("CA9: convite cria vínculo pendente de Administrador só na nova unidade e envia o e-mail")
    void invite() throws Exception {
        var a = tenant();
        outbox.clear();
        String email = "novo-" + UUID.randomUUID() + "@synthetic.example";
        var result = body(call(create("{\"name\":\"" + unique() + "\",\"administrator\":{\"name\":\"Ana\",\"email\":\"" + email + "\"}}"),
                coordinator(a)).getResponse());
        assertThat(result.path("invite").asText()).isEqualTo("SENT");
        UUID unit = UUID.fromString(result.path("unit").path("id").asText());
        var links = adminJdbc().queryForList("""
                select l.status, l.role, lu.unit_id from account_link l join account a on a.id = l.account_id
                join account_link_unit lu on lu.link_id = l.id where a.email = ? and l.organization_id = ?""", email, a.orgId());
        assertThat(links).hasSize(1);
        assertThat(links.get(0).get("status")).isEqualTo("PENDING");
        assertThat(links.get(0).get("role")).isEqualTo("ADMINISTRATOR");
        assertThat(links.get(0).get("unit_id")).isEqualTo(unit);
        assertThat(outbox.list()).anyMatch(m -> m.recipient().equals(email));
    }

    @Test
    @DisplayName("CA10: e-mail que já é Administrador ativo da organização cria a unidade com invite FAILED")
    void inviteFails() throws Exception {
        var a = tenant();
        String email = "ativo-" + UUID.randomUUID() + "@synthetic.example";
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Ativo', 'ACTIVE')", account, email);
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, 'ADMINISTRATOR', 'ACTIVE')", account, a.orgId());
        var response = call(create("{\"name\":\"" + unique() + "\",\"administrator\":{\"name\":\"Ativo\",\"email\":\"" + email + "\"}}"),
                coordinator(a)).getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(body(response).path("invite").asText()).isEqualTo("FAILED");
        assertThat(unitCount(a)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA11: auditoria unit.create com nome, nome curto e cidade, sem e-mail do convidado")
    void audited() throws Exception {
        var a = tenant();
        String email = "audit-" + UUID.randomUUID() + "@synthetic.example";
        var result = body(call(create("{\"name\":\"" + unique() + "\",\"city\":\"Niterói\",\"administrator\":{\"name\":\"Ana\",\"email\":\"" + email + "\"}}"),
                coordinator(a)).getResponse());
        var rows = audit(a, "unit.create");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("actor_profile")).isEqualTo("COORDINATOR");
        assertThat(rows.get(0).get("entity_id")).isEqualTo(result.path("unit").path("id").asText());
        assertThat(rows.get(0).get("a").toString()).contains(result.path("unit").path("shortName").asText()).contains("Niterói");
        assertThat(rows.toString().toLowerCase()).doesNotContain(email, "password", "token", "senha");
    }

    @Test
    @DisplayName("CA12: falha na auditoria desfaz a unidade, as configurações e o histórico")
    void allOrNothing() throws Exception {
        var a = tenant();
        var db = adminJdbc();
        db.execute("create function fail_audit() returns trigger language plpgsql as $$ begin raise exception 'audit down'; end $$");
        db.execute("create trigger fail_audit before insert on audit_log for each row execute function fail_audit()");
        try {
            var response = call(create("{\"name\":\"" + unique() + "\"}"), who(a, Role.COORDINATOR)).getResponse();
            assertThat(response.getStatus()).isEqualTo(500);
        } finally {
            db.execute("drop trigger fail_audit on audit_log");
            db.execute("drop function fail_audit()");
        }
        assertThat(unitCount(a)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_settings where organization_id = ?", Integer.class, a.orgId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from unit_short_name_history where organization_id = ?", Integer.class, a.orgId())).isZero();
    }
}
