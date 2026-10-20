package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-AGE-05: POST /no-class-days, com banco real. */
class NoClassDayWriteIT extends NoClassDayBase {
    private record Setup(Tenant t, UUID teacher, UUID judo, UUID a, UUID b) {}

    private Setup setup() {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        return new Setup(t, ana, judo, klass(t, "Turma A", judo, ana, "ACTIVE"), klass(t, "Turma B", judo, ana, "ACTIVE"));
    }

    @Test
    @DisplayName("CA2, CA8, CA15: feriado confirmado cancela aulas não terminadas com HOLIDAY e audita; cancelada à mão e concluída não entram")
    void holiday() throws Exception {
        var s = setup();
        var t = s.t();
        LocalDate day = TODAY.plusDays(7);
        UUID l1 = future(t, s.a(), s.teacher(), 7);
        UUID l2 = future(t, s.b(), s.teacher(), 7);
        UUID manual = lesson(t, s.a(), s.teacher(), day, "21:00", "22:00", true, false);
        adminJdbc().update("update lesson set cancel_reason_code = 'ACADEMY_EVENT', cancel_reason = 'Evento' where id = ?", manual);
        UUID done = lesson(t, s.a(), s.teacher(), TODAY, "00:00", "00:01", false, false); // já terminou hoje
        Map<String, Object> b = body(TODAY.toString(), day.toString(), "Feriado de teste", t.unitId());
        b.put("holiday", true);

        JsonNode r = body(post(b, who(t, Role.COORDINATOR)), 201);
        assertThat(r).hasSize(1);
        assertThat(r.get(0).get("origin").asText()).isEqualTo("HOLIDAY");
        assertThat(r.get(0).get("notice").get("status").asText()).isEqualTo("NOT_NOTIFIED");
        assertThat(r.get(0).get("cancelledLessons").asInt()).isEqualTo(2);
        assertThat(status(l1)).isEqualTo("CANCELLED");
        assertThat(status(l2)).isEqualTo("CANCELLED");
        assertThat(adminJdbc().queryForObject("select cancel_reason_code || cancel_reason from lesson where id = ?", String.class, l1)).isEqualTo("HOLIDAYFeriado de teste");
        assertThat(adminJdbc().queryForObject("select cancel_reason_code from lesson where id = ?", String.class, manual)).isEqualTo("ACADEMY_EVENT");
        assertThat(adminJdbc().queryForObject("select no_class_day_id is null from lesson where id = ?", Boolean.class, manual)).isTrue();
        assertThat(status(done)).isEqualTo("SCHEDULED");
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'no_class_day.created' and organization_id = ?", String.class, t.orgId());
        assertThat(after).contains("\"cancelledLessons\": 2", "Feriado de teste", "HOLIDAY");
        assertThat(adminJdbc().queryForObject("select notice_status from no_class_day where organization_id = ?", String.class, t.orgId())).isEqualTo("Ainda não avisado");
    }

    @Test
    @DisplayName("CA2: origem COORDINATOR e UNIT")
    void origin() throws Exception {
        var s = setup();
        var t = s.t();
        assertThat(body(post(body(TODAY.plusDays(2).toString(), TODAY.plusDays(2).toString(), "X", t.unitId()), who(t, Role.COORDINATOR)), 201).get(0)
                .get("origin").asText()).isEqualTo("COORDINATOR");
        assertThat(body(post(body(TODAY.plusDays(5).toString(), TODAY.plusDays(5).toString(), "X", t.unitId()), admin(t, t.unitId())), 201).get(0)
                .get("origin").asText()).isEqualTo("UNIT");
    }

