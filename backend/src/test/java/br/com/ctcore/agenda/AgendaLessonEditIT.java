package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** RF-AGE-03: PUT /agenda/lessons/{id} com banco real. */
class AgendaLessonEditIT extends AgendaBase {
    @Autowired ObjectMapper json;
    @Autowired LessonGenerator generator;
    @Autowired br.com.ctcore.isolation.OrganizationTransactions transactions;
    static final br.com.ctcore.audit.AuditActor SYSTEM = new br.com.ctcore.audit.AuditActor(br.com.ctcore.audit.AuditActor.Type.SYSTEM, null, null);

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    static final String NO_AGENDA_EDIT = "update unit_settings set teacher_permissions = '{true,false,true,true}' where unit_id = ?";

    private static Map<String, Object> body(String scope, LocalDate date, String from, String to, UUID teacher) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scope", scope);
        m.put("date", date == null ? null : date.toString());
        m.put("startsAt", from);
        m.put("endsAt", to);
        m.put("teacherId", teacher == null ? null : teacher.toString());
        return m;
    }

    private MockHttpServletResponse put(Object id, Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.put("/api/v1/agenda/lessons/" + id).contentType("application/json").content(json.writeValueAsString(body)),
                who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private String field(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).path("fields").path(0).path("field").asText();
    }

    private String row(UUID id) {
        return adminJdbc().queryForObject("select lesson_date || ' ' || starts_at || ' ' || ends_at || ' ' || substitute || ' ' || manually_edited "
                + "from lesson where id = ?", String.class, id);
    }

    @Test
    @DisplayName("RN-AGE-04: CA1, CA3, CA8: THIS muda só a aula, marca edição, troca professor sem mudar o titular e audita")
    void thisLesson() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?)", t.orgId(), beto, judo);
        UUID c = klass(t, "Judô", judo, ana, "ACTIVE");
        UUID l1 = lesson(t, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        UUID l2 = lesson(t, c, ana, TODAY.plusDays(10), "19:00", "20:00", false, false);
        var coord = who(t, Role.COORDINATOR);

        JsonNode d = ok(put(l1, body("THIS", TODAY.plusDays(4), "18:00", "19:30", beto), coord));
        assertThat(d.get("date").asText()).isEqualTo(TODAY.plusDays(4).toString());
        assertThat(d.get("startsAt").asText()).isEqualTo("18:00");
        assertThat(d.get("substitute").asBoolean()).isTrue();
        assertThat(d.get("head").get("name").asText()).isEqualTo("Ana");
        assertThat(row(l1)).endsWith("true true");
        assertThat(row(l2)).isEqualTo(TODAY.plusDays(10) + " 19:00:00 20:00:00 false false");
        assertThat(adminJdbc().queryForObject("select head_teacher_id from training_class where id = ?", UUID.class, c)).isEqualTo(ana);
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'lesson.updated' and entity_id = ?",
                String.class, l1.toString());
        assertThat(after).contains("THIS", "18:00");
        assertThat(adminJdbc().queryForObject("select original_date from lesson where id = ?", LocalDate.class, l1)).isEqualTo(TODAY.plusDays(3));

        assertThat(ok(put(l1, body("THIS", TODAY.plusDays(4), "18:00", "19:30", ana), coord)).get("substitute").asBoolean()).isFalse();
        // D6: o horário original continua o mesmo depois de outra edição
        assertThat(adminJdbc().queryForObject("select original_date from lesson where id = ?", LocalDate.class, l1)).isEqualTo(TODAY.plusDays(3));
    }

    @Test
    @DisplayName("D6: o gerador não recria o horário antigo de uma aula editada")
    void generatorKeepsMovedLesson() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        LocalDate day = TODAY.plusDays(3);
        slot(t, c, day.getDayOfWeek().getValue(), "19:00", "20:00");
        UUID l = lesson(t, c, ana, day, "19:00", "20:00", false, false);
        ok(put(l, body("THIS", day.plusDays(1), "19:00", "20:00", ana), who(t, Role.COORDINATOR)));
        transactions.provision(t.orgId(), () -> generator.forOrganization(t.orgId(), java.time.Instant.now(), SYSTEM, "DAILY_JOB"));
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where class_id = ? and lesson_date = ?", Integer.class, c, day.plusDays(1))).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where class_id = ? and lesson_date = ?", Integer.class, c, day)).isZero();
    }

    @Test
    @DisplayName("RN-AGE-04: CA2: FOLLOWING desloca as seguintes, preserva cancelada e anterior; colisão dá 409 e nada muda")
    void following() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        UUID l1 = lesson(t, c, ana, TODAY.plusDays(7), "19:00", "20:00", false, false);
        UUID l2 = lesson(t, c, ana, TODAY.plusDays(14), "19:00", "20:00", false, false);
        UUID l3 = lesson(t, c, ana, TODAY.plusDays(21), "19:00", "20:00", false, false);
        UUID cancelled = lesson(t, c, ana, TODAY.plusDays(28), "19:00", "20:00", true, false);
        UUID l4 = lesson(t, c, ana, TODAY.plusDays(35), "19:00", "20:00", false, false);
        var coord = who(t, Role.COORDINATOR);

        ok(put(l2, body("FOLLOWING", TODAY.plusDays(15), "20:00", "21:00", ana), coord));
        assertThat(row(l1)).isEqualTo(TODAY.plusDays(7) + " 19:00:00 20:00:00 false false");
        assertThat(row(l2)).isEqualTo(TODAY.plusDays(15) + " 20:00:00 21:00:00 false true");
        assertThat(row(l3)).isEqualTo(TODAY.plusDays(22) + " 20:00:00 21:00:00 false true");
        assertThat(row(cancelled)).startsWith(TODAY.plusDays(28) + " 19:00:00");
        assertThat(row(l4)).isEqualTo(TODAY.plusDays(36) + " 20:00:00 21:00:00 false true");

        // +7 dias: cada aula passa pela data da seguinte, mas a operação termina sem colisão
        ok(put(l2, body("FOLLOWING", TODAY.plusDays(22), "20:00", "21:00", ana), coord));
        assertThat(row(l2)).startsWith(TODAY.plusDays(22) + " 20:00:00");
        assertThat(row(l3)).startsWith(TODAY.plusDays(29) + " 20:00:00");

        // colisão real: a última aula iria para o dia e início de outra aula da turma; nenhuma muda
        assertThat(row(l4)).startsWith(TODAY.plusDays(43) + " 20:00:00");
        lesson(t, c, ana, TODAY.plusDays(51), "20:00", "21:00", true, false);
        var clash = put(l2, body("FOLLOWING", TODAY.plusDays(30), "20:00", "21:00", ana), coord);
        assertThat(clash.getStatus()).isEqualTo(409);
        assertThat(row(l2)).startsWith(TODAY.plusDays(22) + " 20:00:00");
        assertThat(row(l3)).startsWith(TODAY.plusDays(29) + " 20:00:00");
        assertThat(row(l4)).startsWith(TODAY.plusDays(43) + " 20:00:00");
    }

    @Test
    @DisplayName("CA3, CA5: professor inválido 422; 400 de campos e início no passado")
    void validation() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID inactive = teacher(t, "Inativo", null);
        adminJdbc().update("update teacher set status = 'INACTIVE' where id = ?", inactive);
        UUID nomod = teacher(t, "SemModalidade", null);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        UUID l = lesson(t, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        var coord = who(t, Role.COORDINATOR);
        for (UUID bad : new UUID[] {inactive, nomod, UUID.randomUUID()}) {
            var r = put(l, body("THIS", TODAY.plusDays(3), "19:00", "20:00", bad), coord);
            assertThat(r.getStatus()).isEqualTo(422);
            assertThat(field(r)).isEqualTo("teacherId");
        }
        assertThat(field(put(l, body("THIS", TODAY.plusDays(3), "20:00", "19:00", ana), coord))).isEqualTo("endsAt");
        assertThat(field(put(l, body("THIS", TODAY.minusDays(1), "19:00", "20:00", ana), coord))).isEqualTo("date");
        assertThat(field(put(l, body("THIS", null, "19:00", "20:00", ana), coord))).isEqualTo("date");
        assertThat(field(put(l, body("ALL", TODAY.plusDays(3), "19:00", "20:00", ana), coord))).isEqualTo("scope");
        assertThat(field(put(l, body("THIS", TODAY.plusDays(3), "19:00", "20:00", null), coord))).isEqualTo("teacherId");
        Map<String, Object> bad = body("THIS", TODAY.plusDays(3), "19:00", "20:00", ana);
        bad.put("date", "03/10/2026");
        assertThat(put(l, bad, coord).getStatus()).isEqualTo(400);
        UUID twin = lesson(t, c, ana, TODAY.plusDays(4), "19:00", "20:00", false, false);
        assertThat(put(twin, body("THIS", TODAY.plusDays(3), "19:00", "20:00", ana), coord).getStatus()).isEqualTo(409);
        assertThat(row(l)).isEqualTo(TODAY.plusDays(3) + " 19:00:00 20:00:00 false false");
    }

    @Test
    @DisplayName("RN-AGE-07: CA4: Professor, cancelada, concluída recente e antiga")
    void states() throws Exception {
        var t = tenant();
        UUID acc = account("ana");
        UUID ana = teacher(t, "Ana", acc);
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id) values (?, ?, ?)", t.orgId(), beto, judo);
        UUID c = klass(t, "Judô", judo, ana, "ACTIVE");
        UUID other = klass(t, "Outra", judo, beto, "ACTIVE");
        UUID mine = lesson(t, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        UUID theirs = lesson(t, other, beto, TODAY.plusDays(3), "19:00", "20:00", false, false);
        UUID cancelled = lesson(t, c, ana, TODAY.plusDays(5), "19:00", "20:00", true, false);
        UUID recent = lesson(t, c, ana, TODAY.minusDays(5), "19:00", "20:00", false, false);
        UUID old = lesson(t, c, ana, TODAY.minusDays(40), "19:00", "20:00", false, false);
        var me = new AccountPrincipal(acc, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(t.unitId()));
        var admin = who(t, Role.ADMINISTRATOR);

        ok(put(mine, body("THIS", TODAY.plusDays(3), "18:00", "19:00", ana), me));
        assertThat(put(theirs, body("THIS", TODAY.plusDays(3), "18:00", "19:00", beto), me).getStatus()).isEqualTo(404);
        assertThat(put(cancelled, body("THIS", TODAY.plusDays(5), "19:00", "20:00", ana), admin).getStatus()).isEqualTo(409);
        assertThat(put(recent, body("THIS", TODAY.minusDays(5), "19:00", "20:00", beto), me).getStatus()).isEqualTo(409);
        var noReason = body("THIS", TODAY.minusDays(5), "19:00", "20:00", beto);
        assertThat(put(recent, noReason, admin).getStatus()).isEqualTo(400);
        assertThat(field(put(recent, body("THIS", TODAY.minusDays(4), "19:00", "20:00", beto), admin))).isEqualTo("date");
        noReason.put("reason", "Troca registrada");
        assertThat(ok(put(recent, noReason, admin)).get("teacher").get("name").asText()).isEqualTo("Beto");
        var oldBody = body("THIS", TODAY.minusDays(40), "19:00", "20:00", beto);
        oldBody.put("reason", "x");
        assertThat(put(old, oldBody, admin).getStatus()).isEqualTo(409);
        adminJdbc().update(NO_AGENDA_EDIT, t.unitId());
        assertThat(put(mine, body("THIS", TODAY.plusDays(3), "18:00", "19:00", ana), me).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA9: perfis, sessão, id inválido, outra organização e alcance")
    void access() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID ana = teacher(b, "Ana", null);
        UUID c = klass(b, "Judô", modality(b, "Judô"), ana, "ACTIVE");
        UUID l = lesson(b, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        var in = body("THIS", TODAY.plusDays(3), "18:00", "19:00", ana);
        assertThat(put(l, in, who(a, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(put(l, in, who(b, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(put(l, in, who(b, Role.GUARDIAN)).getStatus()).isEqualTo(403);
        assertThat(put(l, in, null).getStatus()).isEqualTo(401);
        assertThat(put("abc", in, who(b, Role.COORDINATOR)).getStatus()).isEqualTo(400);
        var outOfReach = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), b.orgId(), Role.ADMINISTRATOR,
                Set.of(UUID.randomUUID()));
        assertThat(put(l, in, outOfReach).getStatus()).isEqualTo(404);
        assertThat(row(l)).isEqualTo(TODAY.plusDays(3) + " 19:00:00 20:00:00 false false");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'lesson.updated'", Integer.class)).isZero();
    }
}
