package br.com.ctcore.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.Organizations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class PermissionControllerIT extends PostgresIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Organizations organizations;

    UUID orgId, unitId;

    void org() {
        orgId = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(orgId, "Synthetic", UUID.randomUUID(), "Unit", orgId.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        unitId = input.unitId();
    }

    JsonNode call(Role role, UUID org, Set<UUID> units) throws Exception {
        var who = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), org == null ? null : UUID.randomUUID(), org, role, units);
        var response = mvc.perform(get("/api/v1/permissions").secure(true).with(authentication(
                UsernamePasswordAuthenticationToken.authenticated(who, null, AuthorityUtils.NO_AUTHORITIES)))).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).path("permissions");
    }

    long auditRows() {
        return adminJdbc().queryForObject("select count(*) from audit_log", Long.class);
    }

    @Test
    @DisplayName("CA7 / CA8 / CA15: níveis por perfil conforme a tabela, Professor pelas permissões da unidade e nada vai para a auditoria")
    void effectiveByRole() throws Exception {
        org();
        long audit = auditRows();
        var coordinator = call(Role.COORDINATOR, orgId, Set.of());
        assertThat(coordinator.path("ALL_UNITS_CONSOLIDATED").asText()).isEqualTo("READ");
        assertThat(coordinator.path("ORGANIZATION_SETTINGS").asText()).isEqualTo("FULL");
        var administrator = call(Role.ADMINISTRATOR, orgId, Set.of(unitId));
        assertThat(administrator.path("ORGANIZATION_SETTINGS").asText()).isEqualTo("READ");
        assertThat(administrator.path("UNITS").asText()).isEqualTo("NONE");
        assertThat(administrator.path("SUBSCRIPTION").asText()).isEqualTo("NOTICE");
        var teacher = call(Role.TEACHER, orgId, Set.of(unitId));
        assertThat(teacher.path("COLLECTIONS").asText()).isEqualTo("NONE");
        assertThat(teacher.path("REPORTS").asText()).isEqualTo("OWN");
        assertThat(teacher.path("STUDENT_CONTACTS").asText()).isEqualTo("READ");
        assertThat(teacher.size()).isEqualTo(Permission.values().length);

        adminJdbc().update("update unit_settings set teacher_permissions = '{false,false,false,false,false}' where unit_id = ?", unitId);
        var off = call(Role.TEACHER, orgId, Set.of(unitId));
        for (String code : new String[] {"ANNOUNCEMENTS", "AGENDA_EDIT", "STUDENT_CONTACTS", "TRIAL_CLASSES"}) {
            assertThat(off.path(code).asText()).isEqualTo("NONE");
        }
        assertThat(auditRows()).isEqualTo(audit);
    }

    @Test
    @DisplayName("CA9: sem sessão 401; Aluno, Responsável e sessão sem vínculo ativo recebem 200 com tudo NONE")
    void anonymousPortalAndNoLink() throws Exception {
        org();
        assertThat(mvc.perform(get("/api/v1/permissions").secure(true)).andReturn().getResponse().getStatus()).isEqualTo(401);
        for (JsonNode view : new JsonNode[] {call(Role.STUDENT, orgId, Set.of()), call(Role.GUARDIAN, orgId, Set.of()), call(null, null, Set.of())}) {
            assertThat(view.size()).isEqualTo(Permission.values().length);
            view.forEach(v -> assertThat(v.asText()).isEqualTo("NONE"));
        }
    }
}
