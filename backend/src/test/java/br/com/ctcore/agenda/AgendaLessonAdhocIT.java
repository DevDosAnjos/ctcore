package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-AGE-04: POST /agenda/lessons (aula avulsa) e a leitura, edição e cancelamento dela, com banco real. */
class AgendaLessonAdhocIT extends AgendaBase {
    @Autowired ObjectMapper json;

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    /** Outros testes contam a auditoria de edição e cancelamento no banco inteiro: esta classe devolve o que gravou. */
    @org.junit.jupiter.api.AfterEach
    void cleanAudit() {
        adminJdbc().execute("alter table audit_log disable trigger user");
        adminJdbc().update("delete from audit_log where action in ('lesson.created', 'lesson.updated', 'lesson.cancelled')");
        adminJdbc().execute("alter table audit_log enable trigger user");
    }

    private Map<String, Object> body(Tenant t, UUID modality, UUID teacher) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitId", t.unitId().toString());
        m.put("title", "Seminário de quedas");
        m.put("modalityId", modality.toString());
        m.put("date", TODAY.plusDays(3).toString());
        m.put("startsAt", "10:00");
        m.put("endsAt", "11:30");
        m.put("teacherId", teacher.toString());
        return m;
    }

    private MockHttpServletResponse post(Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/agenda/lessons").contentType("application/json").content(json.writeValueAsString(body)), who)
                .getResponse();
    }

    private MockHttpServletResponse send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Object body, AccountPrincipal who)
            throws Exception {
        return call(b.contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    private JsonNode created(MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return json.readTree(r.getContentAsString());
    }

    private String field(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).path("fields").path(0).path("field").asText();
    }

    private int count(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from lesson where organization_id = ?", Integer.class, t.orgId());
    }

    private MockHttpServletResponse get(String path, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get(path), who).getResponse();
    }

    private AccountPrincipal teacherPrincipal(Tenant t, UUID account) {
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(t.unitId()));
    }

    @Test
    @DisplayName("CA1, CA2: Coordenador cria para toda a unidade ou para uma turma; auditoria com ids")
    void createsAndAudits() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?)", t.orgId(), ana, judo);
        UUID turma = klass(t, "Turma", judo, ana, "ACTIVE");
        var coord = who(t, Role.COORDINATOR);

        JsonNode d = created(post(body(t, judo, ana), coord));
        assertThat(d.get("class").isNull()).isTrue();
        assertThat(d.get("title").asText()).isEqualTo("Seminário de quedas");
        assertThat(d.get("modality").get("name").asText()).isEqualTo("Judô");
        assertThat(d.get("teacher").get("name").asText()).isEqualTo("Ana");
        assertThat(d.get("visibilityClass").isNull()).isTrue();
        assertThat(d.get("substitute").asBoolean()).isFalse();
        assertThat(d.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(d.get("head").isNull()).isTrue();
        assertThat(d.get("canViewClass").asBoolean()).isFalse();
        assertThat(d.get("activeStudents").asInt()).isZero();
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'lesson.created' and entity_id = ?", String.class,
                d.get("id").asText());
        assertThat(after).contains(judo.toString(), ana.toString(), "10:00");

        var b = body(t, judo, ana);
        b.put("visibilityClassId", turma.toString());
        assertThat(created(post(b, coord)).get("visibilityClass").get("name").asText()).isEqualTo("Turma");

        // CA6: leitura na agenda, com filtros, e no painel
        String q = "/api/v1/agenda?from=" + TODAY + "&to=" + TODAY.plusDays(6) + "&unitId=" + t.unitId();
        JsonNode lessons = json.readTree(get(q + "&modalityId=" + judo + "&teacherId=" + ana, coord).getContentAsString()).get("lessons");
        assertThat(lessons).hasSize(2);
        assertThat(lessons.get(0).get("class").isNull()).isTrue();
        assertThat(lessons.get(0).get("title").asText()).isEqualTo("Seminário de quedas");
        assertThat(json.readTree(get(q + "&modalityId=" + UUID.randomUUID(), coord).getContentAsString()).get("lessons")).isEmpty();
    }

    @Test
    @DisplayName("CA2, CA3, CA4: validações com o campo e nada gravado")
    void validation() throws Exception {
        var t = tenant();
        var other = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID inactive = teacher(t, "Inativa", null);
        UUID semModalidade = teacher(t, "Sem", null);
        UUID judo = modality(t, "Judô");
        UUID jiu = modality(t, "Jiu-Jitsu");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?), (?, ?, ?)", t.orgId(), ana, judo,
                t.orgId(), inactive, judo);
        adminJdbc().update("update teacher set status = 'INACTIVE' where id = ?", inactive);
        UUID turmaOff = klass(t, "Off", judo, ana, "INACTIVE");
        UUID otherTeacher = teacher(other, "Beto", null);
        UUID otherModality = modality(other, "Muay Thai");
        UUID otherClass = klass(other, "Outra", otherModality, otherTeacher, "ACTIVE");
        var coord = who(t, Role.COORDINATOR);

        record Case(String name, String key, Object value, int status, String field) {}
        Case[] cases = {
            new Case("título vazio", "title", " ", 400, "title"),
            new Case("título longo", "title", "x".repeat(81), 400, "title"),
            new Case("fim antes do início", "endsAt", "09:00", 400, "endsAt"),
            new Case("início no passado", "date", TODAY.minusDays(1).toString(), 400, "date"),
            new Case("data ruim", "date", "31/12", 400, "date"),
            new Case("horário ruim", "startsAt", "25:00", 400, "startsAt"),
            new Case("sem professor", "teacherId", null, 400, "teacherId"),
            new Case("modalidade não oferecida", "modalityId", UUID.randomUUID().toString(), 422, "modalityId"),
            new Case("modalidade de outra organização", "modalityId", otherModality.toString(), 422, "modalityId"),
            new Case("professor inativo", "teacherId", inactive.toString(), 422, "teacherId"),
            new Case("professor que não ensina", "teacherId", semModalidade.toString(), 422, "teacherId"),
            new Case("professor de outra organização", "teacherId", otherTeacher.toString(), 422, "teacherId"),
            new Case("turma desativada", "visibilityClassId", turmaOff.toString(), 422, "visibilityClassId"),
            new Case("turma de outra organização", "visibilityClassId", otherClass.toString(), 404, ""),
        };
        for (Case c : cases) {
            var b = body(t, judo, ana);
            b.put(c.key(), c.value());
            MockHttpServletResponse r = post(b, coord);
            assertThat(r.getStatus()).as(c.name() + " " + r.getContentAsString()).isEqualTo(c.status());
            if (!c.field().isEmpty()) assertThat(field(r)).as(c.name()).isEqualTo(c.field());
        }
        var otherUnit = body(t, judo, ana);
        otherUnit.put("unitId", other.unitId().toString());
        assertThat(post(otherUnit, coord).getStatus()).isEqualTo(404);
        var jiuBody = body(t, jiu, ana); // professor que não ensina Jiu-Jitsu
        assertThat(field(post(jiuBody, coord))).isEqualTo("teacherId");
        assertThat(count(t)).isZero();

        // professor de outra unidade que ensina a modalidade é aceito
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Zeta', ?)", second, t.orgId(), "z" + second.toString().substring(0, 12));
        adminJdbc().update("update teacher_unit set unit_id = ? where teacher_id = ?", second, ana);
        created(post(body(t, judo, ana), coord));
    }

    @Test
    @DisplayName("CA5, CA6, CA7: Professor só cria para si, vê e edita a sua; THIS vale, FOLLOWING não; cancela")
    void teacherAndWrites() throws Exception {
        var t = tenant();
        UUID acc = account("ana");
        UUID ana = teacher(t, "Ana", acc);
        UUID beto = teacher(t, "Beto", account("beto"));
        UUID judo = modality(t, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?), (?, ?, ?)", t.orgId(), ana, judo,
                t.orgId(), beto, judo);
        var me = teacherPrincipal(t, acc);

        JsonNode d = created(post(body(t, judo, ana), me));
        String id = d.get("id").asText();
        assertThat(post(body(t, judo, beto), me).getStatus()).isEqualTo(403);
        assertThat(get("/api/v1/agenda/lessons/" + id, me).getStatus()).isEqualTo(200);
        assertThat(d.get("canEdit").asBoolean()).isTrue();
        assertThat(get("/api/v1/agenda/lessons/" + id, teacherPrincipal(t, adminJdbc().queryForObject("select account_id from teacher where id = ?",
                UUID.class, beto))).getStatus()).isEqualTo(404);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("scope", "THIS");
        edit.put("date", TODAY.plusDays(4).toString());
        edit.put("startsAt", "11:00");
        edit.put("endsAt", "12:00");
        edit.put("teacherId", beto.toString());
        var coord = who(t, Role.COORDINATOR);
        var put = MockMvcRequestBuilders.put("/api/v1/agenda/lessons/" + id);
        MockHttpServletResponse r = send(put, edit, coord);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        JsonNode e = json.readTree(r.getContentAsString());
        assertThat(e.get("substitute").asBoolean()).isFalse();
        assertThat(e.get("teacher").get("name").asText()).isEqualTo("Beto");
        assertThat(e.get("startsAt").asText()).isEqualTo("11:00");
        edit.put("scope", "FOLLOWING");
        assertThat(send(MockMvcRequestBuilders.put("/api/v1/agenda/lessons/" + id), edit, coord).getStatus()).isEqualTo(400);

        var cancel = send(MockMvcRequestBuilders.post("/api/v1/agenda/lessons/" + id + "/cancel"), Map.of("reason", "HOLIDAY"), coord);
        assertThat(cancel.getStatus()).as(cancel.getContentAsString()).isEqualTo(200);
        assertThat(json.readTree(cancel.getContentAsString()).get("status").asText()).isEqualTo("CANCELLED");

        // sem AGENDA_EDIT
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,false,true,true}' where unit_id = ?", t.unitId());
        assertThat(post(body(t, judo, ana), me).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA5, CA8: Administrador fora do alcance 404; Aluno e Responsável 403; sem sessão 401; RLS recusa sem escopo")
    void accessAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID ana = teacher(a, "Ana", null);
        UUID judo = modality(a, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?)", a.orgId(), ana, judo);
        var outsider = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), a.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        assertThat(post(body(a, judo, ana), outsider).getStatus()).isEqualTo(404);
        assertThat(post(body(a, judo, ana), who(a, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(post(body(a, judo, ana), who(a, Role.GUARDIAN)).getStatus()).isEqualTo(403);
        assertThat(post(body(a, judo, ana), null).getStatus()).isEqualTo(401);
        assertThat(post(body(b, judo, ana), who(a, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        created(post(body(a, judo, ana), who(a, Role.ADMINISTRATOR)));
        assertThat(count(b)).isZero();

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            try {
                sql.execute("insert into lesson (id, organization_id, unit_id, lesson_date, starts_at, ends_at, teacher_id, title, modality_id) values ('"
                        + UUID.randomUUID() + "', '" + a.orgId() + "', '" + a.unitId() + "', current_date, '10:00', '11:00', '" + ana + "', 'x', '" + judo + "')");
                throw new AssertionError("RLS deveria recusar");
            } catch (SQLException expected) {
                assertThat(expected.getMessage()).contains("row-level security");
            } finally {
                app.rollback();
            }
        }
    }

    @Test
    @DisplayName("CA9: falha na auditoria desfaz a aula")
    void allOrNothing() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?)", t.orgId(), ana, judo);
        adminJdbc().execute("create function adhoc_fail() returns trigger language plpgsql as $$ begin raise exception 'boom'; end $$");
        adminJdbc().execute("create trigger adhoc_fail before insert on audit_log for each row when (new.action = 'lesson.created') execute function adhoc_fail()");
        try {
            assertThat(post(body(t, judo, ana), who(t, Role.COORDINATOR)).getStatus()).isGreaterThanOrEqualTo(500);
            assertThat(count(t)).isZero();
        } finally {
            adminJdbc().execute("drop trigger adhoc_fail on audit_log");
            adminJdbc().execute("drop function adhoc_fail()");
        }
    }
}
