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
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-AGE-05: ajudantes dos testes de dias sem aula, com banco real. */
abstract class NoClassDayBase extends AgendaBase {
    @Autowired ObjectMapper json;

    static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    static final String PATH = "/api/v1/no-class-days";

    @AfterEach
    void cleanAudit() {
        adminJdbc().execute("alter table audit_log disable trigger user");
        adminJdbc().update("delete from audit_log where action like 'no_class_day.%' or action = 'lesson.generated'");
        adminJdbc().execute("alter table audit_log enable trigger user");
    }

    protected Tenant secondUnit(Tenant t) {
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Zeta', ?)", second, t.orgId(),
                "z" + second.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), second);
        return new Tenant(t.orgId(), second);
    }

    protected AccountPrincipal admin(Tenant t, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
    }

    protected Map<String, Object> body(String start, String end, String reason, UUID... units) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unitIds", java.util.Arrays.stream(units).map(UUID::toString).toList());
        m.put("startsOn", start);
        m.put("endsOn", end);
        m.put("reason", reason);
        m.put("notice", Map.of("mode", "NONE"));
        return m;
    }

    protected MockHttpServletResponse send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Object body, AccountPrincipal who)
            throws Exception {
        return call(body == null ? b : b.contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    protected MockHttpServletResponse post(Object body, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.post(PATH), body, who);
    }

    protected MockHttpServletResponse put(UUID id, Object body, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.put(PATH + "/" + id), body, who);
    }

    protected MockHttpServletResponse delete(UUID id, String query, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.delete(PATH + "/" + id + query), null, who);
    }

    protected MockHttpServletResponse get(String path, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.get(path), null, who);
    }

    protected JsonNode body(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    protected String field(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString()).path("fields").path(0).path("field").asText();
    }

    protected int days(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from no_class_day where organization_id = ?", Integer.class, t.orgId());
    }

    protected int cancelled(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from lesson where organization_id = ? and cancelled", Integer.class, t.orgId());
    }

    protected String status(UUID lesson) {
        return adminJdbc().queryForObject("select case when cancelled then 'CANCELLED' else 'SCHEDULED' end from lesson where id = ?", String.class, lesson);
    }

    protected UUID classIn(Tenant t, String name, UUID modality, UUID head, String status) {
        return klass(t, name, modality, head, status);
    }

    /** Aula futura de uma turma da unidade do tenant, às 19h. */
    protected UUID future(Tenant t, UUID klass, UUID teacher, int plusDays) {
        return lesson(t, klass, teacher, TODAY.plusDays(plusDays), "19:00", "20:00", false, false);
    }

    protected List<String> ids(JsonNode array) {
        List<String> out = new java.util.ArrayList<>();
        array.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }
}
