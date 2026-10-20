package br.com.ctcore.plan;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-PLA-03: criar, editar, desativar e ativar plano, opções, impacto, histórico, perfis, alcance e isolamento, com banco real. */
class PlanWriteIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from plan_value_history");
        adminJdbc().update("delete from plan");
        adminJdbc().update("delete from unit_modality");
        adminJdbc().update("delete from organization_modality");
    }

    private MockHttpServletResponse post(String path, Object body, AccountPrincipal who) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/v1/plans" + path);
        if (body != null) req = req.contentType("application/json").content(json.writeValueAsString(body));
        return call(req, who).getResponse();
    }

    private MockHttpServletResponse put(UUID id, Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.put("/api/v1/plans/" + id).contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    private MockHttpServletResponse get(String path, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/plans" + path), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID modality(Tenant t, UUID unit, String name, boolean active, boolean offered) {
        UUID id = adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, ?) on conflict do nothing", t.orgId(), id, active);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, ?) on conflict do nothing", t.orgId(), unit, id, offered);
        return id;
    }

    private UUID unit(Tenant t, String name, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12), active);
        return id;
    }

    private Map<String, Object> valid(UUID unit, UUID modality, String name) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("unitId", unit);
        b.put("modalityId", modality);
        b.put("name", name);
        b.put("valueCents", 15000);
        b.put("weeklyFrequency", 2);
        b.put("validOtherUnits", false);
        return b;
    }

    private static Map<String, Object> withOther(Map<String, Object> b) {
        b.put("validOtherUnits", true);
        return b;
    }

    private UUID created(Tenant t, UUID unit, UUID modality, String name) throws Exception {
        var r = post("", valid(unit, modality, name), who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(r).get("id").asText());
    }

    private void assertField(MockHttpServletResponse r, int status, String field) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        assertThat(body(r).get("fields").findValuesAsText("field")).contains(field);
    }

    private int planCount(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from plan where organization_id = ?", Integer.class, t.orgId());
    }

    private int audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, t.orgId(), action);
    }

    @Test
    @DisplayName("CA1, CA9, CA12: cria plano ativo com histórico de hoje e auditoria sem presença")
    void create() throws Exception {
        var t = tenant();
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        var r = post("", valid(t.unitId(), jj, "  Mensal 2x "), who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).isEqualTo(201);
        var b = body(r);
        assertThat(b.get("name").asText()).isEqualTo("Mensal 2x");
        assertThat(b.get("valueCents").asLong()).isEqualTo(15000);
        assertThat(b.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(b.get("studentsUsing").asInt()).isZero();
        assertThat(b.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(r.getContentAsString().toLowerCase()).doesNotContain("attend", "presen", "lesson");
        var h = adminJdbc().queryForList("select value_cents, starts_on from plan_value_history where plan_id = ?", UUID.fromString(b.get("id").asText()));
        assertThat(h).hasSize(1);
        assertThat(h.get(0).get("value_cents")).isEqualTo(15000L);
        assertThat(h.get(0).get("starts_on").toString()).isEqualTo(LocalDate.now().toString());
        assertThat(audits(t, "plan.created")).isEqualTo(1);
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.created'", String.class, t.orgId());
        assertThat(after).contains("Mensal 2x", "15000").doesNotContain("password", "token", "card");
        assertThat(adminJdbc().queryForObject("select unit_id from audit_log where organization_id = ? and action = 'plan.created'", UUID.class, t.orgId())).isEqualTo(t.unitId());
    }

    @Test
    @DisplayName("CA2: entrada inválida dá 400 no campo e nada é gravado")
    void invalid() throws Exception {
        var t = tenant();
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        UUID off = modality(t, t.unitId(), "Judô", false, true);
        UUID notOffered = modality(t, t.unitId(), "Boxe", true, false);
        UUID closed = unit(t, "Fechada", false);
        modality(t, closed, "Jiu-Jitsu", true, true);
        var c = who(t, Role.COORDINATOR);
        for (String name : new String[] {"A", "x".repeat(61), " ", null}) {
            var b = valid(t.unitId(), jj, name);
            assertField(post("", b, c), 400, "name");
        }
        for (Object value : new Object[] {0, -5, 100000000, null, 150.5, "abc"}) {
            var b = valid(t.unitId(), jj, "Plano");
            b.put("valueCents", value);
            assertThat(post("", b, c).getStatus()).as(String.valueOf(value)).isEqualTo(400);
        }
        var freq = valid(t.unitId(), jj, "Plano");
        freq.put("weeklyFrequency", 4);
        assertField(post("", freq, c), 400, "weeklyFrequency");
        var noModality = valid(t.unitId(), null, "Plano");
        assertField(post("", noModality, c), 400, "modalityId");
        assertField(post("", valid(t.unitId(), off, "Plano"), c), 400, "modalityId");
        assertField(post("", valid(t.unitId(), notOffered, "Plano"), c), 400, "modalityId");
        assertField(post("", valid(closed, jj, "Plano"), c), 400, "unitId");
        assertField(post("", valid(null, jj, "Plano"), c), 400, "unitId");
        assertField(post("", valid(UUID.randomUUID(), jj, "Plano"), c), 400, "unitId");
        assertThat(planCount(t)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from plan_value_history where organization_id = ?", Integer.class, t.orgId())).isZero();
    }

    @Test
    @DisplayName("CA3: nome repetido entre ativos (sem acento nem caixa) dá 409; outra modalidade, outra unidade ou primeiro desativado dão 201")
    void uniqueName() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        UUID judo = modality(t, t.unitId(), "Judô", true, true);
        modality(t, second, "Jiu-Jitsu", true, true);
        var c = who(t, Role.COORDINATOR);
        UUID first = created(t, t.unitId(), jj, "Mensal 2x");
        assertThat(post("", valid(t.unitId(), jj, "MENSAL 2X"), c).getStatus()).isEqualTo(409);
        assertThat(post("", valid(t.unitId(), jj, "Mênsal 2x"), c).getStatus()).isEqualTo(409);
        assertThat(post("", valid(t.unitId(), judo, "Mensal 2x"), c).getStatus()).isEqualTo(201);
        assertThat(post("", valid(second, jj, "Mensal 2x"), c).getStatus()).isEqualTo(201);
        assertThat(post("/" + first + "/deactivate", null, c).getStatus()).isEqualTo(200);
        assertThat(post("", valid(t.unitId(), jj, "Mensal 2x"), c).getStatus()).isEqualTo(201);
        assertThat(planCount(t)).isEqualTo(4);
    }

    @Test
    @DisplayName("CA4, CA7, CA12: edita; histórico só com valor novo; unidade e modalidade não mudam; desativado 409; escolhas da RN-PLA-05 na auditoria")
    void update() throws Exception {
        var t = tenant();
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        UUID judo = modality(t, t.unitId(), "Judô", true, true);
        UUID id = created(t, t.unitId(), jj, "Mensal 2x");
        var c = who(t, Role.COORDINATOR);

        var same = valid(t.unitId(), jj, "Mensal 3x");
        same.put("weeklyFrequency", null);
        var r = put(id, same, c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(body(r).get("name").asText()).isEqualTo("Mensal 3x");
        assertThat(body(r).get("weeklyFrequency").isNull()).isTrue();
        assertThat(adminJdbc().queryForObject("select count(*) from plan_value_history where plan_id = ?", Integer.class, id)).isEqualTo(1);
        String noChoices = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.updated'", String.class, t.orgId());
        assertThat(noChoices).doesNotContain("unpaidCharges", "paidFutureDifference");

        var priced = valid(t.unitId(), jj, "Mensal 3x");
        priced.put("valueCents", 18000);
        priced.put("unpaidCharges", "ADJUST");
        priced.put("paidFutureDifference", true);
        assertThat(put(id, priced, c).getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForList("select value_cents from plan_value_history where plan_id = ? order by created_at", Long.class, id)).containsExactly(15000L, 18000L);
        String audit = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.updated' order by occurred_at desc limit 1", String.class, t.orgId());
        assertThat(audit).contains("18000", "ADJUST", "\"paidFutureDifference\": true");
        String old = adminJdbc().queryForObject("select before_data::text from audit_log where organization_id = ? and action = 'plan.updated' order by occurred_at desc limit 1", String.class, t.orgId());
        assertThat(old).contains("15000");

        var defaults = valid(t.unitId(), jj, "Mensal 3x");
        defaults.put("valueCents", 20000);
        assertThat(put(id, defaults, c).getStatus()).isEqualTo(200);
        audit = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'plan.updated' order by occurred_at desc limit 1", String.class, t.orgId());
        assertThat(audit).contains("KEEP", "\"paidFutureDifference\": false");

        var bad = valid(t.unitId(), jj, "Mensal 3x");
        bad.put("valueCents", 21000);
        bad.put("unpaidCharges", "DELETE");
        assertField(put(id, bad, c), 400, "unpaidCharges");
        assertThat(adminJdbc().queryForObject("select value_cents from plan where id = ?", Long.class, id)).isEqualTo(20000L);

        assertField(put(id, valid(t.unitId(), judo, "Mensal 3x"), c), 400, "modalityId");
        assertField(put(id, valid(unit(t, "Outra", true), jj, "Mensal 3x"), c), 400, "unitId");
        assertThat(post("/" + id + "/deactivate", null, c).getStatus()).isEqualTo(200);
        assertThat(put(id, valid(t.unitId(), jj, "Mensal 3x"), c).getStatus()).isEqualTo(409);

        UUID other = created(t, t.unitId(), jj, "Anual");
        assertThat(put(other, valid(t.unitId(), jj, "mensal 3x"), c).getStatus()).isEqualTo(200);
        UUID third = created(t, t.unitId(), jj, "Trimestral");
        assertThat(put(third, valid(t.unitId(), jj, "MENSAL 3X"), c).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA5, CA14: vale em outras unidades só com a configuração plan; opções só do alcance e só modalidades ativas")
    void crossUnit() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        modality(t, t.unitId(), "Judô", false, true);
        modality(t, t.unitId(), "Boxe", true, false);
        modality(t, second, "Jiu-Jitsu", true, true);
        var c = who(t, Role.COORDINATOR);
        var yes = valid(t.unitId(), jj, "Com outras");
        yes.put("validOtherUnits", true);
        var r = post("", yes, c);
        assertThat(body(r).get("validOtherUnits").asBoolean()).isTrue();
        UUID id = UUID.fromString(body(r).get("id").asText());

        for (String mode : new String[] {"allowed", "denied"}) {
            adminJdbc().update("update organization_settings set cross_unit_training = ? where organization_id = ?", mode, t.orgId());
            var again = valid(t.unitId(), jj, "Com outras " + mode);
            again.put("validOtherUnits", true);
            assertThat(body(post("", again, c)).get("validOtherUnits").asBoolean()).as(mode).isFalse();
            assertThat(body(put(id, withOther(valid(t.unitId(), jj, "Editada " + mode)), c)).get("validOtherUnits").asBoolean()).as(mode).isFalse();
            assertThat(body(get("/options", c)).get("crossUnitTraining").asText()).isEqualTo(mode);
        }

        adminJdbc().update("update organization_settings set cross_unit_training = 'plan' where organization_id = ?", t.orgId());
        var options = body(get("/options", c));
        assertThat(options.get("crossUnitTraining").asText()).isEqualTo("plan");
        assertThat(options.get("units").findValuesAsText("name")).contains("Zona Sul", "Unit");
        JsonNode mine = null;
        for (JsonNode u : options.get("units")) if (u.get("id").asText().equals(t.unitId().toString())) mine = u;
        assertThat(mine.get("modalities").findValuesAsText("name")).containsExactly("Jiu-Jitsu");
        var admin = body(get("/options", who(t, Role.ADMINISTRATOR)));
        assertThat(admin.get("units")).hasSize(1);
        assertThat(admin.get("units").get(0).get("id").asText()).isEqualTo(t.unitId().toString());
    }

    @Test
    @DisplayName("CA6: impacto devolve alunos afetados e NEXT_CHARGE; valor inválido ou ausente 400; não grava auditoria")
    void impact() throws Exception {
        var t = tenant();
        UUID id = created(t, t.unitId(), modality(t, t.unitId(), "Jiu-Jitsu", true, true), "Mensal");
        var c = who(t, Role.COORDINATOR);
        var r = get("/" + id + "/value-impact?valueCents=18000", c);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r).get("studentsAffected").asInt()).isZero();
        assertThat(body(r).get("effectiveFrom").asText()).isEqualTo("NEXT_CHARGE");
        for (String q : new String[] {"", "?valueCents=", "?valueCents=0", "?valueCents=-1", "?valueCents=100000000", "?valueCents=abc", "?valueCents=1.5"}) {
            assertField(get("/" + id + "/value-impact" + q, c), 400, "valueCents");
        }
        assertThat(get("/" + UUID.randomUUID() + "/value-impact?valueCents=100", c).getStatus()).isEqualTo(404);
        assertThat(audits(t, "plan.updated")).isZero();
    }

    @Test
    @DisplayName("CA8, CA12: desativar e ativar são idempotentes e auditados; homônimo ativo impede reativar; não há exclusão")
    void activation() throws Exception {
        var t = tenant();
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        UUID id = created(t, t.unitId(), jj, "Mensal");
        var c = who(t, Role.COORDINATOR);
        assertThat(body(post("/" + id + "/deactivate", null, c)).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(post("/" + id + "/deactivate", null, c).getStatus()).isEqualTo(200);
        assertThat(audits(t, "plan.deactivated")).isEqualTo(1);
        assertThat(body(get("?status=INACTIVE", c)).get("items").findValuesAsText("id")).contains(id.toString());

        UUID twin = created(t, t.unitId(), jj, "mensal");
        assertThat(post("/" + id + "/activate", null, c).getStatus()).isEqualTo(409);
        assertThat(post("/" + twin + "/deactivate", null, c).getStatus()).isEqualTo(200);
        assertThat(body(post("/" + id + "/activate", null, c)).get("status").asText()).isEqualTo("ACTIVE");
        assertThat(post("/" + id + "/activate", null, c).getStatus()).isEqualTo(200);
        assertThat(audits(t, "plan.activated")).isEqualTo(1);

        int status = call(MockMvcRequestBuilders.delete("/api/v1/plans/" + id), c).getResponse().getStatus();
        assertThat(status).isIn(404, 405);
        assertThat(planCount(t)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA10: Administrador só age nas unidades dele; Professor, Aluno e Responsável 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        modality(t, second, "Jiu-Jitsu", true, true);
        UUID mine = created(t, t.unitId(), jj, "Minha");
        UUID theirs = created(t, second, jj, "Alheia");
        var admin = who(t, Role.ADMINISTRATOR);

        assertThat(post("", valid(second, jj, "Nova"), admin).getStatus()).isEqualTo(403);
        assertThat(put(theirs, valid(second, jj, "Alheia 2"), admin).getStatus()).isEqualTo(404);
        assertThat(post("/" + theirs + "/deactivate", null, admin).getStatus()).isEqualTo(404);
        assertThat(post("/" + theirs + "/activate", null, admin).getStatus()).isEqualTo(404);
        assertThat(get("/" + theirs + "/value-impact?valueCents=100", admin).getStatus()).isEqualTo(404);
        assertThat(post("", valid(t.unitId(), jj, "Nova"), admin).getStatus()).isEqualTo(201);
        assertThat(put(mine, valid(t.unitId(), jj, "Minha 2"), admin).getStatus()).isEqualTo(200);
        assertThat(post("/" + mine + "/deactivate", null, admin).getStatus()).isEqualTo(200);
        assertThat(post("/" + mine + "/activate", null, admin).getStatus()).isEqualTo(200);

        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = who(t, role);
            assertThat(post("", valid(t.unitId(), jj, "X"), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(put(mine, valid(t.unitId(), jj, "X"), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(get("/options", who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(get("/" + mine + "/value-impact?valueCents=100", who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(post("/" + mine + "/deactivate", null, who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(post("/" + mine + "/activate", null, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(post("", valid(t.unitId(), jj, "X"), null).getStatus()).isEqualTo(401);
        assertThat(put(mine, valid(t.unitId(), jj, "X"), null).getStatus()).isEqualTo(401);
        assertThat(get("/options", null).getStatus()).isEqualTo(401);
        assertThat(get("/" + mine + "/value-impact?valueCents=100", null).getStatus()).isEqualTo(401);
        assertThat(post("/" + mine + "/deactivate", null, null).getStatus()).isEqualTo(401);
        assertThat(post("/" + mine + "/activate", null, null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA11: plano de outra organização dá 404 e nada muda; RLS em plan e plan_value_history; histórico só acrescenta")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID theirs = created(b, b.unitId(), modality(b, b.unitId(), "Jiu-Jitsu", true, true), "Dele");
        UUID jjA = modality(a, a.unitId(), "Jiu-Jitsu", true, true);
        var ca = who(a, Role.COORDINATOR);
        assertThat(put(theirs, valid(a.unitId(), jjA, "Invasor"), ca).getStatus()).isEqualTo(404);
        assertThat(post("/" + theirs + "/deactivate", null, ca).getStatus()).isEqualTo(404);
        assertThat(post("/" + theirs + "/activate", null, ca).getStatus()).isEqualTo(404);
        assertThat(get("/" + theirs + "/value-impact?valueCents=100", ca).getStatus()).isEqualTo(404);
        assertThat(post("", valid(b.unitId(), jjA, "Cruzado"), ca).getStatus()).isIn(400, 403);
        assertThat(adminJdbc().queryForObject("select name from plan where id = ?", String.class, theirs)).isEqualTo("Dele");
        assertThat(adminJdbc().queryForObject("select status from plan where id = ?", String.class, theirs)).isEqualTo("ACTIVE");
        assertThat(planCount(a)).isZero();

        created(a, a.unitId(), jjA, "Meu");
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            for (String table : List.of("plan", "plan_value_history")) {
                try (var rows = sql.executeQuery("select count(*) from " + table + " where organization_id = '" + b.orgId() + "'")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).as(table).isZero();
                }
                try (var rows = sql.executeQuery("select count(*) from " + table)) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getInt(1)).as(table).isEqualTo(1);
                }
            }
            assertThat(sql.executeUpdate("update plan set name = 'x' where organization_id = '" + b.orgId() + "'")).isZero();
            assertThatRefused(sql, "update plan_value_history set value_cents = 1");
            assertThatRefused(sql, "delete from plan_value_history");
            assertThatRefused(sql, "insert into plan_value_history (organization_id, plan_id, value_cents, starts_on) select organization_id, id, 100, now() from plan where organization_id = '" + b.orgId() + "'");
            app.rollback();
        }
    }

    private static void assertThatRefused(Statement sql, String statement) throws SQLException {
        // a failed statement aborts the transaction: a savepoint keeps the checks independent
        sql.execute("savepoint s");
        try {
            int rows = sql.executeUpdate(statement);
            assertThat(rows).as(statement).isZero();
        } catch (SQLException e) {
            assertThat(e.getMessage()).as(statement).containsAnyOf("permission denied", "row-level security");
            sql.execute("rollback to savepoint s");
        }
    }

    @Test
    @DisplayName("CA1, T1: o teto do valor existe no banco e a migração preencheu o histórico dos planos existentes")
    void schema() {
        var t = tenant();
        UUID jj = modality(t, t.unitId(), "Jiu-Jitsu", true, true);
        UUID id = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminJdbc().update(
                "insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents) values (?, ?, ?, ?, 'Teto', ?, 100000000)",
                id, t.orgId(), t.unitId(), jj, Modalities.normalize("Teto"))).hasMessageContaining("plan_value_cents_max");
        assertThat(adminJdbc().queryForObject("select count(*) from information_schema.columns where table_schema = 'public' and table_name = 'plan_value_history' and column_name in ('plan_id', 'value_cents', 'starts_on', 'created_by')", Integer.class)).isEqualTo(4);
    }
}
