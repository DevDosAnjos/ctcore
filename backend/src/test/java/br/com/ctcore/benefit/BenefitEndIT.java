package br.com.ctcore.benefit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** RF-BEN-03 / RN-PRO-08: encerrar benefício e desconto de professor, com banco real. */
class BenefitEndIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        for (String table : new String[] {"benefit", "enrollment_modality", "enrollment", "student", "plan", "training_class", "teacher_unit", "teacher",
                "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from account where email like 'end-%@example.com'");
    }

    private record Fx(UUID student, UUID row, UUID teacher) {}

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "end-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private Fx enrolled(Tenant t, UUID unit, String name) {
        UUID modality = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(0);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), modality);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        UUID teacher = UUID.randomUUID(), klass = UUID.randomUUID(), plan = UUID.randomUUID(), student = UUID.randomUUID(), enrollment = UUID.randomUUID(), row = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), teacher, unit);
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "T" + klass, "t" + klass, modality, teacher);
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 15000, 'ACTIVE')",
                plan, t.orgId(), unit, modality, "P" + plan, "p" + plan);
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, ?, ?, date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", student, t.orgId(), unit, name, Modalities.normalize(name));
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at) values (?, ?, ?, ?, ?, ?, ?, ?, 10, 'ACTIVE', now())", row, t.orgId(), enrollment, student, unit, modality, klass, plan);
        return new Fx(student, row, teacher);
    }

    private MockHttpServletResponse post(String path, Object body, AccountPrincipal who) throws Exception {
        var req = MockMvcRequestBuilders.post("/api/v1/benefits" + path).contentType("application/json");
        if (body != null) req.content(json.writeValueAsString(body));
        return call(req, who).getResponse();
    }

    private UUID grant(Fx fx, AccountPrincipal c, UUID teacherId) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", fx.student());
        m.put("modalityRowId", fx.row());
        m.put("type", "PERCENT");
        m.put("value", 30);
        m.put("reason", "Bolsa social");
        m.put("decidedBy", "Mestre Silva");
        if (teacherId != null) m.put("teacherId", teacherId);
        var r = post("", m, c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(json.readTree(r.getContentAsString()).get("id").asText());
    }

    private MockHttpServletResponse end(UUID id, String reason, AccountPrincipal who) throws Exception {
        return post("/" + id + "/end", Map.of("reason", reason == null ? "" : reason), who);
    }

    private int audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = ?", Integer.class, t.orgId(), action);
    }

    private String status(UUID id) {
        return adminJdbc().queryForObject("select status from benefit where id = ?", String.class, id);
    }

    @Test
    @DisplayName("CA1, CA6: encerra com 200, grava motivo e auditoria com unidade, não mexe na matrícula e permite conceder outro sem replaceExisting")
    void ends() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana");
        var c = account(t, Role.COORDINATOR);
        UUID id = grant(fx, c, null);
        String before = adminJdbc().queryForObject("select em::text from enrollment_modality em where id = ?", String.class, fx.row());
        var r = end(id, "Mudou de situação", c);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        JsonNode row = json.readTree(r.getContentAsString());
        assertThat(row.get("status").asText()).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select end_reason from benefit where id = ? and ended_at is not null", String.class, id)).isEqualTo("Mudou de situação");
        assertThat(adminJdbc().queryForObject("select em::text from enrollment_modality em where id = ?", String.class, fx.row())).isEqualTo(before);
        assertThat(audits(t, "benefit.ended")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select unit_id from audit_log where organization_id = ? and action = 'benefit.ended'", UUID.class, t.orgId())).isEqualTo(t.unitId());
        assertThat(adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'benefit.ended'", String.class, t.orgId()))
                .contains("ENDED", "Mudou de situação");
        grant(fx, c, null);
        assertThat(end(id, "Outra vez", c).getStatus()).as("CA3").isEqualTo(409);
    }

    @Test
    @DisplayName("CA2, CA6: motivo inválido ou corpo ausente dão 400 com o campo; 2 e 200 caracteres passam; nada audita nas recusas")
    void validation() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana");
        var c = account(t, Role.COORDINATOR);
        UUID id = grant(fx, c, null);
        for (String bad : new String[] {"", " ", "x", "x".repeat(201)}) {
            var r = end(id, bad, c);
            assertThat(r.getStatus()).as(bad).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("reason");
        }
        assertThat(post("/" + id + "/end", null, c).getStatus()).isEqualTo(400);
        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(audits(t, "benefit.ended")).isZero();
        assertThat(end(id, "ab", c).getStatus()).isEqualTo(200);
        UUID second = grant(fx, c, null);
        assertThat(end(second, "y".repeat(200), c).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA3: benefício vencido encerra com 200")
    void expired() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana");
        var c = account(t, Role.COORDINATOR);
        UUID id = grant(fx, c, null);
        adminJdbc().update("update benefit set valid_until = current_date - 5 where id = ?", id);
        assertThat(end(id, "Vencido", c).getStatus()).isEqualTo(200);
        assertThat(status(id)).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("CA4, CA5, CA6: perfis, alcance do Administrador, 404 e isolamento entre organizações; recusas não mudam nem auditam")
    void permissionsAndIsolation() throws Exception {
        var t = tenant();
        UUID other = unit(t, "Zona Sul");
        var mine = enrolled(t, t.unitId(), "Minha");
        var foreign = enrolled(t, other, "Alheia");
        var c = account(t, Role.COORDINATOR);
        UUID idMine = grant(mine, c, null), idForeign = grant(foreign, c, null);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(end(idMine, "Motivo", who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(end(idMine, "Motivo", null).getStatus()).isEqualTo(401);
        var admin = account(t, Role.ADMINISTRATOR);
        assertThat(end(idForeign, "Motivo", admin).getStatus()).isEqualTo(403);
        assertThat(end(UUID.randomUUID(), "Motivo", c).getStatus()).isEqualTo(404);
        var b = tenant();
        assertThat(end(idMine, "Motivo", account(b, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(status(idMine)).isEqualTo("ACTIVE");
        assertThat(status(idForeign)).isEqualTo("ACTIVE");
        assertThat(audits(t, "benefit.ended")).isZero();
        assertThat(end(idMine, "Motivo", admin).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA7: dois encerramentos simultâneos dão um 200, um 409 e uma só auditoria")
    void concurrent() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana");
        var c = account(t, Role.COORDINATOR);
        UUID id = grant(fx, c, null);
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return end(id, "Motivo", c).getStatus();
            }));
        }
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (var f : results) statuses.add(f.get());
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(audits(t, "benefit.ended")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA8, CA9: teacherId de professor ativo é gravado; inativo ou de outra organização dá 404; inativar o professor encerra só o desconto dele")
    void teacherDiscount() throws Exception {
        var t = tenant();
        var fx = enrolled(t, t.unitId(), "Ana");
        var plain = enrolled(t, t.unitId(), "Beto");
        var c = account(t, Role.COORDINATOR);
        var b = tenant();
        var foreignTeacher = enrolled(b, b.unitId(), "Zé").teacher();
        Map<String, Object> m = new LinkedHashMap<>(Map.of("studentId", fx.student(), "modalityRowId", fx.row(), "type", "PERCENT", "value", 30,
                "reason", "Bolsa", "decidedBy", "Mestre Silva", "teacherId", foreignTeacher));
        assertThat(post("", m, c).getStatus()).isEqualTo(404);
        adminJdbc().update("update teacher set status = 'INACTIVE' where id = ?", fx.teacher());
        m.put("teacherId", fx.teacher());
        assertThat(post("", m, c).getStatus()).isEqualTo(404);
        assertThat(adminJdbc().queryForObject("select count(*) from benefit", Integer.class)).isZero();
        adminJdbc().update("update teacher set status = 'ACTIVE' where id = ?", fx.teacher());
        UUID teacherBenefit = grant(fx, c, fx.teacher());
        UUID plainBenefit = grant(plain, c, null);
        assertThat(adminJdbc().queryForObject("select teacher_id from benefit where id = ?", UUID.class, teacherBenefit)).isEqualTo(fx.teacher());
        var r = call(MockMvcRequestBuilders.post("/api/v1/teachers/" + fx.teacher() + "/deactivate"), c).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(status(teacherBenefit)).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select end_reason from benefit where id = ?", String.class, teacherBenefit)).isEqualTo("Professor inativado");
        assertThat(status(plainBenefit)).isEqualTo("ACTIVE");
        assertThat(audits(t, "benefit.ended")).isEqualTo(1);
    }
}
