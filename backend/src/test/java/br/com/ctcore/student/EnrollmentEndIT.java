package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-MAT-17 e RN-BEN-01 (RF-MAT-03): encerrar modalidade, validação, benefício, auditoria, alcance, isolamento e concorrência, com banco real. */
class EnrollmentEndIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    record Offer(UUID unit, UUID modality, UUID klass, UUID plan) {}

    @AfterEach
    void clean() {
        EnrollmentFixtures.clean(adminJdbc());
        for (String table : new String[] {"account_invite", "account_session", "password_reset"}) {
            adminJdbc().update("delete from " + table + " where account_id in (select id from account where email like '%.example')");
        }
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in (select id from account where email like '%.example'))");
        adminJdbc().update("delete from account_link where account_id in (select id from account where email like '%.example')");
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal principal(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private UUID unit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(),
                "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID modality(Tenant t, UUID unit, int index) {
        UUID id = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(index);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), id);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, id);
        return id;
    }

    private Offer offer(Tenant t, UUID unit, UUID modality, String tag) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, account + "@prof.example");
        UUID teacher = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, 'Professor', 'professor', ?)", teacher, t.orgId(), account);
        UUID klass = UUID.randomUUID(), plan = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "Turma " + tag, Modalities.normalize("Turma " + tag), modality, teacher);
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 15000, 'ACTIVE')",
                plan, t.orgId(), unit, modality, "Plano " + tag, Modalities.normalize("Plano " + tag));
        return new Offer(unit, modality, klass, plan);
    }

    private UUID student(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, phone, postal_code, street, number, "
                + "district, city, state) values (?, ?, ?, 'Aluno Fictício', 'aluno ficticio', date '1990-01-01', '52998224725', '11900000000', '01001000', 'Rua', '1', "
                + "'Centro', 'Cidade', 'SP')", id, t.orgId(), t.unitId());
        return id;
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json").content(body == null ? "" : body.toString());
        return call(request, who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private UUID enroll(UUID student, Offer o, AccountPrincipal who) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.putArray("modalities").add(json.createObjectNode().put("unitId", o.unit().toString()).put("modalityId", o.modality().toString())
                .put("primaryClassId", o.klass().toString()).put("planId", o.plan().toString()).put("dueDay", 5));
        JsonNode e = ok(send(HttpMethod.POST, "/" + student + "/modalities", body, who), 201);
        for (JsonNode m : e.get("modalities")) if (m.get("modality").get("id").asText().equals(o.modality().toString())) return UUID.fromString(m.get("id").asText());
        throw new AssertionError(e.toString());
    }

    private MockHttpServletResponse end(UUID s, UUID row, String reason, String note, AccountPrincipal who) throws Exception {
        ObjectNode b = json.createObjectNode();
        if (reason != null) b.put("reason", reason);
        if (note != null) b.put("note", note);
        return send(HttpMethod.POST, "/" + s + "/modalities/" + row + "/end", b, who);
    }

    private MockHttpServletResponse preview(UUID s, UUID row, AccountPrincipal who) throws Exception {
        return send(HttpMethod.GET, "/" + s + "/modalities/" + row + "/end-preview", null, who);
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private int audits(Tenant t, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }

    private void benefit(UUID row, UUID account) {
        adminJdbc().update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, status) "
                + "select ?, em.organization_id, em.id, em.student_id, em.unit_id, 'PERCENT', 10, 'Motivo', 'Mestre', ?, 'ACTIVE' from enrollment_modality em where em.id = ?",
                UUID.randomUUID(), account, row);
    }

    @Test
    @DisplayName("CA1, CA2, CA3, CA4, CA7: encerra uma de duas modalidades, valida o corpo, encerra o benefício e audita sem a observação")
    void ends() throws Exception {
        Tenant t = tenant();
        UUID bjj = modality(t, t.unitId(), 0), judo = modality(t, t.unitId(), 1);
        Offer a = offer(t, t.unitId(), bjj, "A"), b = offer(t, t.unitId(), judo, "B");
        UUID s = student(t);
        AccountPrincipal gestor = principal(t, Role.COORDINATOR, t.unitId());
        UUID rowA = enroll(s, a, gestor), rowB = enroll(s, b, gestor);
        benefit(rowA, gestor.accountId());
        benefit(rowB, gestor.accountId());

        JsonNode pv = ok(preview(s, rowA, gestor), 200);
        assertThat(pv.get("openCharges")).isEmpty();
        assertThat(audits(t, "student.modality_ended")).isZero();

        // Corpo inválido: 400 e nada muda.
        assertThat(end(s, rowA, null, null, gestor).getStatus()).isEqualTo(400);
        assertThat(end(s, rowA, "NOPE", null, gestor).getStatus()).isEqualTo(400);
        assertThat(end(s, rowA, "FINANCIAL", "x".repeat(501), gestor).getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from enrollment_modality where id = ? and status = 'ACTIVE'", rowA)).isEqualTo(1);
        assertThat(audits(t, "student.modality_ended")).isZero();

        JsonNode after = ok(end(s, rowA, "FINANCIAL", "  Sem dinheiro, lesão no joelho  ", gestor), 200);
        assertThat(after.get("status").asText()).isEqualTo("ACTIVE");
        for (JsonNode m : after.get("modalities")) {
            assertThat(m.get("status").asText()).isEqualTo(m.get("id").asText().equals(rowA.toString()) ? "ENDED" : "ACTIVE");
        }
        assertThat(adminJdbc().queryForMap("select end_reason, end_note from enrollment_modality where id = ?", rowA))
                .containsEntry("end_reason", "FINANCIAL").containsEntry("end_note", "Sem dinheiro, lesão no joelho");
        assertThat(count("select count(*) from enrollment_modality where id = ? and ended_at is not null", rowA)).isEqualTo(1);
        // Benefício da modalidade encerrada acaba; o da outra segue.
        assertThat(count("select count(*) from benefit where enrollment_modality_id = ? and status = 'ENDED' and end_reason = 'Modalidade encerrada'", rowA)).isEqualTo(1);
        assertThat(count("select count(*) from benefit where enrollment_modality_id = ? and status = 'ACTIVE'", rowB)).isEqualTo(1);
        assertThat(audits(t, "benefit.ended")).isEqualTo(1);
        // Auditoria sem a observação nem dado pessoal.
        assertThat(audits(t, "student.modality_ended")).isEqualTo(1);
        String trail = adminJdbc().queryForObject("select before_data::text || after_data::text from audit_log where organization_id = ? and action = 'student.modality_ended'",
                String.class, t.orgId());
        assertThat(trail).contains("FINANCIAL", "hasNote").doesNotContain("joelho", "52998224725");
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'student.modality_ended' and unit_id = ?", t.orgId(), t.unitId())).isEqualTo(1);

        // Já encerrada: 404 nas duas rotas. Vaga liberada: dá para matricular de novo.
        assertThat(end(s, rowA, "OTHER", null, gestor).getStatus()).isEqualTo(404);
        assertThat(preview(s, rowA, gestor).getStatus()).isEqualTo(404);
        UUID again = enroll(s, offer(t, t.unitId(), bjj, "A2"), gestor);
        assertThat(again).isNotEqualTo(rowA);

        // Observação de 500 caracteres e ausente valem; só espaços vira ausente.
        ok(end(s, again, "OTHER", "y".repeat(500), gestor), 200);
        assertThat(count("select count(*) from audit_log where action = 'student.modality_ended' and after_data->>'hasNote' = 'true'")).isEqualTo(2);
        UUID blank = enroll(s, offer(t, t.unitId(), bjj, "A3"), gestor);
        ok(end(s, blank, "SCHEDULE", "   ", gestor), 200);
        assertThat(count("select count(*) from enrollment_modality where id = ? and end_note is null", blank)).isEqualTo(1);
        // Última modalidade ativa: a matrícula vira ex-aluno.
        assertThat(ok(end(s, rowB, "INJURY", null, gestor), 200).get("status").asText()).isEqualTo("FORMER");
        assertThat(count("select count(*) from enrollment_modality where student_id = ?", s)).isEqualTo(4);
    }

    @Test
    @DisplayName("CA5, CA6: perfis, alcance, sessão ausente e outra organização não encerram nada")
    void guards() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID bjj = modality(t, t.unitId(), 0);
        UUID b = unit(t);
        Offer inA = offer(t, t.unitId(), bjj, "A"), inB = offer(t, b, modality(t, b, 1), "B");
        UUID s = student(t);
        AccountPrincipal gestor = principal(t, Role.COORDINATOR, t.unitId(), b);
        UUID rowA = enroll(s, inA, gestor), rowB = enroll(s, inB, gestor);
        AccountPrincipal adminA = principal(t, Role.ADMINISTRATOR, t.unitId());

        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = principal(t, role, t.unitId(), b);
            assertThat(end(s, rowA, "OTHER", null, who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(preview(s, rowA, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(end(s, rowA, "OTHER", null, null).getStatus()).isEqualTo(401);
        assertThat(preview(s, rowA, null).getStatus()).isEqualTo(401);
        assertThat(end(s, rowB, "OTHER", null, adminA).getStatus()).isEqualTo(403);
        assertThat(preview(s, rowB, adminA).getStatus()).isEqualTo(403);
        assertThat(end(s, UUID.randomUUID(), "OTHER", null, gestor).getStatus()).isEqualTo(404);
        assertThat(end(UUID.randomUUID(), rowA, "OTHER", null, gestor).getStatus()).isEqualTo(404);
        AccountPrincipal foreign = principal(other, Role.COORDINATOR, other.unitId());
        assertThat(end(s, rowA, "OTHER", null, foreign).getStatus()).isEqualTo(404);
        assertThat(preview(s, rowA, foreign).getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from enrollment_modality where student_id = ? and status = 'ACTIVE'", s)).isEqualTo(2);
        assertThat(audits(t, "student.modality_ended")).isZero();

        // RLS: com ctcore_app, sem contexto nada aparece; com o contexto de A, a modalidade de B segue invisível.
        UUID foreignRow = adminJdbc().queryForObject("select id from enrollment_modality where student_id = ? limit 1", UUID.class, s);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            try (var rows = sql.executeQuery("select count(*) from enrollment_modality where id = '" + foreignRow + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            sql.execute("select set_config('app.organization_id', '" + other.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from enrollment_modality where id = '" + foreignRow + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
        }

        ok(end(s, rowA, "OTHER", null, adminA), 200);
    }

    @Test
    @DisplayName("CA8: dois encerramentos simultâneos dão um 200, um 404 e uma só auditoria")
    void concurrentEnds() throws Exception {
        Tenant t = tenant();
        Offer a = offer(t, t.unitId(), modality(t, t.unitId(), 0), "A");
        UUID s = student(t);
        AccountPrincipal gestor = principal(t, Role.COORDINATOR, t.unitId());
        UUID row = enroll(s, a, gestor);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> task = () -> end(s, row, "CITY_CHANGE", null, gestor).getStatus();
            Future<Integer> one = pool.submit(task), two = pool.submit(task);
            assertThat(List.of(one.get(), two.get())).containsExactlyInAnyOrder(200, 404);
        } finally {
            pool.shutdownNow();
        }
        assertThat(audits(t, "student.modality_ended")).isEqualTo(1);
    }
}
