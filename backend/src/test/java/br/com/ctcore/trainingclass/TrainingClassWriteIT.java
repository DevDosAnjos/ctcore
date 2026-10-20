package br.com.ctcore.trainingclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-TUR-03: criar e editar turma e opções do formulário, com banco real. */
class TrainingClassWriteIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean ClassScheduleChange scheduleChange;
    @MockitoSpyBean TrainingClasses classes;

    @AfterEach
    void clean() {
        reset(scheduleChange, classes);
        adminJdbc().update("delete from training_class_assistant");
        adminJdbc().update("delete from training_class_schedule");
        adminJdbc().update("delete from lesson");
        adminJdbc().update("delete from training_class");
        adminJdbc().update("delete from teacher_modality");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
        adminJdbc().update("delete from unit_modality");
        adminJdbc().update("delete from organization_modality");
    }

    private MockHttpServletResponse post(Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/classes").contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    private MockHttpServletResponse put(UUID id, Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.put("/api/v1/classes/" + id).contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    private MockHttpServletResponse options(AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/classes/options"), who).getResponse();
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

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID teacher(Tenant t, String name, UUID unit, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, status) values (?, ?, ?, ?, ?)", id, t.orgId(), name, Modalities.normalize(name), status);
        if (unit != null) adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        return id;
    }

    private UUID age(Tenant t) {
        return adminJdbc().queryForObject("select id from age_range where organization_id = ? and name = 'Adulto'", UUID.class, t.orgId());
    }

    private Map<String, Object> slot(int day, String from, String to) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weekday", day);
        m.put("startsAt", from);
        m.put("endsAt", to);
        return m;
    }

    /** Corpo válido: Judô oferecido na unidade, titular ativo da unidade, um horário. */
    private Map<String, Object> valid(Tenant t, String name) {
        UUID m = modality(t, t.unitId(), "Judô", true, true);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("unitId", t.unitId());
        b.put("name", name);
        b.put("modalityId", m);
        b.put("schedule", List.of(slot(3, "19:00", "20:00")));
        b.put("headTeacherId", teacherOf(t, "Titular " + name, t.unitId()));
        return b;
    }

    private UUID teacherOf(Tenant t, String name, UUID unit) {
        UUID id = teacher(t, name, unit, "ACTIVE");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id, level_id, degree) values (?, ?, ?, null, 0)",
                t.orgId(), id, adminJdbc().queryForObject("select id from modality where name = 'Judô'", UUID.class));
        return id;
    }

    private UUID created(Tenant t, String name) throws Exception {
        var r = post(valid(t, name), who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(r).get("id").asText());
    }

    private void assertField(MockHttpServletResponse r, int status, String field) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        assertThat(body(r).get("fields").findValuesAsText("field")).contains(field);
    }

    private int classCount(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from training_class where organization_id = ?", Integer.class, t.orgId());
    }

    @Test
    @DisplayName("RN-TUR-05, RN-PRO-09: titular e auxiliares têm de ensinar a modalidade (400 no campo); turma antiga com titular que não ensina exige troca ao editar; opções trazem as modalidades de cada professor")
    void teachersMustTeachTheModality() throws Exception {
        var t = tenant();
        var coordinator = who(t, Role.COORDINATOR);
        UUID outsider = teacher(t, "Fora", t.unitId(), "ACTIVE");
        var asHead = valid(t, "Turma A");
        asHead.put("headTeacherId", outsider);
        assertField(post(asHead, coordinator), 400, "headTeacherId");
        var asAssistant = valid(t, "Turma B");
        asAssistant.put("assistantIds", List.of(outsider));
        assertField(post(asAssistant, coordinator), 400, "assistantIds");
        assertThat(classCount(t)).isZero();

        var ok = valid(t, "Turma C");
        ok.put("assistantIds", List.of(teacherOf(t, "Aux", t.unitId())));
        var r = post(ok, coordinator);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        UUID id = UUID.fromString(body(r).get("id").asText());

        UUID head = UUID.fromString(ok.get("headTeacherId").toString());
        adminJdbc().update("delete from teacher_modality where teacher_id = ?", head);
        assertField(put(id, ok, coordinator), 400, "headTeacherId");
        ok.put("headTeacherId", teacherOf(t, "Novo titular", t.unitId()));
        assertThat(put(id, ok, coordinator).getStatus()).isEqualTo(200);
        ok.put("assistantIds", List.of(outsider));
        assertField(put(id, ok, coordinator), 400, "assistantIds");

        var teachers = body(options(coordinator)).get("units").get(0).get("teachers");
        UUID judo = adminJdbc().queryForObject("select id from modality where name = 'Judô'", UUID.class);
        for (var n : teachers) {
            boolean teaches = toList(n.get("modalityIds")).contains(judo.toString());
            assertThat(teaches).as(n.get("name").asText()).isEqualTo(!List.of("Fora", "Titular Turma C").contains(n.get("name").asText()));
        }
    }

    private static List<String> toList(JsonNode ids) {
        List<String> out = new ArrayList<>();
        ids.forEach(i -> out.add(i.asText()));
        return out;
    }

    @Test
    @DisplayName("CA1, CA10, CA11: Coordenador cria com todos os campos; aparece na lista; auditoria sem o link; sem presença")
    void create() throws Exception {
        var t = tenant();
        var b = valid(t, "Judô Infantil");
        UUID beto = teacherOf(t, "Beto", t.unitId());
        UUID caio = teacherOf(t, "Caio", t.unitId());
        b.put("schedule", List.of(slot(5, "18:00", "19:00"), slot(2, "17:00", "18:00")));
        b.put("assistantIds", List.of(beto, caio));
        b.put("ageRangeId", age(t));
        b.put("capacity", 25);
        b.put("groupLink", "https://chat.example.com/segredo");
        var r = post(b, who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        var d = body(r);
        assertThat(d.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.get("schedule").findValues("weekday").stream().map(JsonNode::asInt)).containsExactly(2, 5);
        assertThat(d.get("assistants")).hasSize(2);
        assertThat(d.get("capacity").asInt()).isEqualTo(25);
        assertThat(d.has("scheduleChanged")).isFalse();
        var list = body(call(MockMvcRequestBuilders.get("/api/v1/classes"), who(t, Role.COORDINATOR)).getResponse());
        assertThat(list.get("items").findValuesAsText("name")).contains("Judô Infantil");
        String audit = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'class.created'", String.class, t.orgId());
        assertThat(audit).contains("Judô Infantil").doesNotContain("segredo").doesNotContain("chat.example.com");
        assertThat(adminJdbc().queryForObject("select count(*) from information_schema.columns where table_name like 'training_class%' and "
                + "(column_name ilike '%presen%' or column_name ilike '%frequen%' or column_name ilike '%attend%')", Integer.class)).isZero();
        assertThat(r.getContentAsString()).doesNotContainIgnoringCase("presen").doesNotContainIgnoringCase("frequen");
    }

    @Test
    @DisplayName("CA2: entradas inválidas dão 400 com o campo e não gravam; limites válidos salvam")
    void validation() throws Exception {
        var t = tenant();
        var who = who(t, Role.COORDINATOR);
        Map<String, Object> b;
        for (String name : new String[] {"A", "x".repeat(81), "   "}) {
            b = valid(t, "ok");
            b.put("name", name);
            assertField(post(b, who), 400, "name");
        }
        b = valid(t, "ok1");
        b.put("schedule", List.of());
        assertField(post(b, who), 400, "schedule");
        b = valid(t, "ok2");
        b.put("schedule", List.of(slot(1, "10:00", "11:00"), slot(1, "12:00", "13:00")));
        assertField(post(b, who), 400, "schedule");
        for (int day : new int[] {0, 8}) {
            b = valid(t, "ok3");
            b.put("schedule", List.of(slot(day, "10:00", "11:00")));
            assertField(post(b, who), 400, "schedule");
        }
        for (String[] range : new String[][] {{"10:00", "10:00"}, {"11:00", "10:00"}, {"25:00", "26:00"}, {"9h", "10h"}}) {
            b = valid(t, "ok4");
            b.put("schedule", List.of(slot(1, range[0], range[1])));
            assertField(post(b, who), 400, "schedule");
        }
        for (int cap : new int[] {0, 201}) {
            b = valid(t, "ok5");
            b.put("capacity", cap);
            assertField(post(b, who), 400, "capacity");
        }
        for (String link : new String[] {"javascript:alert(1)", "chat.example.com/x", "ftp://x.com/a", " https://a.com", "https://a.com/" + "x".repeat(300)}) {
            b = valid(t, "ok6");
            b.put("groupLink", link);
            assertField(post(b, who), 400, "groupLink");
        }
        b = valid(t, "ok7");
        b.remove("headTeacherId");
        assertField(post(b, who), 400, "headTeacherId");
        b = valid(t, "ok8");
        b.remove("unitId");
        assertField(post(b, who), 400, "unitId");
        assertThat(classCount(t)).isZero();
        b = valid(t, "Capacidade 1");
        b.put("capacity", 1);
        b.put("groupLink", "");
        var r = post(b, who);
        assertThat(r.getStatus()).isEqualTo(201);
        assertThat(body(r).get("groupLink").isNull()).isTrue();
        b = valid(t, "Capacidade 200");
        b.put("capacity", 200);
        assertThat(post(b, who).getStatus()).isEqualTo(201);
        b = valid(t, "Sem capacidade");
        assertThat(body(post(b, who)).get("capacity").isNull()).isTrue();
    }

    @Test
    @DisplayName("RN-TUR-01: CA3: nome único na unidade entre ativas, sem caixa nem acento; outra unidade, desativada e editar a si mesma passam")
    void uniqueName() throws Exception {
        var t = tenant();
        var who = who(t, Role.COORDINATOR);
        UUID first = created(t, "Judô Infantil");
        assertField(post(valid(t, "judo infantil"), who), 409, "name");
        assertThat(classCount(t)).isEqualTo(1);
        UUID other = unit(t, "Zona Sul");
        var b = valid(t, "Judô Infantil");
        modality(t, other, "Judô", true, true);
        b.put("unitId", other);
        b.put("headTeacherId", teacherOf(t, "Outro", other));
        assertThat(post(b, who).getStatus()).isEqualTo(201);
        b = valid(t, "Outro nome");
        b.put("schedule", List.of(slot(3, "19:00", "20:00")));
        assertThat(post(b, who).getStatus()).as("mesma modalidade e horário").isEqualTo(201);
        var edit = valid(t, "Judô Infantil");
        assertThat(put(first, edit, who).getStatus()).isEqualTo(200);
        adminJdbc().update("update training_class set status = 'INACTIVE' where id = ?", first);
        assertThat(post(valid(t, "Judô Infantil"), who).getStatus()).as("igual a desativada").isEqualTo(201);
        UUID second = created(t, "Segunda");
        assertField(put(second, valid(t, "JUDÔ INFANTIL"), who), 409, "name");
    }

    @Test
    @DisplayName("RN-TUR-05: CA4: titular e auxiliares ativos da unidade e da organização; sem auxiliares salva")
    void teachers() throws Exception {
        var t = tenant();
        var o = tenant();
        var who = who(t, Role.COORDINATOR);
        UUID elsewhere = unit(t, "Zona Sul");
        for (UUID bad : new UUID[] {teacher(t, "Inativo", t.unitId(), "INACTIVE"), teacher(t, "Outra unidade", elsewhere, "ACTIVE"),
                teacher(t, "Sem unidade", null, "ACTIVE"), teacher(o, "Alheio", o.unitId(), "ACTIVE"), UUID.randomUUID()}) {
            var b = valid(t, "T1");
            b.put("headTeacherId", bad);
            assertField(post(b, who), 400, "headTeacherId");
            b = valid(t, "T2");
            b.put("assistantIds", List.of(bad));
            assertField(post(b, who), 400, "assistantIds");
        }
        var b = valid(t, "T3");
        b.put("assistantIds", List.of(b.get("headTeacherId")));
        assertField(post(b, who), 400, "assistantIds");
        b = valid(t, "T4");
        UUID aux = teacherOf(t, "Aux", t.unitId());
        b.put("assistantIds", List.of(aux, aux));
        assertField(post(b, who), 400, "assistantIds");
        assertThat(post(valid(t, "T5"), who).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("RN-TUR-01: CA5: modalidade ativa e oferecida pela unidade; faixa da organização; sem faixa salva")
    void modalityAndAge() throws Exception {
        var t = tenant();
        var o = tenant();
        var who = who(t, Role.COORDINATOR);
        UUID inactive = modality(t, t.unitId(), "Boxe", false, true);
        UUID notOffered = modality(t, t.unitId(), "Karatê", true, false);
        UUID foreign = modality(o, o.unitId(), "MMA", true, true);
        for (UUID m : new UUID[] {inactive, notOffered, foreign}) {
            var b = valid(t, "M1");
            b.put("modalityId", m);
            assertField(post(b, who), 400, "modalityId");
        }
        var b = valid(t, "M2");
        b.put("ageRangeId", age(o));
        assertField(post(b, who), 400, "ageRangeId");
        assertThat(post(valid(t, "M3"), who).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA6: PUT substitui horários e auxiliares; unidade e situação ficam; capacidade abaixo dos alunos salva; desativada 409")
    void update() throws Exception {
        var t = tenant();
        var who = who(t, Role.COORDINATOR);
        var b = valid(t, "Original");
        b.put("assistantIds", List.of(teacherOf(t, "Aux 1", t.unitId())));
        b.put("schedule", List.of(slot(1, "10:00", "11:00"), slot(3, "10:00", "11:00")));
        b.put("groupLink", "https://a.example.com/x");
        UUID id = UUID.fromString(body(post(b, who)).get("id").asText());

        UUID newHead = teacherOf(t, "Novo titular", t.unitId());
        UUID aux2 = teacherOf(t, "Aux 2", t.unitId());
        doReturn(9).when(classes).activeStudents(id);
        var e = valid(t, "Renomeada");
        e.put("unitId", UUID.randomUUID()); // ignorado: a unidade não muda
        e.put("headTeacherId", newHead);
        e.put("assistantIds", List.of(aux2));
        e.put("schedule", List.of(slot(6, "09:00", "10:30")));
        e.put("capacity", 5);
        e.put("groupLink", "https://b.example.com/y");
        var r = put(id, e, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        var d = body(r);
        assertThat(d.get("name").asText()).isEqualTo("Renomeada");
        assertThat(d.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(d.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.get("head").get("id").asText()).isEqualTo(newHead.toString());
        assertThat(d.get("assistants").findValuesAsText("id")).containsExactly(aux2.toString());
        assertThat(d.get("schedule")).hasSize(1);
        assertThat(d.get("groupLink").asText()).isEqualTo("https://b.example.com/y");
        assertThat(d.get("capacity").asInt()).isEqualTo(5);
        assertThat(d.get("overCapacity").asBoolean()).isTrue();
        assertThat(adminJdbc().queryForObject("select count(*) from training_class_schedule where class_id = ?", Integer.class, id)).isEqualTo(1);

        adminJdbc().update("update training_class set status = 'INACTIVE' where id = ?", id);
        var again = put(id, valid(t, "Outra"), who);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(adminJdbc().queryForObject("select name from training_class where id = ?", String.class, id)).isEqualTo("Renomeada");
    }

    @Test
    @DisplayName("CA7, CA10: horários alterados chamam o ponto único uma vez e a auditoria traz notifyClass; iguais não chamam")
    void scheduleChanged() throws Exception {
        var t = tenant();
        var who = who(t, Role.COORDINATOR);
        UUID id = created(t, "Turma");
        var same = valid(t, "Turma");
        same.put("groupLink", "https://segredo.example.com/z");
        var r = put(id, same, who);
        assertThat(body(r).get("scheduleChanged").asBoolean()).isFalse();
        verify(scheduleChange, never()).changed(any(), any(), any(), any(), any());

        var changed = valid(t, "Turma");
        changed.put("schedule", List.of(slot(3, "19:00", "20:00"), slot(5, "19:00", "20:00")));
        changed.put("notifyClass", false);
        r = put(id, changed, who);
        assertThat(body(r).get("scheduleChanged").asBoolean()).isTrue();
        verify(scheduleChange, times(1)).changed(eq(id),
                eq(List.of(new TrainingClasses.Slot(3, "19:00", "20:00"))),
                eq(List.of(new TrainingClasses.Slot(3, "19:00", "20:00"), new TrainingClasses.Slot(5, "19:00", "20:00"))), any(), any());
        List<String> audits = adminJdbc().queryForList("select after_data::text from audit_log where organization_id = ? and action = 'class.updated' order by occurred_at",
                String.class, t.orgId());
        assertThat(audits).hasSize(2);
        assertThat(audits.get(1)).contains("notifyClass").contains("schedule");
        assertThat(String.join("", audits)).doesNotContain("segredo");
    }

    @Test
    @DisplayName("CA8: Professor, Aluno e Responsável 403; anônimo 401; Administrador só na unidade dele")
    void permissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID id = created(t, "Existente");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var w = who(t, role);
            assertThat(post(valid(t, "X" + role), w).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(put(id, valid(t, "Existente"), w).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(options(w).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(post(valid(t, "Anon"), null).getStatus()).isEqualTo(401);
        assertThat(put(id, valid(t, "Existente"), null).getStatus()).isEqualTo(401);
        assertThat(options(null).getStatus()).isEqualTo(401);
        var admin = who(t, Role.ADMINISTRATOR);
        modality(t, second, "Judô", true, true);
        var far = valid(t, "Fora");
        far.put("unitId", second);
        far.put("headTeacherId", teacherOf(t, "Longe", second));
        assertThat(post(far, admin).getStatus()).isEqualTo(403);
        assertThat(post(valid(t, "Dentro"), admin).getStatus()).isEqualTo(201);
        assertThat(post(far, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(201);
        UUID inSecond = adminJdbc().queryForObject("select id from training_class where name = 'Fora'", UUID.class);
        assertThat(put(inSecond, far, admin).getStatus()).isEqualTo(404);
        assertThat(put(inSecond, far, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        assertThat(put(id, valid(t, "Existente"), admin).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA9: referências e turmas de outra organização; RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var ca = who(a, Role.COORDINATOR);
        UUID theirs = created(b, "Alheia");
        var body = valid(a, "Invasora");
        body.put("unitId", b.unitId());
        assertField(post(body, ca), 400, "unitId");
        assertThat(put(theirs, valid(a, "Invasora"), ca).getStatus()).isEqualTo(404);
        assertThat(adminJdbc().queryForObject("select name from training_class where id = ?", String.class, theirs)).isEqualTo("Alheia");
        assertThat(classCount(a)).isZero();
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from training_class")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            assertThat(sql.executeUpdate("update training_class set name = 'x'")).isZero();
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA12: opções só das unidades do alcance, com modalidades oferecidas e professores ativos")
    void optionsScope() throws Exception {
        var t = tenant();
        UUID u2 = unit(t, "Zona Sul");
        UUID u3 = unit(t, "Zona Norte");
        UUID off = unit(t, "Desativada");
        adminJdbc().update("update unit set active = false where id = ?", off);
        modality(t, t.unitId(), "Judô", true, true);
        modality(t, t.unitId(), "Boxe", true, false);
        modality(t, t.unitId(), "Karatê", false, true);
        modality(t, u2, "Judô", true, true);
        teacherOf(t, "Ativo", t.unitId());
        teacher(t, "Inativo", t.unitId(), "INACTIVE");
        teacher(t, "Da outra", u3, "ACTIVE");
        var admin = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, java.util.Set.of(t.unitId(), u2));
        var d = body(options(admin));
        assertThat(d.get("units")).hasSize(2);
        var mine = d.get("units").get(0).get("id").asText().equals(t.unitId().toString()) ? d.get("units").get(0) : d.get("units").get(1);
        assertThat(mine.get("modalities").findValuesAsText("name")).containsExactly("Judô");
        assertThat(mine.get("teachers").findValuesAsText("name")).containsExactly("Ativo");
        assertThat(d.get("ageRanges")).isNotEmpty();
        var all = body(options(who(t, Role.COORDINATOR)));
        List<String> ids = new ArrayList<>();
        all.get("units").forEach(u -> ids.add(u.get("id").asText()));
        assertThat(ids).containsExactlyInAnyOrder(t.unitId().toString(), u2.toString(), u3.toString());
    }
}
