package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-AGE-01: GET /agenda com banco real (situação e RN-AGE-08 têm teste unitário em AgendaRulesTest). */
class AgendaIT extends AgendaBase {
    @Autowired ObjectMapper json;

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/agenda" + query), who).getResponse();
    }

    private JsonNode ok(String query, AccountPrincipal who) throws Exception {
        var r = get(query, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private static String range(LocalDate from, LocalDate to) {
        return "?from=" + from + "&to=" + to;
    }

    private static List<String> ids(JsonNode view) {
        List<String> out = new java.util.ArrayList<>();
        view.get("lessons").forEach(l -> out.add(l.get("id").asText()));
        return out;
    }

    private AccountPrincipal teacherPrincipal(Tenant t, UUID account) {
        return new AccountPrincipal(account, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(t.unitId()));
    }

    @Test
    @DisplayName("CA7, CA12: período, ordem, campos, dias sem aula, opções e filtros; id de outra organização devolve lista vazia")
    void listsAndFilters() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        UUID jiu = modality(t, "Jiu-Jitsu");
        UUID cj = klass(t, "Judô Adulto", judo, ana, "ACTIVE");
        UUID cz = klass(t, "Jiu Adulto", jiu, beto, "ACTIVE");
        LocalDate d = TODAY.plusDays(3);
        UUID second = lesson(t, cj, ana, d, "19:00", "20:00", false, false);
        UUID first = lesson(t, cz, beto, d, "18:00", "19:00", true, false);
        UUID later = lesson(t, cj, ana, d.plusDays(1), "19:00", "20:00", false, false);
        lesson(t, cj, ana, d.plusDays(40), "19:00", "20:00", false, false);
        adminJdbc().update("update lesson set substitute = true where id = ?", later);
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, 'Feriado')",
                UUID.randomUUID(), t.orgId(), t.unitId(), d.plusDays(2), d.plusDays(2));
        var coord = who(t, Role.COORDINATOR);

        JsonNode v = ok(range(TODAY, TODAY.plusDays(10)), coord);
        assertThat(ids(v)).containsExactly(first.toString(), second.toString(), later.toString());
        JsonNode l = v.get("lessons").get(0);
        assertThat(l.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(l.get("cancelReason").asText()).isEqualTo("Motivo");
        assertThat(l.get("class").get("name").asText()).isEqualTo("Jiu Adulto");
        assertThat(l.get("modality").get("id").asText()).isEqualTo(jiu.toString());
        assertThat(l.get("teacher").get("name").asText()).isEqualTo("Beto");
        assertThat(l.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(l.get("date").asText()).isEqualTo(d.toString());
        assertThat(l.get("startsAt").asText()).isEqualTo("18:00");
        assertThat(l.get("substitute").asBoolean()).isFalse();
        assertThat(v.get("lessons").get(2).get("substitute").asBoolean()).isTrue();
        assertThat(v.get("lessons").get(1).get("cancelReason").isNull()).isTrue();
        assertThat(v.get("noClassDays")).hasSize(1);
        assertThat(v.get("noClassDays").get(0).get("reason").asText()).isEqualTo("Feriado");
        assertThat(v.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(v.get("units")).hasSize(1);
        assertThat(v.get("teachers").findValuesAsText("name")).containsExactly("Ana", "Beto");
        assertThat(v.get("modalities").findValuesAsText("name")).containsExactly("Jiu-Jitsu", "Judô");
        assertThat(ids(ok(range(d.plusDays(1), d.plusDays(1)), coord))).containsExactly(later.toString());

        assertThat(ids(ok(range(TODAY, TODAY.plusDays(10)) + "&teacherId=" + ana, coord))).containsExactly(second.toString(), later.toString());
        assertThat(ids(ok(range(TODAY, TODAY.plusDays(10)) + "&modalityId=" + jiu, coord))).containsExactly(first.toString());
        assertThat(ids(ok(range(TODAY, TODAY.plusDays(10)) + "&modalityId=" + jiu + "&teacherId=" + ana, coord))).isEmpty();
        assertThat(ids(ok(range(TODAY, TODAY.plusDays(10)) + "&modalityId=" + UUID.randomUUID(), coord))).isEmpty();

        String body = get(range(TODAY, TODAY.plusDays(10)), coord).getContentAsString().toLowerCase();
        assertThat(body).doesNotContain("espera", "waitlist", "presen", "attendance", "frequen");
        List<String> columns = adminJdbc().queryForList("select column_name from information_schema.columns where table_name in ('lesson', 'no_class_day')", String.class);
        assertThat(columns).noneMatch(c -> c.matches(".*(wait|espera|presen|attend|frequen).*"));
    }

    @Test
    @DisplayName("CA8: canEdit e canCancel por perfil, tempo e aula própria")
    void canEditAndCancel() throws Exception {
        var t = tenant();
        UUID acc = account("ana");
        UUID ana = teacher(t, "Ana", acc);
        UUID beto = teacher(t, "Beto", null);
        UUID judo = modality(t, "Judô");
        UUID mine = klass(t, "Minha", judo, ana, "ACTIVE");
        UUID theirs = klass(t, "Dele", judo, beto, "ACTIVE");
        LocalDate day = TODAY.minusDays(5);
        UUID recent = lesson(t, mine, ana, day, "19:00", "20:00", false, false);
        UUID old = lesson(t, mine, ana, TODAY.minusDays(40), "19:00", "20:00", false, false);
        UUID future = lesson(t, mine, ana, TODAY.plusDays(2), "19:00", "20:00", false, false);
        UUID cancelled = lesson(t, mine, ana, TODAY.plusDays(3), "19:00", "20:00", true, false);
        UUID other = lesson(t, theirs, beto, TODAY.plusDays(2), "17:00", "18:00", false, false);

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR}) {
            var admin = who(t, role);
            JsonNode v = ok(range(TODAY.minusDays(10), TODAY.plusDays(10)), admin);
            assertThat(flags(v, recent)).isEqualTo("true/false");
            assertThat(flags(v, future)).isEqualTo("true/true");
            assertThat(flags(v, cancelled)).isEqualTo("false/false");
            assertThat(flags(ok(range(TODAY.minusDays(45), TODAY.minusDays(35)), admin), old)).isEqualTo("false/false");
        }
        var teacher = teacherPrincipal(t, acc);
        JsonNode v = ok(range(TODAY.minusDays(10), TODAY.plusDays(10)), teacher);
        assertThat(ids(v)).containsExactlyInAnyOrder(recent.toString(), future.toString(), cancelled.toString()); // só as turmas dele
        assertThat(flags(v, recent)).isEqualTo("false/false");
        assertThat(flags(v, future)).isEqualTo("true/true");
        assertThat(flags(v, cancelled)).isEqualTo("false/false");

        // Sem AGENDA_EDIT (segundo recurso do Professor na unidade) nada pode, mesmo na aula futura própria.
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,false,true,true}' where unit_id = ?", t.unitId());
        assertThat(flags(ok(range(TODAY.minusDays(10), TODAY.plusDays(10)), teacher), future)).isEqualTo("false/false");
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,true,true}' where unit_id = ?", t.unitId());

        // Professor titular de outra turma, na aula em que é o professor da aula (substituto): vê e pode.
        adminJdbc().update("update lesson set teacher_id = ?, substitute = true where id = ?", ana, other);
        v = ok(range(TODAY.plusDays(1), TODAY.plusDays(5)), teacher);
        assertThat(ids(v)).contains(other.toString());
        assertThat(flags(v, other)).isEqualTo("true/true");
    }

    private static String flags(JsonNode view, UUID lesson) {
        for (JsonNode l : view.get("lessons")) {
            if (l.get("id").asText().equals(lesson.toString())) return l.get("canEdit").asBoolean() + "/" + l.get("canCancel").asBoolean();
        }
        throw new AssertionError("aula ausente " + lesson);
    }

    @Test
    @DisplayName("CA9: Aluno e Responsável 403, sem sessão 401, alcance do Administrador, Coordenador vê todas, 400 com o campo")
    void access() throws Exception {
        var t = tenant();
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Zeta', ?)", second, t.orgId(), "z" + second.toString().substring(0, 12));
        var judo = modality(t, "Judô");
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Judô", judo, ana, "ACTIVE");
        UUID mine = lesson(t, c, ana, TODAY.plusDays(1), "19:00", "20:00", false, false);
        String r = range(TODAY, TODAY.plusDays(7));

        assertThat(get(r, who(t, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(get(r, who(t, Role.GUARDIAN)).getStatus()).isEqualTo(403);
        assertThat(raw(MockMvcRequestBuilders.get("/api/v1/agenda" + r), null).getResponse().getStatus()).isEqualTo(401);

        var admin = who(t, Role.ADMINISTRATOR); // só a unidade do tenant
        JsonNode v = ok(r, admin);
        assertThat(v.get("units")).hasSize(1);
        assertThat(v.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(get(r + "&unitId=" + second, admin).getStatus()).isEqualTo(404);
        assertThat(get(r + "&unitId=" + UUID.randomUUID(), admin).getStatus()).isEqualTo(404);
        assertThat(ok(r, who(t, Role.COORDINATOR)).get("units")).hasSize(2);
        assertThat(ids(ok(r + "&unitId=" + second, who(t, Role.COORDINATOR)))).isEmpty();
        assertThat(ids(ok(r, who(t, Role.COORDINATOR)))).containsExactly(mine.toString());

        for (String bad : new String[] {"?from=2026-13-01&to=2026-12-01", "?from=" + TODAY + "&to=" + TODAY.minusDays(1), "?from=" + TODAY + "&to=" + TODAY.plusDays(31),
                "?to=" + TODAY}) {
            var resp = get(bad, admin);
            assertThat(resp.getStatus()).as(bad).isEqualTo(400);
            assertThat(json.readTree(resp.getContentAsString()).get("fields").findValuesAsText("field")).as(bad).isNotEmpty();
        }
        assertThat(get(r + "&teacherId=abc", admin).getStatus()).isEqualTo(400);
        assertThat(get("?from=" + TODAY + "&to=" + TODAY.plusDays(30), admin).getStatus()).isEqualTo(200); // 31 dias é o máximo
    }

    @Test
    @DisplayName("CA10: ids e aulas de outra organização não aparecem; Professor sem cadastro não vê nada")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID ta = teacher(a, "Ana", null);
        UUID tb = teacher(b, "Beto", null);
        UUID ma = modality(a, "Judô");
        UUID mb = modality(b, "Judô");
        UUID la = lesson(a, klass(a, "Turma A", ma, ta, "ACTIVE"), ta, TODAY.plusDays(1), "19:00", "20:00", false, false);
        lesson(b, klass(b, "Turma B", mb, tb, "ACTIVE"), tb, TODAY.plusDays(1), "19:00", "20:00", false, false);
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, 'B')",
                UUID.randomUUID(), b.orgId(), b.unitId(), TODAY.plusDays(1), TODAY.plusDays(1));
        String r = range(TODAY, TODAY.plusDays(7));
        var coord = who(a, Role.COORDINATOR);

        JsonNode v = ok(r, coord);
        assertThat(ids(v)).containsExactly(la.toString());
        assertThat(v.get("noClassDays")).isEmpty();
        assertThat(v.get("teachers").findValuesAsText("id")).containsExactly(ta.toString());
        assertThat(get(r + "&unitId=" + b.unitId(), coord).getStatus()).isEqualTo(404);
        assertThat(ids(ok(r + "&teacherId=" + tb, coord))).isEmpty();
        assertThat(ids(ok(r, teacherPrincipal(a, account("sem-cadastro"))))).isEmpty();
    }
}
