package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-AGE-03: POST /agenda/lessons/{id}/cancel com banco real. */
class AgendaLessonCancelIT extends AgendaBase {
    @Autowired ObjectMapper json;

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private static Map<String, Object> body(String reason, String text, List<String> channels) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reason", reason);
        if (text != null) m.put("reasonText", text);
        if (channels != null) m.put("channels", channels);
        return m;
    }

    private MockHttpServletResponse post(Object id, Map<String, Object> body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/agenda/lessons/" + id + "/cancel").contentType("application/json")
                .content(json.writeValueAsString(body)), who).getResponse();
    }

    private String field(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).path("fields").path(0).path("field").asText();
    }

    private boolean cancelled(UUID id) {
        return adminJdbc().queryForObject("select cancelled from lesson where id = ?", Boolean.class, id);
    }

    @Test
    @DisplayName("RN-AGE-05: CA6, CA8, CA10: cada motivo cancela; texto só em OTHER; canais na auditoria com o portal; lista traz o código")
    void reasons() throws Exception {
        var t = tenant();
        UUID ana = teacher(t, "Ana", null);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        var coord = who(t, Role.COORDINATOR);
        int day = 3;
        for (String reason : List.of("TEACHER_UNAVAILABLE", "HOLIDAY", "ACADEMY_EVENT")) {
            UUID l = lesson(t, c, ana, TODAY.plusDays(day++), "19:00", "20:00", false, false);
            var r = post(l, body(reason, "ignorado", List.of("EMAIL", "WHATSAPP")), coord);
            assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
            JsonNode d = json.readTree(r.getContentAsString());
            assertThat(d.get("status").asText()).isEqualTo("CANCELLED");
            assertThat(d.get("cancelReasonCode").asText()).isEqualTo(reason);
            assertThat(d.get("cancelReason").isNull()).isTrue();
            assertThat(adminJdbc().queryForObject("select cancel_reason from lesson where id = ?", String.class, l)).isNull();
            assertThat(d.get("canCancel").asBoolean()).isFalse();
        }
        UUID other = lesson(t, c, ana, TODAY.plusDays(20), "19:00", "20:00", false, false);
        var r = post(other, body("OTHER", " Chuva forte ", List.of("EMAIL")), coord);
        assertThat(json.readTree(r.getContentAsString()).get("cancelReason").asText()).isEqualTo("Chuva forte");
        String audit = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'lesson.cancelled' and entity_id = ?",
                String.class, other.toString());
        assertThat(audit).contains("PORTAL", "EMAIL", "OTHER").doesNotContain("WHATSAPP");
        assertThat(adminJdbc().queryForObject("select reason from audit_log where action = 'lesson.cancelled' and entity_id = ?", String.class,
                other.toString())).isEqualTo("OTHER");

        var list = call(MockMvcRequestBuilders.get("/api/v1/agenda?from=" + TODAY.plusDays(20) + "&to=" + TODAY.plusDays(20)), coord).getResponse();
        assertThat(json.readTree(list.getContentAsString()).get("lessons").get(0).get("cancelReasonCode").asText()).isEqualTo("OTHER");
    }

    @Test
    @DisplayName("CA6, CA9, CA10: 400, 409, Professor, perfis, outra organização; cancelada por dia sem aula segue com o texto")
    void rules() throws Exception {
        var t = tenant();
        UUID acc = account("ana");
        UUID ana = teacher(t, "Ana", acc);
        UUID c = klass(t, "Judô", modality(t, "Judô"), ana, "ACTIVE");
        UUID l = lesson(t, c, ana, TODAY.plusDays(3), "19:00", "20:00", false, false);
        var coord = who(t, Role.COORDINATOR);
        assertThat(field(post(l, body("OTHER", null, null), coord))).isEqualTo("reasonText");
        assertThat(field(post(l, body("OTHER", "x".repeat(201), null), coord))).isEqualTo("reasonText");
        assertThat(field(post(l, body("XYZ", null, null), coord))).isEqualTo("reason");
        assertThat(field(post(l, body("HOLIDAY", null, List.of("SMS")), coord))).isEqualTo("channels");
        Map<String, Object> badTrial = body("HOLIDAY", null, null);
        badTrial.put("trialDecision", "NOPE");
        assertThat(field(post(l, badTrial, coord))).isEqualTo("trialDecision");
        assertThat(cancelled(l)).isFalse();
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'lesson.cancelled'", Integer.class)).isZero();

        UUID done = lesson(t, c, ana, TODAY.minusDays(2), "19:00", "20:00", false, false);
        assertThat(post(done, body("HOLIDAY", null, null), coord).getStatus()).isEqualTo(409);
        UUID noClassDay = lesson(t, c, ana, TODAY.plusDays(6), "19:00", "20:00", true, false);
        assertThat(post(noClassDay, body("HOLIDAY", null, null), coord).getStatus()).isEqualTo(409);
        assertThat(json.readTree(call(MockMvcRequestBuilders.get("/api/v1/agenda/lessons/" + noClassDay), coord).getResponse().getContentAsString())
                .get("cancelReason").asText()).isEqualTo("Motivo");

        var me = new AccountPrincipal(acc, UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(t.unitId()));
        assertThat(post(l, body("TEACHER_UNAVAILABLE", null, null), me).getStatus()).isEqualTo(200);
        assertThat(cancelled(l)).isTrue();

        var b = tenant();
        UUID bt = teacher(b, "Bia", null);
        UUID bl = lesson(b, klass(b, "Judô B", modality(b, "Judô"), bt, "ACTIVE"), bt, TODAY.plusDays(3), "19:00", "20:00", false, false);
        assertThat(post(bl, body("HOLIDAY", null, null), coord).getStatus()).isEqualTo(404);
        assertThat(cancelled(bl)).isFalse();
        assertThat(post(bl, body("HOLIDAY", null, null), who(b, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(post(bl, body("HOLIDAY", null, null), who(b, Role.GUARDIAN)).getStatus()).isEqualTo(403);
        assertThat(post(bl, body("HOLIDAY", null, null), null).getStatus()).isEqualTo(401);
        assertThat(post("abc", body("HOLIDAY", null, null), who(b, Role.COORDINATOR)).getStatus()).isEqualTo(400);
        var outOfReach = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), b.orgId(), Role.ADMINISTRATOR,
                Set.of(UUID.randomUUID()));
        assertThat(post(bl, body("HOLIDAY", null, null), outOfReach).getStatus()).isEqualTo(404);
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,false,true,true,false}' where unit_id = ?", t.unitId());
        UUID l2 = lesson(t, c, ana, TODAY.plusDays(9), "19:00", "20:00", false, false);
        assertThat(post(l2, body("HOLIDAY", null, null), me).getStatus()).isEqualTo(403);
    }
}
