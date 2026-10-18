package br.com.ctcore.staff;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.Organizations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Apoio das rotas de /users: organizações e vínculos criados direto no banco (fora da RLS), chamada com o principal montado. */
@AutoConfigureMockMvc
abstract class StaffBase extends PostgresIT {
    @Autowired protected MockMvc mvc;
    @Autowired protected Organizations organizations;
    @Autowired protected ObjectMapper json;

    record Tenant(UUID orgId, UUID unitId) {}

    /** Pessoa da gestão com conta e vínculo reais no banco. */
    record Member(UUID account, UUID link, UUID orgId, Role role, Set<UUID> units, String email) {
        AccountPrincipal principal() {
            return new AccountPrincipal(account, UUID.randomUUID(), link, orgId, role, units);
        }
    }

    protected Tenant tenant() {
        UUID id = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(id, "Synthetic " + id, UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        return new Tenant(input.id(), input.unitId());
    }

    protected UUID addUnit(Tenant t, String name, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, ?, ?, ?)", id, t.orgId(), name, "u-" + id, active);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), id);
        return id;
    }

    protected Member member(Tenant t, Role role, String name, String status, UUID... units) {
        UUID account = UUID.randomUUID();
        UUID link = UUID.randomUUID();
        String email = name.toLowerCase().replace(' ', '.') + "." + account + "@synthetic.example";
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, ?, 'ACTIVE')", account, email, name);
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, ?)",
                link, account, t.orgId(), role.name(), status);
        for (UUID unit : units) {
            adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", link, t.orgId(), unit);
        }
        return new Member(account, link, t.orgId(), role, Set.of(units), email);
    }

    protected Member coordinator(Tenant t) {
        var m = member(t, Role.COORDINATOR, "Coord", "ACTIVE");
        adminJdbc().update("insert into account_two_factor (account_id, secret_encrypted, enabled_at) values (?, 'x', now())", m.account());
        return m;
    }

    protected MockHttpServletResponse call(MockHttpServletRequestBuilder request, Member who) throws Exception {
        request.secure(true).with(csrf());
        if (who != null) {
            request.with(authentication(UsernamePasswordAuthenticationToken.authenticated(who.principal(), "synthetic",
                    AuthorityUtils.createAuthorityList("ROLE_" + who.role().name()))));
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    protected static MockHttpServletRequestBuilder list(String query) {
        return MockMvcRequestBuilders.get("/api/v1/users" + query);
    }

    protected static MockHttpServletRequestBuilder invite(String body) {
        return MockMvcRequestBuilders.post("/api/v1/users").contentType("application/json").content(body);
    }

    protected static MockHttpServletRequestBuilder resend(UUID link) {
        return MockMvcRequestBuilders.post("/api/v1/users/" + link + "/resend");
    }

    protected static MockHttpServletRequestBuilder units(UUID link, UUID... units) {
        return MockMvcRequestBuilders.put("/api/v1/users/" + link + "/units").contentType("application/json").content(unitsBody(units));
    }

    protected static MockHttpServletRequestBuilder deactivate(UUID link) {
        return MockMvcRequestBuilders.delete("/api/v1/users/" + link);
    }

    protected static MockHttpServletRequestBuilder appoint(UUID... units) {
        return MockMvcRequestBuilders.post("/api/v1/users/coordinator-appointment").contentType("application/json").content(unitsBody(units));
    }

    protected static String unitsBody(UUID... units) {
        return "{\"unitIds\":[" + String.join(",", java.util.Arrays.stream(units).map(u -> "\"" + u + "\"").toList()) + "]}";
    }

    protected static String inviteBody(String email, String name, UUID... units) {
        return "{\"email\":\"" + email + "\",\"name\":\"" + name + "\",\"unitIds\":" + unitsBody(units).substring(11);
    }

    protected static List<String> names(JsonNode rows) {
        return java.util.stream.StreamSupport.stream(rows.spliterator(), false).map(r -> r.path("name").asText()).toList();
    }

    protected JsonNode body(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString());
    }

    protected String status(UUID link) {
        return adminJdbc().queryForObject("select status from account_link where id = ?", String.class, link);
    }

    protected List<UUID> unitsOf(UUID link) {
        return adminJdbc().queryForList("select unit_id from account_link_unit where link_id = ?", UUID.class, link);
    }

    protected List<Map<String, Object>> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select actor_profile, actor_id, before_data::text as b, after_data::text as a, entity_id "
                + "from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }
}
