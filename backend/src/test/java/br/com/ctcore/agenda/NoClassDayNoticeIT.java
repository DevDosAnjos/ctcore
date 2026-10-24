package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.communication.AnnouncementSendJob;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COM-02 (#82, R11): o dia sem aula gera e mantém um único comunicado (RN-AGE-10), com banco real. */
class NoClassDayNoticeIT extends NoClassDayBase {
    @Autowired AnnouncementSendJob job;

    private record Setup(Tenant t, UUID a, UUID b) {}

    private Setup setup() {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        return new Setup(t, klass(t, "Turma A", judo, ana, "ACTIVE"), klass(t, "Turma B", judo, ana, "ACTIVE"));
    }

    private Map<String, Object> day(int plus, String reason, String mode, UUID unit, UUID... classes) {
        String d = TODAY.plusDays(plus).toString();
        Map<String, Object> b = body(d, d, reason, unit);
        Map<String, Object> notice = new HashMap<>(Map.of("mode", mode));
        if ("SCHEDULED".equals(mode)) notice.put("sendAt", Instant.now().plusSeconds(3600).toString());
        b.put("notice", notice);
        if (classes.length > 0) b.put("classIds", java.util.Arrays.stream(classes).map(UUID::toString).toList());
        return b;
    }

    private UUID created(Map<String, Object> b, AccountPrincipal who) throws Exception {
        return UUID.fromString(body(post(b, who), 201).get(0).get("id").asText());
    }

    private int announcements(UUID day) {
        return adminJdbc().queryForObject("select count(*) from announcement where no_class_day_id = ?", Integer.class, day);
    }

    private String dayStatus(UUID day) {
        return adminJdbc().queryForObject("select notice_status from no_class_day where id = ?", String.class, day);
    }

    @Test
    @DisplayName("CA16: NOW gera um comunicado NO_CLASS_DAY enviado (UNIT ou CLASSES) e o dia Enviado; NONE não gera")
    void now() throws Exception {
        var s = setup();
        var coord = who(s.t(), Role.COORDINATOR);
        UUID unit = created(day(5, "Recesso", "NOW", s.t().unitId()), coord);
        var row = adminJdbc().queryForMap("select type, target_kind, class_id, unit_id, sent_at, recipient_count, body from announcement where no_class_day_id = ?", unit);
        assertThat(row.get("type")).isEqualTo("NO_CLASS_DAY");
        assertThat(row.get("target_kind")).isEqualTo("UNIT");
        assertThat(row.get("unit_id")).isEqualTo(s.t().unitId());
        assertThat(row.get("sent_at")).isNotNull();
        assertThat(row.get("body").toString()).contains("Recesso");
        assertThat(dayStatus(unit)).isEqualTo("Enviado");
        assertThat(announcements(unit)).isEqualTo(1);

        UUID some = created(day(6, "Reforma", "NOW", s.t().unitId(), s.a(), s.b()), coord);
        assertThat(adminJdbc().queryForObject("select target_kind from announcement where no_class_day_id = ?", String.class, some)).isEqualTo("CLASSES");
        assertThat(adminJdbc().queryForObject("select count(*) from announcement_class c join announcement a on a.id = c.announcement_id where a.no_class_day_id = ?",
                Integer.class, some)).isEqualTo(2);

        UUID none = created(day(7, "Sem aviso", "NONE", s.t().unitId()), coord);
        assertThat(announcements(none)).isZero();
        assertThat(dayStatus(none)).isEqualTo("Ainda não avisado");
    }

    @Test
    @DisplayName("CA17: SCHEDULED agenda; editar mantém um só; NONE apaga; a tarefa envia e o dia vira Enviado")
    void scheduled() throws Exception {
        var s = setup();
        var coord = who(s.t(), Role.COORDINATOR);
        UUID id = created(day(10, "Original", "SCHEDULED", s.t().unitId()), coord);
        assertThat(dayStatus(id)).isEqualTo("Agendado");
        assertThat(adminJdbc().queryForObject("select sent_at is null from announcement where no_class_day_id = ?", Boolean.class, id)).isTrue();

        assertThat(put(id, day(11, "Editado", "SCHEDULED", s.t().unitId(), s.a()), coord).getStatus()).isEqualTo(200);
        assertThat(announcements(id)).isEqualTo(1);
        var row = adminJdbc().queryForMap("select target_kind, body from announcement where no_class_day_id = ?", id);
        assertThat(row.get("target_kind")).isEqualTo("CLASSES");
        assertThat(row.get("body").toString()).contains("Editado");

        assertThat(put(id, day(11, "Editado", "NONE", s.t().unitId()), coord).getStatus()).isEqualTo(200);
        assertThat(announcements(id)).isZero();
        assertThat(dayStatus(id)).isEqualTo("Ainda não avisado");

        assertThat(put(id, day(11, "Editado", "SCHEDULED", s.t().unitId()), coord).getStatus()).isEqualTo(200);
        adminJdbc().update("update announcement set scheduled_for = now() - interval '1 minute' where no_class_day_id = ?", id);
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(dayStatus(id)).isEqualTo("Enviado");
        assertThat(adminJdbc().queryForObject("select sent_at is not null from announcement where no_class_day_id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("CA18: dia Enviado editado não avisa de novo; Coordenador com duas unidades tem um dia e um comunicado por unidade")
    void sentAndUnits() throws Exception {
        var s = setup();
        var coord = who(s.t(), Role.COORDINATOR);
        UUID id = created(day(5, "Recesso", "NOW", s.t().unitId()), coord);
        Map<String, Object> edit = day(6, "Outro motivo", "NOW", s.t().unitId());
        assertThat(put(id, edit, coord).getStatus()).isEqualTo(200);
        assertThat(announcements(id)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isEqualTo(1);

        var second = secondUnit(s.t());
        String d = TODAY.plusDays(20).toString();
        Map<String, Object> both = body(d, d, "Feriado", s.t().unitId(), second.unitId());
        both.put("notice", Map.of("mode", "NOW"));
        JsonNode r = body(post(both, coord), 201);
        assertThat(r.size()).isEqualTo(2);
        for (JsonNode item : r) assertThat(announcements(UUID.fromString(item.get("id").asText()))).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(distinct unit_id) from announcement where no_class_day_id is not null", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA19: desfazer dia Agendado apaga o comunicado; Enviado com avisar gera Aviso geral e mantém o original; sem avisar, nada")
    void undo() throws Exception {
        var s = setup();
        var coord = who(s.t(), Role.COORDINATOR);
        UUID scheduled = created(day(10, "Agendado", "SCHEDULED", s.t().unitId()), coord);
        assertThat(delete(scheduled, "?notify=true", coord).getStatus()).isEqualTo(204);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isZero();

        UUID sent = created(day(12, "Enviado", "NOW", s.t().unitId(), s.a()), coord);
        assertThat(delete(sent, "?notify=false", coord).getStatus()).isEqualTo(204);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isEqualTo(1);

        UUID again = created(day(14, "Outro", "NOW", s.t().unitId(), s.a()), coord);
        assertThat(delete(again, "?notify=true", coord).getStatus()).isEqualTo(204);
        List<Map<String, Object>> all = adminJdbc().queryForList("select type, title, target_kind, sent_at, no_class_day_id from announcement where title = 'Aulas de volta'");
        assertThat(all).hasSize(1);
        assertThat(all.get(0).get("type")).isEqualTo("GENERAL");
        assertThat(all.get(0).get("target_kind")).isEqualTo("CLASSES");
        assertThat(all.get(0).get("sent_at")).isNotNull();
        assertThat(all.get(0).get("no_class_day_id")).isNull();
        assertThat(adminJdbc().queryForObject("select count(*) from announcement where type = 'NO_CLASS_DAY'", Integer.class)).isEqualTo(2);
    }

    private Map<String, Object> manual(UUID unit, UUID day, String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("targetKind", "UNIT");
        m.put("unitId", unit);
        m.put("type", type);
        m.put("title", "Dia sem aula");
        m.put("body", "Não haverá aula.");
        m.put("channels", List.of());
        m.put("noClassDayId", day);
        return m;
    }

    @Test
    @DisplayName("CA20: comunicado à mão liga ao dia não avisado e o dia vira Enviado; dia já avisado, de outra unidade ou outro tipo recusa sem gravar")
    void manualLink() throws Exception {
        var s = setup();
        var coord = who(s.t(), Role.COORDINATOR);
        var second = secondUnit(s.t());
        UUID day = created(day(5, "Recesso", "NONE", s.t().unitId()), coord);
        UUID elsewhere = created(day(5, "Outra", "NONE", second.unitId()), coord);
        var post = MockMvcRequestBuilders.post("/api/v1/announcements");

        assertThat(field(send(MockMvcRequestBuilders.post("/api/v1/announcements"), manual(s.t().unitId(), day, "GENERAL"), coord))).isEqualTo("type");
        var other = send(MockMvcRequestBuilders.post("/api/v1/announcements"), manual(s.t().unitId(), elsewhere, "NO_CLASS_DAY"), coord);
        assertThat(other.getStatus()).isEqualTo(409);
        assertThat(field(other)).isEqualTo("noClassDayId");
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isZero();

        assertThat(send(post, manual(s.t().unitId(), day, "NO_CLASS_DAY"), coord).getStatus()).isEqualTo(201);
        assertThat(dayStatus(day)).isEqualTo("Enviado");
        assertThat(announcements(day)).isEqualTo(1);
        assertThat(send(MockMvcRequestBuilders.post("/api/v1/announcements"), manual(s.t().unitId(), day, "NO_CLASS_DAY"), coord).getStatus()).isEqualTo(409);
        assertThat(adminJdbc().queryForObject("select count(*) from announcement", Integer.class)).isEqualTo(1);
    }
}
