package br.com.ctcore.trainingclass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-TUR-02: painel da turma e desativação (RN-TUR-03), com banco real e os pontos únicos de alunos substituídos quando preciso. */
class TrainingClassDetailIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean TrainingClasses classes;

    @AfterEach
    void clean() {
        reset(classes);
        adminJdbc().update("delete from training_class_assistant");
        adminJdbc().update("delete from training_class_schedule");
        adminJdbc().update("delete from lesson");
        adminJdbc().update("delete from training_class");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
    }

    private MockHttpServletResponse get(UUID id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/classes/" + id), who).getResponse();
    }

    private MockHttpServletResponse deactivate(UUID id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/classes/" + id + "/deactivate"), who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID modality(String name) {
        return adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID teacher(Tenant t, String name, UUID unit) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, ?, ?)", id, t.orgId(), name, Modalities.normalize(name));
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, unit);
        return id;
    }

    private UUID klass(Tenant t, UUID unit, String name, UUID head, String link) {
        UUID id = UUID.randomUUID();
        UUID adult = adminJdbc().queryForObject("select id from age_range where organization_id = ? and name = 'Adulto'", UUID.class, t.orgId());
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, age_range_id, capacity, head_teacher_id, group_link) "
                + "values (?, ?, ?, ?, ?, ?, ?, 30, ?, ?)", id, t.orgId(), unit, name, Modalities.normalize(name), modality("Judô"), adult, head, link);
        return id;
    }

    private UUID fullClass(Tenant t) {
        UUID ana = teacher(t, "Ana", t.unitId());
        UUID beto = teacher(t, "Beto", t.unitId());
        UUID caio = teacher(t, "Caio", t.unitId());
        UUID id = klass(t, t.unitId(), "Judô Adulto", ana, "https://chat.example.com/grupo");
        adminJdbc().update("insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values (?, ?, 3, '19:00', '20:00'), (?, ?, 1, '18:00', '19:30')",
                t.orgId(), id, t.orgId(), id);
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?), (?, ?, ?)", t.orgId(), id, beto, t.orgId(), id, caio);
        return id;
    }

    private int audits(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'class.deactivated'", Integer.class, t.orgId());
    }

    private void lesson(Tenant t, UUID klass, UUID head, java.time.LocalDate date, boolean cancelled) {
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id, cancelled) values (?, ?, ?, ?, ?, '19:00', '20:00', ?, ?)",
                UUID.randomUUID(), t.orgId(), t.unitId(), klass, date, head, cancelled);
    }

    @Test
    @DisplayName("CA16 (#49): próxima aula é a primeira não cancelada que ainda não terminou; sem aula ou só canceladas, nula")
    void nextClass() throws Exception {
        var t = tenant();
        UUID id = fullClass(t);
        UUID head = adminJdbc().queryForObject("select head_teacher_id from training_class where id = ?", UUID.class, id);
        var today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        var coord = who(t, Role.COORDINATOR);
        assertThat(body(get(id, coord)).get("nextClass").isNull()).isTrue();
        lesson(t, id, head, today.minusDays(3), false);
        lesson(t, id, head, today.plusDays(2), true);
        assertThat(body(get(id, coord)).get("nextClass").isNull()).isTrue();
        lesson(t, id, head, today.plusDays(9), false);
        lesson(t, id, head, today.plusDays(5), false);
        var next = body(get(id, coord)).get("nextClass");
        assertThat(next.get("date").asText()).isEqualTo(today.plusDays(5).toString());
        assertThat(next.get("startsAt").asText()).isEqualTo("19:00");
        assertThat(next.get("endsAt").asText()).isEqualTo("20:00");
    }

    @Test
    @DisplayName("CA1: Coordenador abre o painel com todos os campos, horários ordenados, auxiliares, grupo e nada de alunos")
    void detail() throws Exception {
        var t = tenant();
        UUID id = fullClass(t);
        var r = get(id, who(t, Role.COORDINATOR));
        assertThat(r.getStatus()).isEqualTo(200);
        var d = body(r);
        assertThat(d.get("id").asText()).isEqualTo(id.toString());
        assertThat(d.get("name").asText()).isEqualTo("Judô Adulto");
        assertThat(d.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(d.get("modality").get("name").asText()).isEqualTo("Judô");
        assertThat(d.get("ageRange").get("name").asText()).isEqualTo("Adulto");
        assertThat(d.get("schedule").findValues("weekday").stream().map(JsonNode::asInt)).containsExactly(1, 3);
        assertThat(d.get("schedule").findValuesAsText("startsAt")).containsExactly("18:00", "19:00");
        assertThat(d.get("head").get("name").asText()).isEqualTo("Ana");
        assertThat(d.get("assistants").findValuesAsText("name")).containsExactly("Beto", "Caio");
        assertThat(d.get("capacity").asInt()).isEqualTo(30);
        assertThat(d.get("activeStudents").asInt()).isZero();
        assertThat(d.get("overCapacity").asBoolean()).isFalse();
        assertThat(d.get("groupLink").asText()).isEqualTo("https://chat.example.com/grupo");
        assertThat(d.get("nextClass").isNull()).isTrue();
        assertThat(d.get("students")).isEmpty();
        assertThat(d.get("deactivation").get("allowed").asBoolean()).isTrue();
        assertThat(d.get("deactivation").get("blockingStudents").asInt()).isZero();
        assertThat(r.getContentAsString()).doesNotContainIgnoringCase("presen").doesNotContainIgnoringCase("attend").doesNotContainIgnoringCase("frequen");
        UUID bare = klass(t, t.unitId(), "Sem grupo", teacher(t, "Dani", t.unitId()), null);
        assertThat(body(get(bare, who(t, Role.COORDINATOR))).get("groupLink").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA2: Administrador só abre turma da unidade dele (404 fora); Professor, Aluno e Responsável 403; anônimo 401; outra organização 404 e RLS")
    void access() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID mine = klass(t, t.unitId(), "Minha", teacher(t, "Ana", t.unitId()), null);
        UUID other = klass(t, second, "Alheia", teacher(t, "Beto", second), null);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(get(mine, admin).getStatus()).isEqualTo(200);
        assertThat(get(other, admin).getStatus()).isEqualTo(404);
        assertThat(get(other, who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get(mine, who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get(mine, null).getStatus()).isEqualTo(401);
        assertThat(get(UUID.randomUUID(), who(t, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        var b = tenant();
        assertThat(get(mine, who(b, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(deactivate(mine, who(b, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            try (var rows = sql.executeQuery("select 1 from training_class where id = '" + mine + "'")) {
                assertThat(rows.next()).isFalse();
            }
            app.rollback();
        }
    }

    @Test
    @DisplayName("RN-TUR-04: CA3: aluno de turma principal tem primary verdadeiro e o de outro horário falso; os dois contam como ativos")
    void primaryMark() throws Exception {
        var t = tenant();
        UUID id = klass(t, t.unitId(), "Judô", teacher(t, "Ana", t.unitId()), null);
        UUID other = UUID.randomUUID();
        assertThat(TrainingClasses.isPrimary(id, id)).isTrue();
        assertThat(TrainingClasses.isPrimary(id, other)).isFalse();
        var students = List.of(new TrainingClasses.Student(UUID.randomUUID(), "Principal", TrainingClasses.isPrimary(id, id)),
                new TrainingClasses.Student(UUID.randomUUID(), "Outro horário", TrainingClasses.isPrimary(id, other)));
        doReturn(students).when(classes).students(any());
        doReturn(students.size()).when(classes).activeStudents(any());
        var d = body(get(id, who(t, Role.COORDINATOR)));
        assertThat(d.get("activeStudents").asInt()).isEqualTo(2);
        assertThat(d.get("students").findValues("primary").stream().map(JsonNode::asBoolean)).containsExactly(true, false);
    }

    @Test
    @DisplayName("CA4: desativa turma sem aluno principal, mantém na lista INACTIVE, audita sem dado pessoal; repetir dá 409")
    void deactivates() throws Exception {
        var t = tenant();
        UUID id = fullClass(t);
        var c = who(t, Role.COORDINATOR);
        var r = deactivate(id, c);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(body(r).get("status").asText()).isEqualTo("INACTIVE");
        assertThat(body(r).get("deactivation").get("allowed").asBoolean()).isFalse();
        var list = body(call(MockMvcRequestBuilders.get("/api/v1/classes?status=INACTIVE"), c).getResponse());
        assertThat(list.get("items")).hasSize(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(id.toString());
        assertThat(audits(t)).isEqualTo(1);
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where organization_id = ? and action = 'class.deactivated'", String.class, t.orgId());
        assertThat(after).doesNotContain("Ana").doesNotContain("grupo");
        assertThat(deactivate(id, c).getStatus()).isEqualTo(409);
        assertThat(audits(t)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from training_class_schedule where class_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA5: com aluno principal (injetado) responde 409 HAS_PRIMARY_STUDENTS, nada muda e o painel não permite")
    void blocked() throws Exception {
        var t = tenant();
        UUID id = klass(t, t.unitId(), "Judô", teacher(t, "Ana", t.unitId()), null);
        doReturn(3).when(classes).primaryStudents(any());
        var c = who(t, Role.COORDINATOR);
        var r = deactivate(id, c);
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("HAS_PRIMARY_STUDENTS").contains("3");
        assertThat(adminJdbc().queryForObject("select status from training_class where id = ?", String.class, id)).isEqualTo("ACTIVE");
        assertThat(audits(t)).isZero();
        var d = body(get(id, c)).get("deactivation");
        assertThat(d.get("allowed").asBoolean()).isFalse();
        assertThat(d.get("blockingStudents").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("CA6: Administrador sem alcance 404, Administrador com alcance 200, Professor 403, anônimo 401; nada muda")
    void deactivatePermissions() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID other = klass(t, second, "Alheia", teacher(t, "Beto", second), null);
        UUID mine = klass(t, t.unitId(), "Minha", teacher(t, "Ana", t.unitId()), null);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(deactivate(other, admin).getStatus()).isEqualTo(404);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(deactivate(mine, who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(deactivate(mine, null).getStatus()).isEqualTo(401);
        assertThat(adminJdbc().queryForList("select status from training_class", String.class)).containsOnly("ACTIVE");
        assertThat(deactivate(mine, admin).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-TUR-06: CA7: nenhuma coluna da turma menciona presença ou frequência")
    void noAttendance() {
        assertThat(adminJdbc().queryForList("select column_name from information_schema.columns where table_schema = 'public' and table_name like 'training_class%'", String.class))
                .noneMatch(n -> n.matches("(?i).*(presen|attend|frequen).*"));
    }
}
