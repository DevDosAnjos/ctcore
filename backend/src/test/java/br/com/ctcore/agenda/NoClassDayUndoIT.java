package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-AGE-05: PUT e DELETE /no-class-days/{id}, com banco real. */
class NoClassDayUndoIT extends NoClassDayBase {
    private record Setup(Tenant t, UUID teacher, UUID a, UUID b) {}

    private Setup setup() {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        return new Setup(t, ana, klass(t, "Turma A", judo, ana, "ACTIVE"), klass(t, "Turma B", judo, ana, "ACTIVE"));
    }

    private UUID create(Setup s, int plus, int plusEnd, String reason, UUID... classes) throws Exception {
        Map<String, Object> b = body(TODAY.plusDays(plus).toString(), TODAY.plusDays(plusEnd).toString(), reason, s.t().unitId());
        if (classes.length > 0) b.put("classIds", java.util.Arrays.stream(classes).map(UUID::toString).toList());
        return UUID.fromString(body(post(b, who(s.t(), Role.COORDINATOR)), 201).get(0).get("id").asText());
    }

    @Test
    @DisplayName("CA11: editar devolve as aulas que saíram, cancela as novas e audita; dia iniciado 409; aviso Enviado imutável")
    void edit() throws Exception {
        var s = setup();
        var t = s.t();
        var coord = who(t, Role.COORDINATOR);
        UUID la5 = future(t, s.a(), s.teacher(), 5);
        UUID lb5 = future(t, s.b(), s.teacher(), 5);
        UUID lb8 = future(t, s.b(), s.teacher(), 8);
        UUID id = create(s, 5, 5, "Original");
        assertThat(status(la5)).isEqualTo("CANCELLED");
        assertThat(status(lb5)).isEqualTo("CANCELLED");

        Map<String, Object> b = body(TODAY.plusDays(8).toString(), TODAY.plusDays(8).toString(), "Editado", t.unitId());
        b.put("classIds", java.util.List.of(s.b().toString()));
        JsonNode r = body(put(id, b, coord), 200);
        assertThat(r.get("reason").asText()).isEqualTo("Editado");
        assertThat(r.get("cancelledLessons").asInt()).isEqualTo(1);
        assertThat(r.get("classes")).hasSize(1);
        assertThat(status(la5)).isEqualTo("SCHEDULED");
        assertThat(status(lb5)).isEqualTo("SCHEDULED");
        assertThat(status(lb8)).isEqualTo("CANCELLED");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'no_class_day.updated' and entity_id = ?", Integer.class, id.toString()))
                .isEqualTo(1);
        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'no_class_day.updated'", String.class);
        assertThat(after).contains("\"releasedLessons\": 2", "\"cancelledLessons\": 1");

        assertThat(put(id, body(TODAY.plusDays(8).toString(), TODAY.plusDays(7).toString(), "X", t.unitId()), coord).getStatus()).isEqualTo(400);
        UUID other = create(s, 12, 12, "Outro");
        assertThat(put(id, body(TODAY.plusDays(11).toString(), TODAY.plusDays(12).toString(), "X", t.unitId()), coord).getStatus()).isEqualTo(409); // sobrepõe
        assertThat(put(other, body(TODAY.plusDays(12).toString(), TODAY.plusDays(12).toString(), "Mesmo dia", t.unitId()), coord).getStatus()).isEqualTo(200);

        UUID started = UUID.randomUUID();
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, 'Hoje')", started, t.orgId(),
                t.unitId(), TODAY, TODAY);
        assertThat(put(started, body(TODAY.plusDays(30).toString(), TODAY.plusDays(30).toString(), "X", t.unitId()), coord).getStatus()).isEqualTo(409);

        adminJdbc().update("update no_class_day set notice_status = 'Enviado', notice_mode = 'NOW' where id = ?", id);
        Map<String, Object> changed = body(TODAY.plusDays(8).toString(), TODAY.plusDays(8).toString(), "Editado", t.unitId());
        changed.put("notice", Map.of("mode", "NONE"));
        var denied = put(id, changed, coord);
        assertThat(denied.getStatus()).isEqualTo(400);
        assertThat(field(denied)).isEqualTo("notice");
        Map<String, Object> same = body(TODAY.plusDays(8).toString(), TODAY.plusDays(8).toString(), "Sem mexer no aviso", t.unitId());
        same.put("notice", Map.of("mode", "NOW"));
        assertThat(body(put(id, same, coord), 200).get("notice").get("status").asText()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("CA12: desfazer apaga o dia, devolve só as aulas dele e guarda notify; outras unidades intactas; dia iniciado 409")
    void undo() throws Exception {
        var s = setup();
        var t = s.t();
        var u2 = secondUnit(t);
        UUID c2 = klass(u2, "Turma Z", modality(u2, "Muay Thai"), s.teacher(), "ACTIVE");
        var coord = who(t, Role.COORDINATOR);
        UUID mine = future(t, s.a(), s.teacher(), 6);
        UUID manual = lesson(t, s.a(), s.teacher(), TODAY.plusDays(6), "21:00", "22:00", true, false);
        UUID theirs = future(u2, c2, s.teacher(), 6);
        String d = TODAY.plusDays(6).toString();
        JsonNode created = body(post(body(d, d, "Recesso", t.unitId(), u2.unitId()), coord), 201);
        UUID id1 = UUID.fromString(created.get(0).get("id").asText());
        UUID id2 = UUID.fromString(created.get(1).get("id").asText());
        assertThat(status(mine)).isEqualTo("CANCELLED");

        assertThat(delete(id1, "?notify=true", coord).getStatus()).isEqualTo(204);
        assertThat(status(mine)).isEqualTo("SCHEDULED");
        assertThat(status(manual)).isEqualTo("CANCELLED");
        assertThat(status(theirs)).isEqualTo("CANCELLED");
        assertThat(days(t)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from no_class_day where id = ?", Integer.class, id2)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select after_data::text from audit_log where action = 'no_class_day.deleted'", String.class))
                .contains("\"notify\": true", "\"releasedLessons\": 1");
        assertThat(delete(id1, "", coord).getStatus()).isEqualTo(404);

        UUID started = UUID.randomUUID();
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, 'Hoje')", started, t.orgId(),
                t.unitId(), TODAY, TODAY);
        assertThat(delete(started, "", coord).getStatus()).isEqualTo(409);
        assertThat(delete(id2, "", admin(t, t.unitId())).getStatus()).isEqualTo(404); // unidade fora do alcance
        assertThat(delete(id2, "", who(t, Role.COORDINATOR)).getStatus()).isEqualTo(204);
        assertThat(status(theirs)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("CA13: perfis sem permissão 403, sem sessão 401, dia de outra organização 404")
    void access() throws Exception {
        var s = setup();
        var t = s.t();
        var other = tenant();
        UUID id = create(s, 4, 4, "Dia");
        String d = TODAY.plusDays(4).toString();
        for (Role r : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(put(id, body(d, d, "X", t.unitId()), who(t, r)).getStatus()).as(r.name()).isEqualTo(403);
            assertThat(delete(id, "", who(t, r)).getStatus()).as(r.name()).isEqualTo(403);
        }
        assertThat(put(id, body(d, d, "X", t.unitId()), null).getStatus()).isEqualTo(401);
        assertThat(delete(id, "", null).getStatus()).isEqualTo(401);
        assertThat(put(id, body(d, d, "X", t.unitId()), who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(delete(id, "", who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(days(t)).isEqualTo(1);
        assertThat(delete(UUID.randomUUID(), "", who(t, Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }
}
