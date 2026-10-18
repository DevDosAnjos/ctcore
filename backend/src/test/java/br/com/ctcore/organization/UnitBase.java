package br.com.ctcore.organization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
