package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RF-AGE-05: leitura, sugestões, prévia, o gerador diário e a sombra da Agenda, com banco real. */
class NoClassDayReadIT extends NoClassDayBase {
    @Autowired LessonGenerator generator;
    @Autowired OrganizationTransactions transactions;

    private void day(Tenant t, UUID id, LocalDate start, LocalDate end, String reason) {
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, ?)", id, t.orgId(),
                t.unitId(), start, end, reason);
    }

    @Test
    @DisplayName("CA1: lista dias que não terminaram, com aviso e aulas canceladas; sugestões sem o já confirmado")
    void lists() throws Exception {
        var t = tenant();
        var coord = who(t, Role.COORDINATOR);
        LocalDate holiday = Holidays.next12Months(TODAY).get(0).date();
        UUID future = UUID.randomUUID();
        day(t, future, holiday, holiday, "Feriado confirmado");
        day(t, UUID.randomUUID(), TODAY.minusDays(3), TODAY.minusDays(1), "Terminou ontem");
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        UUID l = lesson(t, c, ana, holiday, "19:00", "20:00", true, false);
        adminJdbc().update("update lesson set no_class_day_id = ? where id = ?", future, l);

        JsonNode v = body(get(PATH, coord), 200);
        assertThat(v.get("units")).hasSize(1);
        assertThat(v.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(v.get("days")).hasSize(1);
        JsonNode d = v.get("days").get(0);
        assertThat(d.get("reason").asText()).isEqualTo("Feriado confirmado");
        assertThat(d.get("origin").asText()).isEqualTo("UNIT");
        assertThat(d.get("allClasses").asBoolean()).isTrue();
        assertThat(d.get("notice").get("status").asText()).isEqualTo("NOT_NOTIFIED");
        assertThat(d.get("cancelledLessons").asInt()).isEqualTo(1);
        assertThat(d.get("canEdit").asBoolean()).isTrue();
        var dates = v.get("suggestions").findValuesAsText("date");
        assertThat(dates).isNotEmpty().doesNotContain(holiday.toString()).isSorted();
        assertThat(v.get("suggestions").get(0).get("code").asText()).isNotBlank();
    }

    @Test
    @DisplayName("CA13: Professor, Aluno e Responsável 403; sem sessão 401; unidade fora do alcance ou de outra organização 404")
    void access() throws Exception {
        var t = tenant();
        var other = tenant();
        var second = secondUnit(t);
        for (Role r : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get(PATH, who(t, r)).getStatus()).as(r.name()).isEqualTo(403);
            assertThat(get(PATH + "/preview?unitIds=" + t.unitId() + "&startsOn=" + TODAY + "&endsOn=" + TODAY, who(t, r)).getStatus()).isEqualTo(403);
        }
        assertThat(get(PATH, null).getStatus()).isEqualTo(401);
        var admin = admin(t, t.unitId());
        assertThat(get(PATH + "?unitId=" + second.unitId(), admin).getStatus()).isEqualTo(404);
        assertThat(get(PATH + "?unitId=" + other.unitId(), who(t, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(get(PATH + "/preview?unitIds=" + second.unitId() + "&startsOn=" + TODAY + "&endsOn=" + TODAY, admin).getStatus()).isEqualTo(404);
        assertThat(body(get(PATH, admin), 200).get("units")).hasSize(1);
        assertThat(body(get(PATH, who(t, Role.COORDINATOR)), 200).get("units")).hasSize(2);
    }

    @Test
    @DisplayName("CA10: prévia por unidade, só aulas que seriam canceladas, sem gravar")
    void preview() throws Exception {
        var t = tenant();
        var coord = who(t, Role.COORDINATOR);
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        UUID a = klass(t, "Turma A", judo, ana, "ACTIVE");
        UUID b = klass(t, "Turma B", judo, ana, "ACTIVE");
        UUID la = future(t, a, ana, 5);
        future(t, b, ana, 5);
        lesson(t, a, ana, TODAY.plusDays(5), "21:00", "22:00", true, false); // cancelada à mão
        String q = "/preview?unitIds=" + t.unitId() + "&startsOn=" + TODAY.plusDays(5) + "&endsOn=" + TODAY.plusDays(5);
        JsonNode all = body(get(PATH + q, coord), 200);
        assertThat(all.get("units").get(0).get("lessons")).hasSize(2);
        assertThat(all.get("units").get(0).get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        JsonNode one = body(get(PATH + q + "&classIds=" + a, coord), 200);
        assertThat(ids(one.get("units").get(0).get("lessons"))).containsExactly(la.toString());
        assertThat(one.get("units").get(0).get("lessons").get(0).get("class").get("name").asText()).isEqualTo("Turma A");
        assertThat(cancelled(t)).isEqualTo(1);
        assertThat(days(t)).isZero();
        assertThat(get(PATH + "/preview?unitIds=" + t.unitId() + "&startsOn=" + TODAY + "&endsOn=" + TODAY.plusDays(90), coord).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA9, CA16: o gerador cria Cancelada só para a turma afetada; a Agenda só sombreia dia de todas as turmas")
    void generatorAndAgenda() throws Exception {
        var t = tenant();
        var coord = who(t, Role.COORDINATOR);
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        UUID a = klass(t, "Turma A", judo, ana, "ACTIVE");
        UUID b = klass(t, "Turma B", judo, ana, "ACTIVE");
        allWeekdays(t, a);
        allWeekdays(t, b);
        LocalDate target = TODAY.plusDays(20);
        UUID onlyA = UUID.randomUUID();
        day(t, onlyA, target, target, "Só a turma A");
        adminJdbc().update("insert into no_class_day_class (organization_id, no_class_day_id, class_id) values (?, ?, ?)", t.orgId(), onlyA, a);
        LocalDate everyone = TODAY.plusDays(30);
        UUID all = UUID.randomUUID();
        day(t, all, everyone, everyone, "Todas");
        adminJdbc().update("update no_class_day set origin = 'HOLIDAY' where id = ?", all);

        transactions.provision(t.orgId(), () -> generator.forOrganization(t.orgId(), Instant.now(), new AuditActor(AuditActor.Type.SYSTEM, null, null), "TEST"));

        String sql = "select cancelled || '|' || coalesce(cancel_reason_code, '-') || '|' || coalesce(cancel_reason, '-') || '|' || coalesce(no_class_day_id::text, '-') "
                + "from lesson where class_id = ? and lesson_date = ?";
        assertThat(adminJdbc().queryForObject(sql, String.class, a, target)).isEqualTo("true|OTHER|Só a turma A|" + onlyA);
        assertThat(adminJdbc().queryForObject(sql, String.class, b, target)).isEqualTo("false|-|-|-");
        assertThat(adminJdbc().queryForObject(sql, String.class, a, everyone)).isEqualTo("true|HOLIDAY|Todas|" + all);
        assertThat(adminJdbc().queryForObject(sql, String.class, b, everyone)).isEqualTo("true|HOLIDAY|Todas|" + all);

        String range = "?from=" + target + "&to=" + everyone + "&unitId=" + t.unitId();
        JsonNode agenda = body(get("/api/v1/agenda" + range, coord), 200);
        assertThat(agenda.get("noClassDays")).hasSize(1);
        assertThat(agenda.get("noClassDays").get(0).get("reason").asText()).isEqualTo("Todas");
    }
}
