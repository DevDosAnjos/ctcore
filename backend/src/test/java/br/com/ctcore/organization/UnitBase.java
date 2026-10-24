package br.com.ctcore.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Apoio das rotas de /units: unidades e Administradores criados direto no banco, fora da RLS. */
abstract class UnitBase extends OrganizationBase {
    @Autowired ObjectMapper json;

    protected static MockHttpServletRequestBuilder list() {
        return MockMvcRequestBuilders.get("/api/v1/units");
    }

    protected static MockHttpServletRequestBuilder create(String json) {
        return MockMvcRequestBuilders.post("/api/v1/units").contentType("application/json").content(json);
    }

    protected static MockHttpServletRequestBuilder mine() {
        return MockMvcRequestBuilders.get("/api/v1/units/mine");
    }

    protected static MockHttpServletRequestBuilder read(UUID unitId) {
        return MockMvcRequestBuilders.get("/api/v1/units/" + unitId);
    }

    protected static MockHttpServletRequestBuilder put(UUID unitId, JsonNode body) {
        return MockMvcRequestBuilders.put("/api/v1/units/" + unitId).contentType("application/json").content(body.toString());
    }

    /** Corpo completo e válido do PUT, com os valores padrão da unidade; cada teste muda o que precisa. */
    protected ObjectNode payload() {
        ObjectNode n = json.createObjectNode();
        n.put("name", "Unidade");
        ObjectNode s = n.putObject("settings");
        s.putArray("dueDays").add(1).add(5).add(10).add(15);
        s.put("enrollmentFeeEnabled", false).put("enrollmentFeeCents", 0);
        ObjectNode fees = s.putObject("feesCents");
        for (String key : new String[] {"cash", "pix", "bankSlip", "creditCard", "debitCard", "automaticDebit", "manualPix", "terminalCard", "transfer"}) {
            fees.put(key, 0);
        }
        s.putArray("reminderDays").add(-3).add(0).add(3).add(10);
        s.putObject("teacherPermissions").put("announcements", true).put("agendaEdit", true).put("studentContacts", true).put("trialClasses", true).put("studentRecord", false);
        s.put("directoryEnabled", false).put("trialLinkEnabled", true).put("selfRegistrationLinkEnabled", true);
        s.put("remindersEnabled", true).put("remindersHour", 9);
        return n;
    }

    protected int unitCount(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from unit where organization_id = ?", Integer.class, t.orgId());
    }

    protected static MockHttpServletRequestBuilder adjust(UUID unitId, String shortName) {
        return MockMvcRequestBuilders.patch("/api/v1/units/" + unitId + "/short-name").contentType("application/json")
                .content("{\"shortName\":" + (shortName == null ? "null" : "\"" + shortName + "\"") + "}");
    }

    protected UUID addUnit(Tenant t, String name, String shortName, String city) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, city) values (?, ?, ?, ?, ?)", id, t.orgId(), name, shortName, city);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), id);
        return id;
    }

    /** Administrador com o status do vínculo dado, ligado às unidades dadas. */
    protected void addAdministrator(Tenant t, String name, String linkStatus, UUID... units) {
        UUID account = UUID.randomUUID();
        UUID link = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, ?, 'ACTIVE')", account, account + "@synthetic.example", name);
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, 'ADMINISTRATOR', ?)",
                link, account, t.orgId(), linkStatus);
        for (UUID unit : units) adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", link, t.orgId(), unit);
    }

    protected JsonNode body(org.springframework.mock.web.MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    protected Map<String, Object> unitRow(UUID unitId) {
        return adminJdbc().queryForMap("select short_name, short_name_adjusted from unit where id = ?", unitId);
    }

    protected List<Map<String, Object>> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select actor_profile, before_data::text as b, after_data::text as a, entity_id from audit_log "
                + "where organization_id = ? and action = ?", t.orgId(), action);
    }
}