    @Test
    @DisplayName("CA3: várias unidades criam um dia por unidade; outra organização 404; sobreposição 409; nada gravado")
    void severalUnits() throws Exception {
        var s = setup();
        var t = s.t();
        var u2 = secondUnit(t);
        var u3 = secondUnit(t);
        var other = tenant();
        UUID c2 = klass(u2, "Turma Z", s.judo(), s.teacher(), "ACTIVE");
        UUID l1 = future(t, s.a(), s.teacher(), 4);
        UUID l2 = future(u2, c2, s.teacher(), 4);
        UUID c3 = klass(u3, "Turma Y", s.judo(), s.teacher(), "ACTIVE");
        UUID l3 = future(u3, c3, s.teacher(), 4);
        var coord = who(t, Role.COORDINATOR);
        String d = TODAY.plusDays(4).toString();

        assertThat(post(body(d, d, "Recesso", t.unitId(), other.unitId()), coord).getStatus()).isEqualTo(404);
        assertThat(days(t)).isZero();
        assertThat(body(post(body(d, d, "Recesso", t.unitId(), u2.unitId()), coord), 201)).hasSize(2);
        assertThat(days(t)).isEqualTo(2);
        assertThat(status(l1)).isEqualTo("CANCELLED");
        assertThat(status(l2)).isEqualTo("CANCELLED");
        assertThat(status(l3)).isEqualTo("SCHEDULED");

        assertThat(post(body(d, d, "Outra", u2.unitId(), u3.unitId()), coord).getStatus()).isEqualTo(409); // sobrepõe a de u2
        assertThat(days(t)).isEqualTo(2);
        assertThat(status(l3)).isEqualTo("SCHEDULED");
        assertThat(post(body(d, d, "Outra", t.unitId()), who(t, Role.COORDINATOR)).getStatus()).isEqualTo(409);
        assertThat(post(body(TODAY.plusDays(3).toString(), TODAY.plusDays(4).toString(), "Outra", t.unitId()), coord).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA4: turmas: só uma unidade, ativa e da unidade (422); outra organização 404; turma válida cancela só as dela")
    void classes() throws Exception {
        var s = setup();
        var t = s.t();
        var u2 = secondUnit(t);
        var other = tenant();
        UUID foreign = klass(u2, "Turma Z", s.judo(), s.teacher(), "ACTIVE");
        UUID off = klass(t, "Turma Off", s.judo(), s.teacher(), "INACTIVE");
        UUID otherClass = klass(other, "Turma O", modality(other, "Judô"), teacher(other, "Beto", null), "ACTIVE");
        UUID la = future(t, s.a(), s.teacher(), 6);
        UUID lb = future(t, s.b(), s.teacher(), 6);
        var coord = who(t, Role.COORDINATOR);
        String d = TODAY.plusDays(6).toString();

        for (UUID bad : new UUID[] {foreign, off}) {
            Map<String, Object> b = body(d, d, "X", t.unitId());
            b.put("classIds", java.util.List.of(bad.toString()));
            var r = post(b, coord);
            assertThat(r.getStatus()).isEqualTo(422);
            assertThat(field(r)).isEqualTo("classIds");
        }
        Map<String, Object> two = body(d, d, "X", t.unitId(), u2.unitId());
        two.put("classIds", java.util.List.of(s.a().toString()));
        assertThat(post(two, coord).getStatus()).isEqualTo(422);
        Map<String, Object> o = body(d, d, "X", t.unitId());
        o.put("classIds", java.util.List.of(otherClass.toString()));
        assertThat(post(o, coord).getStatus()).isEqualTo(404);
        assertThat(days(t)).isZero();
        assertThat(cancelled(t)).isZero();

        Map<String, Object> ok = body(d, d, "Só A", t.unitId());
        ok.put("classIds", java.util.List.of(s.a().toString()));
        JsonNode r = body(post(ok, coord), 201);
        assertThat(r.get(0).get("allClasses").asBoolean()).isFalse();
        assertThat(r.get(0).get("classes").get(0).get("name").asText()).isEqualTo("Turma A");
        assertThat(status(la)).isEqualTo("CANCELLED");
        assertThat(status(lb)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("CA5, CA7: validações respondem 400 com o campo e nada é gravado; aviso agendado")
    void validation() throws Exception {
        var s = setup();
        var t = s.t();
        var coord = who(t, Role.COORDINATOR);
        String d = TODAY.plusDays(3).toString();
        record Case(String name, String key, Object value, String field) {}
        Case[] cases = {
                new Case("motivo vazio", "reason", "  ", "reason"),
                new Case("motivo longo", "reason", "x".repeat(121), "reason"),
                new Case("data malformada", "startsOn", "2026-13-40", "startsOn"),
                new Case("sem data", "startsOn", null, "startsOn"),
                new Case("fim antes do início", "endsOn", TODAY.plusDays(2).toString(), "endsOn"),
                new Case("início no passado", "startsOn", TODAY.minusDays(1).toString(), "startsOn"),
                new Case("sem unidade", "unitIds", java.util.List.of(), "unitIds"),
                new Case("aviso agendado sem data", "notice", Map.of("mode", "SCHEDULED"), "notice"),
                new Case("aviso agendado no passado", "notice", Map.of("mode", "SCHEDULED", "sendAt", Instant.now().minusSeconds(60).toString()), "notice"),
                new Case("aviso agendado depois do início", "notice", Map.of("mode", "SCHEDULED", "sendAt", Instant.now().plusSeconds(86400L * 10).toString()), "notice"),
                new Case("aviso com modo inválido", "notice", Map.of("mode", "ALWAYS"), "notice"),
        };
        for (Case c : cases) {
            Map<String, Object> b = body(d, d, "Motivo", t.unitId());
            b.put(c.key(), c.value());
            var r = post(b, coord);
            assertThat(r.getStatus()).as(c.name()).isEqualTo(400);
            assertThat(field(r)).as(c.name()).isEqualTo(c.field());
        }
        var r = post(body(TODAY.toString(), TODAY.plusDays(90).toString(), "Longo", t.unitId()), coord);
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(field(r)).isEqualTo("endsOn");
        assertThat(days(t)).isZero();

        Map<String, Object> sched = body(d, d, "Agendado", t.unitId());
        sched.put("notice", Map.of("mode", "SCHEDULED", "sendAt", Instant.now().plusSeconds(3600).toString()));
        JsonNode created = body(post(sched, coord), 201);
        assertThat(created.get(0).get("notice").get("status").asText()).isEqualTo("SCHEDULED");
        assertThat(created.get(0).get("notice").get("sendAt").asText()).isNotBlank();
        Map<String, Object> now = body(TODAY.plusDays(10).toString(), TODAY.plusDays(10).toString(), "Agora", t.unitId());
        now.put("notice", Map.of("mode", "NOW"));
        assertThat(body(post(now, coord), 201).get(0).get("notice").get("status").asText()).isEqualTo("NOT_NOTIFIED");
        Map<String, Object> bare = body(TODAY.plusDays(20).toString(), TODAY.plusDays(20).toString(), "Sem aviso", t.unitId());
        bare.remove("notice");
        assertThat(body(post(bare, coord), 201).get(0).get("notice").get("mode").asText()).isEqualTo("NONE");
        assertThat(post(null, coord).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA13: perfis sem permissão 403, sem sessão 401, Administrador fora do alcance 404 ou com várias unidades 400; RLS recusa sem escopo")
    void access() throws Exception {
        var s = setup();
        var t = s.t();
        var u2 = secondUnit(t);
        String d = TODAY.plusDays(3).toString();
        for (Role r : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(post(body(d, d, "X", t.unitId()), who(t, r)).getStatus()).as(r.name()).isEqualTo(403);
        }
        assertThat(post(body(d, d, "X", t.unitId()), null).getStatus()).isEqualTo(401);
        var admin = admin(t, t.unitId());
        assertThat(post(body(d, d, "X", u2.unitId()), admin).getStatus()).isEqualTo(404);
        assertThat(post(body(d, d, "X", t.unitId(), u2.unitId()), admin).getStatus()).isEqualTo(400);
        assertThat(post(body(d, d, "X", t.unitId(), u2.unitId()), admin(t, t.unitId(), u2.unitId())).getStatus()).isEqualTo(400);
        assertThat(days(t)).isZero();

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            try {
                sql.execute("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values ('" + UUID.randomUUID() + "', '"
                        + t.orgId() + "', '" + t.unitId() + "', current_date, current_date, 'x')");
                throw new AssertionError("RLS deveria recusar");
            } catch (SQLException expected) {
                assertThat(expected.getMessage()).contains("row-level security");
            } finally {
                app.rollback();
            }
            try {
                sql.execute("insert into no_class_day_class (organization_id, no_class_day_id, class_id) values ('" + t.orgId() + "', '" + UUID.randomUUID()
                        + "', '" + s.a() + "')");
                throw new AssertionError("RLS deveria recusar");
            } catch (SQLException expected) {
                assertThat(expected.getMessage()).contains("row-level security");
            } finally {
                app.rollback();
            }
        }
    }

    @Test
    @DisplayName("CA14: falha na auditoria desfaz o dia e as aulas canceladas")
    void allOrNothing() throws Exception {
        var s = setup();
        var t = s.t();
        UUID l = future(t, s.a(), s.teacher(), 3);
        adminJdbc().execute("create function ncd_fail() returns trigger language plpgsql as $$ begin raise exception 'boom'; end $$");
        adminJdbc().execute("create trigger ncd_fail before insert on audit_log for each row when (new.action = 'no_class_day.created') execute function ncd_fail()");
        try {
            String d = TODAY.plusDays(3).toString();
            assertThat(post(body(d, d, "X", t.unitId()), who(t, Role.COORDINATOR)).getStatus()).isGreaterThanOrEqualTo(500);
            assertThat(days(t)).isZero();
            assertThat(status(l)).isEqualTo("SCHEDULED");
            assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'no_class_day.created'", Integer.class)).isZero();
        } finally {
            adminJdbc().execute("drop trigger ncd_fail on audit_log");
            adminJdbc().execute("drop function ncd_fail()");
        }
    }
}
