package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** RF-ORG-08 e RNF-15: "última alteração" tirada da auditoria e controle de edição simultânea por versão (If-Match). */
class LastChangeIT extends UnitBase {
    private AccountPrincipal named(Tenant t, Role role, String name) {
        AccountPrincipal who = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, ?, 'ACTIVE')", who.accountId(), who.accountId() + "@synthetic.example", name);
        return who;
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        var response = call(request, who).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return body(response);
    }

    private JsonNode orgLast(AccountPrincipal who) throws Exception {
        return ok(get(), who).path("lastChange");
    }

    private void audited(Tenant t, UUID unit, String action, String entityType, String entityId) {
        adminJdbc().update("insert into audit_log (organization_id, unit_id, actor_type, action, entity_type, entity_id, occurred_at) "
                + "values (?, ?, 'SYSTEM', ?, ?, ?, now() + interval '1 second')", t.orgId(), unit, action, entityType, entityId);
    }

    @Test
    @DisplayName("CA1: lastChange da organização traz ator e instante; logo posterior é a última; transferência de coordenação não conta")
    void organizationLastChange() throws Exception {
        var a = tenant();
        var x = named(a, Role.COORDINATOR, "Xavier Coord");
        var y = named(a, Role.COORDINATOR, "Yara Coord");
        assertThat(orgLast(x).isNull()).isTrue();
        ok(patch("{\"name\":\"Novo\"}"), x);
        var first = orgLast(x);
        assertThat(first.path("by").asText()).isEqualTo("Xavier Coord");
        assertThat(adminJdbc().queryForObject("select occurred_at from audit_log where action = 'organization.update' and organization_id = ?",
                OffsetDateTime.class, a.orgId()).toInstant()).isEqualTo(OffsetDateTime.parse(first.path("at").asText()).toInstant());
        assertThat(orgLast(named(a, Role.ADMINISTRATOR, "Ana Admin")).path("by").asText()).isEqualTo("Xavier Coord");
        ok(putLogo(LogoImageTest.image("png", 512, 512), "x.png"), y);
        assertThat(orgLast(x).path("by").asText()).isEqualTo("Yara Coord");
        audited(a, null, "organization.coordination_transferred", "organization", a.orgId().toString());
        assertThat(orgLast(x).path("by").asText()).isEqualTo("Yara Coord");
        ok(deleteLogo(), x);
        assertThat(orgLast(x).path("by").asText()).isEqualTo("Xavier Coord");
    }

    @Test
    @DisplayName("CA2: unidade sem alteração tem lastChange nulo; PUT, nome curto e desativação passam a ser a última")
    void unitLastChange() throws Exception {
        var a = tenant();
        var x = named(a, Role.COORDINATOR, "Xavier Coord");
        var y = named(a, Role.COORDINATOR, "Yara Coord");
        assertThat(ok(read(a.unitId()), x).path("lastChange").isNull()).isTrue();
        ok(put(a.unitId(), payload()), x);
        assertThat(ok(read(a.unitId()), x).path("lastChange").path("by").asText()).isEqualTo("Xavier Coord");
        ok(adjust(a.unitId(), "novo-nome-curto"), y);
        assertThat(ok(read(a.unitId()), x).path("lastChange").path("by").asText()).isEqualTo("Yara Coord");
        audited(a, a.unitId(), "unit.deactivated", "unit", a.unitId().toString());
        var last = ok(read(a.unitId()), x).path("lastChange");
        assertThat(last.path("by").isNull()).isTrue(); // ator de sistema: sem pessoa
        assertThat(last.path("at").asText()).isNotEmpty();
    }

    @Test
    @DisplayName("CA3: alteração da organização B nunca aparece para A; unidade de B pelo id continua 404")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var coordA = named(a, Role.COORDINATOR, "Coord A");
        var coordB = named(b, Role.COORDINATOR, "Coord B");
        ok(patch("{\"name\":\"B mudou\"}"), coordB);
        ok(put(b.unitId(), payload()), coordB);
        assertThat(orgLast(coordA).isNull()).isTrue();
        assertThat(ok(read(a.unitId()), coordA).path("lastChange").isNull()).isTrue();
        assertThat(call(read(b.unitId()), coordA).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(put(b.unitId(), payload()), coordA).getResponse().getStatus()).isEqualTo(404);
        assertThat(orgLast(coordB).path("by").asText()).isEqualTo("Coord B");
    }

    @Test
    @DisplayName("CA4: PATCH e PUT gravam uma auditoria com antes e depois só do campo mudado, ator e organização")
    void audit() throws Exception {
        var a = tenant();
        var x = named(a, Role.COORDINATOR, "Xavier Coord");
        ok(patch("{\"name\":\"Auditada\"}"), x);
        ObjectNode unit = payload();
        unit.put("phone", "21999998888");
        ok(put(a.unitId(), unit), x);
        var org = adminJdbc().queryForMap("select actor_id, organization_id, before_data::text b, after_data::text a from audit_log "
                + "where action = 'organization.update' and organization_id = ?", a.orgId());
        assertThat(org.get("actor_id")).isEqualTo(x.accountId());
        assertThat(org.get("a").toString()).contains("Auditada").doesNotContain("color");
        assertThat(org.get("b").toString()).contains("Synthetic").doesNotContain("color");
        var rows = adminJdbc().queryForList("select after_data::text a from audit_log where action = 'unit.update' and organization_id = ?", a.orgId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("a").toString()).contains("phone").doesNotContain("dueDays").doesNotContain("password").doesNotContain("token");
    }

    @Test
    @DisplayName("CA5 / CA6: versão sobe só quando o valor muda; versão antiga 409; sem If-Match ou inválido 428; nada muda")
    void versions() throws Exception {
        var a = tenant();
        var x = named(a, Role.COORDINATOR, "Xavier Coord");
        assertThat(ok(get(), x).path("version").asInt()).isZero();
        assertThat(ok(patch("{\"name\":\"V1\"}"), x).path("version").asInt()).isEqualTo(1);
        assertThat(ok(patch("{\"name\":\"V1\"}"), x).path("version").asInt()).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'organization.update' and organization_id = ?", Integer.class, a.orgId())).isEqualTo(1);
        var stale = call(patch("{\"name\":\"V2\"}").header("If-Match", "0"), x).getResponse();
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("EDIT_CONFLICT");
        assertThat(ok(get(), x).path("name").asText()).isEqualTo("V1");
        assertThat(call(patch("{\"name\":\"V2\"}").header("If-Match", "\"1\""), x).getResponse().getStatus()).isEqualTo(200);
        assertThat(raw(patch("{\"name\":\"V3\"}"), x).getResponse().getStatus()).isEqualTo(428);
        for (String bad : List.of("abc", "", "-1", "1.5")) {
            var response = call(patch("{\"name\":\"V3\"}").header("If-Match", bad), x).getResponse();
            assertThat(response.getStatus()).as(bad).isEqualTo(428);
            assertThat(response.getContentAsString()).contains("PRECONDITION_REQUIRED");
        }
        assertThat(ok(get(), x).path("name").asText()).isEqualTo("V2");

        assertThat(ok(read(a.unitId()), x).path("version").asInt()).isZero();
        ObjectNode unit = payload();
        assertThat(ok(put(a.unitId(), unit), x).path("version").asInt()).isEqualTo(1);
        assertThat(ok(put(a.unitId(), unit), x).path("version").asInt()).isEqualTo(1);
        assertThat(audit(a, "unit.update")).hasSize(1);
        unit.put("phone", "21999998888");
        var staleUnit = call(put(a.unitId(), unit).header("If-Match", "0"), x).getResponse();
        assertThat(staleUnit.getStatus()).isEqualTo(409);
        assertThat(staleUnit.getContentAsString()).contains("EDIT_CONFLICT");
        assertThat(raw(put(a.unitId(), unit), x).getResponse().getStatus()).isEqualTo(428);
        assertThat(call(put(a.unitId(), unit).header("If-Match", "x"), x).getResponse().getStatus()).isEqualTo(428);
        assertThat(ok(read(a.unitId()), x).path("phone").isNull()).isTrue();
        assertThat(audit(a, "unit.update")).hasSize(1);
        assertThat(call(put(a.unitId(), unit).header("If-Match", "1"), x).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA8: Administrador sem escrita recebe 403 antes da checagem de versão; Professor e portal 403 na leitura")
    void permissionsFirst() throws Exception {
        var a = tenant();
        var admin = who(a, Role.ADMINISTRATOR);
        assertThat(raw(patch("{\"name\":\"Hack\"}"), admin).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(patch("{\"name\":\"Hack\"}").header("If-Match", "99"), admin).getResponse().getStatus()).isEqualTo(403);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(get(), who(a, role)).getResponse().getStatus()).isEqualTo(403);
            assertThat(call(read(a.unitId()), who(a, role)).getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(adminJdbc().queryForObject("select version from organization where id = ?", Integer.class, a.orgId())).isZero();
    }

    @Test
    @DisplayName("CA7: duas gravações simultâneas com a mesma versão: uma 200, uma 409; a versão sobe uma vez")
    void concurrent() throws Exception {
        var a = tenant();
        var x = named(a, Role.COORDINATOR, "Xavier Coord");
        List<Integer> orgStatuses = race(() -> call(patch("{\"name\":\"" + UUID.randomUUID() + "\"}").header("If-Match", "0"), x).getResponse().getStatus());
        assertThat(orgStatuses).containsExactlyInAnyOrder(200, 409);
        assertThat(adminJdbc().queryForObject("select version from organization where id = ?", Integer.class, a.orgId())).isEqualTo(1);
        List<Integer> unitStatuses = race(() -> {
            ObjectNode body = payload();
            body.put("name", "U " + UUID.randomUUID());
            return call(put(a.unitId(), body).header("If-Match", "0"), x).getResponse().getStatus();
        });
        assertThat(unitStatuses).containsExactlyInAnyOrder(200, 409);
        assertThat(adminJdbc().queryForObject("select version from unit where id = ?", Integer.class, a.unitId())).isEqualTo(1);
    }

    private static List<Integer> race(Callable<Integer> request) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<Integer> result = new ArrayList<>();
            for (var f : futures) result.add(f.get());
            return result;
        } finally {
            pool.shutdownNow();
        }
    }
}
