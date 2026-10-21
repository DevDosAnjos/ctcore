package br.com.ctcore.benefit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-BEN-02: preview e concessão (substituição, 409, validação, perfis, alcance, isolamento, auditoria e concorrência), com banco real. */
class BenefitGrantIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        for (String table : new String[] {"benefit", "enrollment_modality", "enrollment", "student", "plan", "training_class", "teacher", "unit_modality",
                "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from account where email like 'grant-%@example.com'");
    }

    private record Fx(UUID student, UUID row) {}

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "grant-" + p.accountId() + "@example.com");
        return p;
    }

    private AccountPrincipal coordinator(Tenant t) {
        return account(t, Role.COORDINATOR);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private Fx enrolled(Tenant t, UUID unit, String name, long planCents) {
        UUID modality = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(0);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), modality);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        UUID teacher = UUID.randomUUID(), klass = UUID.randomUUID(), plan = UUID.randomUUID(), student = UUID.randomUUID(), enrollment = UUID.randomUUID(), row = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "T" + klass, "t" + klass, modality, teacher);
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                plan, t.orgId(), unit, modality, "P" + plan, "p" + plan, planCents);
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, ?, ?, date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", student, t.orgId(), unit, name, Modalities.normalize(name));
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at) values (?, ?, ?, ?, ?, ?, ?, ?, 10, 'ACTIVE', now())", row, t.orgId(), enrollment, student, unit, modality, klass, plan);
        return new Fx(student, row);
    }

    private Map<String, Object> body(Fx fx, String type, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", fx.student());
        m.put("modalityRowId", fx.row());
        m.put("type", type);
        m.put("value", value);
        m.put("reason", "Bolsa social");
        m.put("decidedBy", "Mestre Silva");
        return m;
    }

    private MockHttpServletResponse post(String path, Object body, AccountPrincipal who) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/v1/benefits" + path).contentType("application/json").content(json.writeValueAsString(body));
        return call(req, who).getResponse();
    }

    private JsonNode read(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private int count(String where, Object... args) {
        return adminJdbc().queryForObject("select count(*) from benefit where " + where, Integer.class, args);
    }

    private int audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, t.orgId(), action);
    }

    @Test
    @DisplayName("CA1, CA4, CA7: concede, devolve a linha com o nome da conta, ignora lançador/organização do corpo; bolsa de 100% não muda o aluno")
    void grant() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana", 15000);
        var c = coordinator(t);
        String before = adminJdbc().queryForObject("select em::text from enrollment_modality em where id = ?", String.class, fx.row());
        var b = body(fx, "PERCENT", 100);
        b.put("grantedBy", "Invasor");
        b.put("organizationId", UUID.randomUUID());
        var r = post("", b, c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        var row = read(r);
        assertThat(row.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(row.get("grantedBy").asText()).isEqualTo("Gestora Sintética");
        assertThat(row.get("monthlyValueCents").asLong()).isZero();
        assertThat(row.get("high").asBoolean()).isTrue();
        assertThat(adminJdbc().queryForObject("select organization_id from benefit where id = ?", UUID.class, UUID.fromString(row.get("id").asText()))).isEqualTo(t.orgId());
        assertThat(adminJdbc().queryForObject("select em::text from enrollment_modality em where id = ?", String.class, fx.row())).isEqualTo(before);
        var list = json.readTree(call(MockMvcRequestBuilders.get("/api/v1/benefits"), c).getResponse().getContentAsString());
        assertThat(list.get("total").asInt()).isEqualTo(1);
        var student = json.readTree(call(MockMvcRequestBuilders.get("/api/v1/students/" + fx.student() + "/enrollment"), c).getResponse().getContentAsString());
        assertThat(student.get("status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("CA2, CA10: sem confirmação 409 e nada muda; com replaceExisting encerra o atual (bolsa para reais e de volta) e audita")
    void replace() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana", 15000);
        var c = coordinator(t);
        assertThat(post("", body(fx, "PERCENT", 30), c).getStatus()).isEqualTo(201);
        assertThat(post("", body(fx, "AMOUNT", 5000), c).getStatus()).isEqualTo(409);
        assertThat(post("", body(fx, "PERCENT", 30), c).getStatus()).isEqualTo(409);
        assertThat(count("status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("true")).isEqualTo(1);
        assertThat(audits(t, "benefit.granted")).isEqualTo(1);
        assertThat(audits(t, "benefit.ended")).isZero();

        var withReplace = body(fx, "AMOUNT", 5000);
        withReplace.put("replaceExisting", true);
        assertThat(post("", withReplace, c).getStatus()).isEqualTo(201);
        var back = body(fx, "PERCENT", 40);
        back.put("replaceExisting", true);
        assertThat(post("", back, c).getStatus()).isEqualTo(201);
        assertThat(count("status = 'ACTIVE'")).isEqualTo(1);
        assertThat(count("status = 'ENDED' and ended_at is not null and end_reason = 'Substituído por novo benefício'")).isEqualTo(2);
        assertThat(audits(t, "benefit.granted")).isEqualTo(3);
        assertThat(audits(t, "benefit.ended")).isEqualTo(2);
        String audit = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'benefit.granted' and after_data->>'type' = 'PERCENT' limit 1",
                String.class, t.orgId());
        assertThat(audit).contains("Bolsa social", "Mestre Silva", "PERCENT").doesNotContain("cpf");
        assertThat(adminJdbc().queryForObject("select unit_id from audit_log where organization_id = ? and action = 'benefit.ended' limit 1", UUID.class, t.orgId()))
                .isEqualTo(t.unitId());
    }

    @Test
    @DisplayName("CA3: entradas inválidas dão 400 com o campo e nada é gravado; validade de hoje é aceita")
    void validation() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana", 15000);
        var c = coordinator(t);
        List<Map<String, Object>> bad = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        for (Object[] v : new Object[][] {{"X", 10, "type"}, {"PERCENT", 0, "value"}, {"PERCENT", 101, "value"}, {"PERCENT", 10.5, "value"},
                {"AMOUNT", 0, "value"}, {"AMOUNT", 100000000, "value"}}) {
            bad.add(body(fx, (String) v[0], v[1]));
            fields.add((String) v[2]);
        }
        var noReason = body(fx, "PERCENT", 10);
        noReason.put("reason", " ");
        bad.add(noReason);
        fields.add("reason");
        var longReason = body(fx, "PERCENT", 10);
        longReason.put("reason", "x".repeat(201));
        bad.add(longReason);
        fields.add("reason");
        var shortBy = body(fx, "PERCENT", 10);
        shortBy.put("decidedBy", "M");
        bad.add(shortBy);
        fields.add("decidedBy");
        var yesterday = body(fx, "PERCENT", 10);
        yesterday.put("validUntil", LocalDate.now().minusDays(2).toString());
        bad.add(yesterday);
        fields.add("validUntil");
        for (int i = 0; i < bad.size(); i++) {
            var r = post("", bad.get(i), c);
            assertThat(r.getStatus()).as(bad.get(i).toString()).isEqualTo(400);
            assertThat(r.getContentAsString()).contains(fields.get(i));
        }
        assertThat(count("true")).isZero();
        assertThat(audits(t, "benefit.granted")).isZero();
        var ok = body(fx, "PERCENT", 10);
        ok.put("validUntil", LocalDate.now().toString());
        assertThat(post("", ok, c).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA6: preview de bolsa 30%, R$ 50, R$ 200 e 100% sobre plano de R$ 150; com ativo preenche atual e replaces; não grava")
    void preview() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana", 15000);
        var c = coordinator(t);
        long[] expected = {10500, 10000, 0, 0};
        boolean[] high = {false, false, true, true};
        Object[][] cases = {{"PERCENT", 30}, {"AMOUNT", 5000}, {"AMOUNT", 20000}, {"PERCENT", 100}};
        for (int i = 0; i < cases.length; i++) {
            var r = post("/preview", body(fx, (String) cases[i][0], cases[i][1]), c);
            assertThat(r.getStatus()).isEqualTo(200);
            var p = read(r);
            assertThat(p.get("newMonthlyValueCents").asLong()).isEqualTo(expected[i]);
            assertThat(p.get("high").asBoolean()).isEqualTo(high[i]);
            assertThat(p.get("replaces").asBoolean()).isFalse();
            assertThat(p.get("current").isNull()).isTrue();
            assertThat(p.get("plan").get("valueCents").asLong()).isEqualTo(15000);
            assertThat(p.get("effectiveFrom").asText()).matches("\\d{4}-\\d{2}");
        }
        assertThat(count("true")).isZero();
        assertThat(post("", body(fx, "PERCENT", 20), c).getStatus()).isEqualTo(201);
        var p = read(post("/preview", body(fx, "AMOUNT", 5000), c));
        assertThat(p.get("replaces").asBoolean()).isTrue();
        assertThat(p.get("current").get("value").asLong()).isEqualTo(20);
        assertThat(count("true")).isEqualTo(1);
        assertThat(audits(t, "benefit.granted")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8: perfis, alcance do Administrador e 404 para aluno/linha inexistente, de outro aluno, encerrada ou de outra organização")
    void permissions() throws Exception {
        var t = tenant();
        UUID other = unit(t, "Zona Sul");
        var mine = enrolled(t, t.unitId(), "Minha", 15000);
        var foreign = enrolled(t, other, "Alheia", 15000);
        var c = coordinator(t);
        var otherOrg = tenant();
        for (String path : new String[] {"", "/preview"}) {
            for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
                assertThat(post(path, body(mine, "PERCENT", 10), who(t, role)).getStatus()).as(role + path).isEqualTo(403);
            }
            assertThat(post(path, body(mine, "PERCENT", 10), null).getStatus()).isEqualTo(401);
            var admin = account(t, Role.ADMINISTRATOR);
            assertThat(post(path, body(foreign, "PERCENT", 10), admin).getStatus()).as("fora do alcance" + path).isEqualTo(403);
            assertThat(post(path, body(mine, "PERCENT", 10), admin).getStatus()).isIn(200, 201);
            var wrongStudent = body(mine, "PERCENT", 10);
            wrongStudent.put("studentId", foreign.student());
            assertThat(post(path, wrongStudent, c).getStatus()).isEqualTo(404);
            var noStudent = body(mine, "PERCENT", 10);
            noStudent.put("studentId", UUID.randomUUID());
            assertThat(post(path, noStudent, c).getStatus()).isEqualTo(404);
            var noRow = body(mine, "PERCENT", 10);
            noRow.put("modalityRowId", UUID.randomUUID());
            assertThat(post(path, noRow, c).getStatus()).isEqualTo(404);
            assertThat(post(path, body(mine, "PERCENT", 10), coordinator(otherOrg)).getStatus()).as("outra organização" + path).isEqualTo(404);
        }
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id = ?", foreign.row());
        assertThat(post("", body(foreign, "PERCENT", 10), c).getStatus()).isEqualTo(404);
        assertThat(count("student_id = ?", foreign.student())).isZero();
    }

    @Test
    @DisplayName("CA9: ids de B usados pelo Coordenador de A dão 404 e nada muda em B")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var fxB = enrolled(b, b.unitId(), "Beto", 15000);
        assertThat(post("", body(fxB, "PERCENT", 10), coordinator(b)).getStatus()).isEqualTo(201);
        assertThat(count("organization_id = ?", a.orgId())).isZero();
        var replace = body(fxB, "AMOUNT", 100);
        replace.put("replaceExisting", true);
        assertThat(post("", replace, coordinator(a)).getStatus()).isEqualTo(404);
        assertThat(count("organization_id = ? and status = 'ACTIVE'", b.orgId())).isEqualTo(1);
        assertThat(count("organization_id = ?", b.orgId())).isEqualTo(1);
    }

    @Test
    @DisplayName("CA11: duas concessões simultâneas sem confirmação deixam um ativo e a outra recebe 409")
    void concurrent() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana", 15000);
        var c = coordinator(t);
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return post("", body(fx, "PERCENT", 10), c).getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var f : results) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(count("status = 'ACTIVE'")).isEqualTo(1);
    }
}
