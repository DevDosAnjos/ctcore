package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-AGE-02: GET /agenda/lessons/{id} com banco real (a janela do substituto tem teste unitário em AgendaRulesTest). */
class AgendaLessonIT extends AgendaBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean Clock clock;

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private MockHttpServletResponse get(Object id, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/agenda/lessons/" + id), who).getResponse();
    }

    private JsonNode ok(UUID id, AccountPrincipal who) throws Exception {
        var r = get(id, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private AccountPrincipal teacherPrincipal(Tenant t, UUID account, UUID... units) {
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(units));
    }

    private static String flags(JsonNode d) {
        return d.get("canEdit").asBoolean() + "/" + d.get("canCancel").asBoolean();
    }

    @Test
    @DisplayName("RN-AGE-04: followingEdited conta as aulas seguintes ainda agendadas e alteradas à mão que \"esta e as seguintes\" substituiria; avulsa e outra organização")
    void followingEdited() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Turma", modality(t, "Judô"), ana, "ACTIVE");
        UUID other = klass(t, "Outra", modality(t, "Judô"), ana, "ACTIVE");
        UUID current = lesson(t, c, ana, TODAY.plusDays(7), "19:00", "20:00", false, true);
        lesson(t, c, ana, TODAY.plusDays(14), "19:00", "20:00", false, true);
        lesson(t, c, ana, TODAY.plusDays(21), "19:00", "20:00", false, true);
        lesson(t, c, ana, TODAY.plusDays(28), "19:00", "20:00", false, false);
        lesson(t, c, ana, TODAY.plusDays(35), "19:00", "20:00", true, true);
        lesson(t, c, ana, TODAY.minusDays(7), "19:00", "20:00", false, true);
        lesson(t, other, ana, TODAY.plusDays(14), "19:00", "20:00", false, true);
        UUID covered = lesson(t, c, ana, TODAY.plusDays(42), "19:00", "20:00", false, false);
        adminJdbc().update("update lesson set substitute = true where id = ?", covered); // RN-AGE-04: substituto gravado conta como alterada à mão
        var coord = who(t, Role.COORDINATOR);
        assertThat(ok(current, coord).get("followingEdited").asInt()).isEqualTo(3);
        assertThat(ok(lesson(t, c, ana, TODAY.plusDays(40), "19:00", "20:00", false, false), coord).get("followingEdited").asInt()).isEqualTo(1);
        assertThat(get(current, who(tenant(), Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("RN-AGE-13: CA1, CA2, CA6, CA8: campos, substituição com titular, cancelada, grupo e canViewClass; sem lista de espera nem auditoria")
    void fields() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        UUID c = klass(t, "Judô Adulto", judo, ana, "ACTIVE");
        adminJdbc().update("update training_class set group_link = 'https://chat.whatsapp.com/abc' where id = ?", c);
        UUID future = lesson(t, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        UUID sub = lesson(t, c, beto, TODAY.plusDays(4), "19:00", "20:00", false, false);
        adminJdbc().update("update lesson set substitute = true where id = ?", sub);
        UUID cancelled = lesson(t, c, ana, TODAY.plusDays(5), "19:00", "20:00", true, false);
        int audits = adminJdbc().queryForObject("select count(*) from audit_log", Integer.class);
        var coord = who(t, Role.COORDINATOR);

        JsonNode d = ok(future, coord);
        assertThat(d.get("id").asText()).isEqualTo(future.toString());
        assertThat(d.get("date").asText()).isEqualTo(TODAY.plusDays(3).toString());
        assertThat(d.get("startsAt").asText()).isEqualTo("19:00");
        assertThat(d.get("endsAt").asText()).isEqualTo("20:00");
        assertThat(d.get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(d.get("class").get("name").asText()).isEqualTo("Judô Adulto");
        assertThat(d.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(d.get("modality").get("name").asText()).isEqualTo("Judô");
        assertThat(d.get("teacher").get("name").asText()).isEqualTo("Ana");
        assertThat(d.get("head").isNull()).isTrue();
        assertThat(d.get("substitute").asBoolean()).isFalse();
        assertThat(d.get("activeStudents").asInt()).isZero();
        assertThat(d.get("trialClasses").asInt()).isZero();
        assertThat(d.get("groupLink").asText()).isEqualTo("https://chat.whatsapp.com/abc");
        assertThat(flags(d)).isEqualTo("true/true");
        assertThat(d.get("canViewClass").asBoolean()).isTrue();
        assertThat(d.get("cancelReason").isNull()).isTrue();

        JsonNode s = ok(sub, coord);
        assertThat(s.get("substitute").asBoolean()).isTrue();
        assertThat(s.get("teacher").get("name").asText()).isEqualTo("Beto");
        assertThat(s.get("head").get("name").asText()).isEqualTo("Ana");

        JsonNode x = ok(cancelled, coord);
        assertThat(x.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(x.get("cancelReason").asText()).isEqualTo("Motivo");
        assertThat(flags(x)).isEqualTo("false/false");

        assertThat(ok(future, who(t, Role.ADMINISTRATOR)).get("canViewClass").asBoolean()).isTrue();

        String body = get(future, coord).getContentAsString().toLowerCase();
        assertThat(body).doesNotContain("espera", "waitlist", "presen", "attendance", "frequen");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(audits);
    }

    @Test
    @DisplayName("CA2, CA3: em andamento, concluída recente e antiga, com canEdit e canCancel como na lista")
    void statusAndFlags() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Turma", modality(t, "Judô"), ana, "ACTIVE");
        var coord = who(t, Role.COORDINATOR);
        UUID recent = lesson(t, c, ana, TODAY.minusDays(5), "19:00", "20:00", false, false);
        UUID old = lesson(t, c, ana, TODAY.minusDays(40), "19:00", "20:00", false, false);
        assertThat(ok(recent, coord).get("status").asText()).isEqualTo("COMPLETED");
        assertThat(flags(ok(recent, coord))).isEqualTo("true/false");
        assertThat(flags(ok(old, coord))).isEqualTo("false/false");

        // Relógio fixo ao meio-dia de hoje em São Paulo: o resultado não depende da hora em que o teste roda.
        doReturn(TODAY.atTime(12, 0).atZone(ZoneId.of("America/Sao_Paulo")).toInstant()).when(clock).instant();
        UUID running = lesson(t, c, ana, TODAY, "11:00", "13:00", false, false);
        JsonNode d = ok(running, coord);
        assertThat(d.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(flags(d)).isEqualTo("true/true");
    }

    @Test
    @DisplayName("CA3, CA5, CA6: Professor só vê aula da turma dele ou em que substitui; AGENDA_EDIT e canViewClass falso")
    void teacher() throws Exception {
        var t = tenant();
        UUID acc = account("ana");
        UUID ana = teacher(t, "Ana", acc);
        UUID aux = teacher(t, "Aux", account("aux"));
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        UUID mine = klass(t, "Minha", judo, ana, "ACTIVE");
        UUID assisted = klass(t, "Auxiliada", judo, beto, "ACTIVE");
        UUID theirs = klass(t, "Dele", judo, beto, "ACTIVE");
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), assisted, ana);
        UUID l1 = lesson(t, mine, ana, TODAY.plusDays(2), "19:00", "20:00", false, false);
        UUID l2 = lesson(t, assisted, beto, TODAY.plusDays(2), "17:00", "18:00", false, false);
        UUID l3 = lesson(t, theirs, beto, TODAY.plusDays(2), "17:00", "18:00", false, false);
        var me = teacherPrincipal(t, acc, t.unitId());

        JsonNode d = ok(l1, me);
        assertThat(flags(d)).isEqualTo("true/true");
        assertThat(d.get("canViewClass").asBoolean()).isFalse();
        assertThat(flags(ok(l2, me))).isEqualTo("true/true"); // auxiliar
        assertThat(get(l3, me).getStatus()).isEqualTo(404);
        assertThat(get(l1, teacherPrincipal(t, account("sem-cadastro"), t.unitId())).getStatus()).isEqualTo(404);
        assertThat(get(l1, teacherPrincipal(t, adminJdbc().queryForObject("select account_id from teacher where id = ?", UUID.class, aux), t.unitId()))
                .getStatus()).isEqualTo(404);

        // Aula da turma de outro professor, sem AGENDA_EDIT: ele só enxerga se substituir; aqui, auxiliar sem a permissão.
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,false,true,true}' where unit_id = ?", t.unitId());
        assertThat(flags(ok(l1, me))).isEqualTo("false/false");
        assertThat(flags(ok(l2, me))).isEqualTo("false/false");
    }

    @Test
    @DisplayName("CA4: substituto de outra unidade abre só no dia da aula; titular abre sempre; fuso da unidade")
    void substituteWindow() throws Exception {
        var t = tenant();
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Zeta', ?)", second, t.orgId(), "z" + second.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), second);
        UUID acc = account("sub");
        UUID subTeacher = teacher(t, "Sub", acc);
        UUID head = teacher(t, "Titular", null);
        UUID c = klass(t, "Turma", modality(t, "Judô"), head, "ACTIVE");
        var sub = teacherPrincipal(t, acc, second);
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        LocalDate today = LocalDate.now(zone);
        UUID eve = lesson(t, c, subTeacher, today.plusDays(1), "19:00", "20:00", false, false);
        UUID day = lesson(t, c, subTeacher, today, "00:00", "00:30", false, false);
        UUID after = lesson(t, c, subTeacher, today.minusDays(1), "19:00", "20:00", false, false);
        adminJdbc().update("update lesson set substitute = true where id in (?, ?, ?)", eve, day, after);

        assertThat(get(eve, sub).getStatus()).isEqualTo(404);
        assertThat(ok(day, sub).get("substitute").asBoolean()).isTrue();
        assertThat(get(after, sub).getStatus()).isEqualTo(404);
        // Professor da aula sem ser substituição e fora da turma: nunca abre.
        adminJdbc().update("update lesson set substitute = false where id = ?", day);
        assertThat(get(day, sub).getStatus()).isEqualTo(404);

        // Outro fuso: o dia da aula é o da unidade.
        adminJdbc().update("update lesson set substitute = true where id = ?", day);
        timezone(t, "Pacific/Kiritimati");
        LocalDate there = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
        adminJdbc().update("update lesson set lesson_date = ? where id = ?", there, day);
        assertThat(get(day, sub).getStatus()).isEqualTo(200);
        adminJdbc().update("update lesson set lesson_date = ? where id = ?", there.minusDays(1), day);
        assertThat(get(day, sub).getStatus()).isEqualTo(404);

        // Titular abre em qualquer dia.
        UUID headAcc = account("head");
        adminJdbc().update("update teacher set account_id = ? where id = ?", headAcc, head);
        assertThat(get(after, teacherPrincipal(t, headAcc, t.unitId())).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA5, CA7: Aluno e Responsável 403, sem sessão 401, alcance do Administrador, id inválido 400, outra organização 404")
    void accessAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID ta = teacher(a, "Ana", null);
        UUID tb = teacher(b, "Beto", null);
        UUID la = lesson(a, klass(a, "Turma A", modality(a, "Judô"), ta, "ACTIVE"), ta, TODAY.plusDays(1), "19:00", "20:00", false, false);
        UUID lb = lesson(b, klass(b, "Turma B", modality(b, "Judô"), tb, "ACTIVE"), tb, TODAY.plusDays(1), "19:00", "20:00", false, false);

        assertThat(get(la, who(a, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(get(la, who(a, Role.GUARDIAN)).getStatus()).isEqualTo(403);
        assertThat(raw(MockMvcRequestBuilders.get("/api/v1/agenda/lessons/" + la), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(get("abc", who(a, Role.COORDINATOR)).getStatus()).isEqualTo(400);
        assertThat(get(UUID.randomUUID(), who(a, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        for (Role role : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR}) assertThat(get(lb, who(a, role)).getStatus()).isEqualTo(404);

        var outsider = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), a.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        assertThat(get(la, outsider).getStatus()).isEqualTo(404);
        assertThat(ok(la, who(a, Role.ADMINISTRATOR)).get("id").asText()).isEqualTo(la.toString());

        // RLS: sem escopo, o papel operacional não enxerga nenhuma linha de lesson.
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from lesson where id = '" + lb + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isZero();
            }
            app.rollback();
        }
    }
}
