package br.com.ctcore.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.ctcore.features.FeatureFlags;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.RouteIsolationModelIT;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** CA6 e CA10: o molde #15 ligado a uma rota de teste protegida por {@code require(COLLECTIONS)}, com a identidade real da sessão. */
@Import(PermissionRouteIT.Probe.class)
class PermissionRouteIT extends RouteIsolationModelIT {
    @RestController
    static class Probe {
        @Autowired Permissions permissions;
        @Autowired FeatureFlags flags;

        @GetMapping("/api/v1/permission-probe/units/{unit}")
        ResponseEntity<Object> read(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID unit) {
            return permissions.grant(who, Permission.UNIT_SETTINGS, unit) == Access.NONE
                    ? ResponseEntity.notFound().build() : ResponseEntity.ok(Map.of("unitId", unit));
        }

        @PutMapping("/api/v1/permission-probe/units/{unit}/collections")
        void write(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID unit) {
            permissions.require(who, Permission.COLLECTIONS, unit);
            flags.setEnabled(who.organizationId(), "proof", true);
        }
    }

    @Override protected Authentication login(Tenant tenant, String role) {
        var who = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), tenant.orgId(),
                Role.valueOf(role.substring("ROLE_".length())), Set.of(tenant.unitId()));
        return UsernamePasswordAuthenticationToken.authenticated(who, "synthetic", AuthorityUtils.createAuthorityList(role));
    }

    @Override protected MockHttpServletRequestBuilder read(Tenant t) {
        return get("/api/v1/permission-probe/units/" + t.unitId());
    }
    @Override protected MockHttpServletRequestBuilder readMissing() {
        return get("/api/v1/permission-probe/units/" + UUID.randomUUID());
    }
    @Override protected MockHttpServletRequestBuilder write(Tenant t) {
        return put("/api/v1/permission-probe/units/" + t.unitId() + "/collections");
    }
    @Override protected Set<String> allowedRoles() { return Set.of("ROLE_ADMINISTRATOR", "ROLE_COORDINATOR"); }
    @Override protected Set<String> deniedRoles() { return Set.of("ROLE_TEACHER", "ROLE_STUDENT", "ROLE_GUARDIAN"); }
    @Override protected Object state(Tenant t) {
        return adminJdbc().queryForList("select feature_key, enabled from organization_feature where organization_id = ?", t.orgId())
                .stream().map(Object::toString).toList();
    }

    @Test
    @DisplayName("CA6: Professor recebe 403 FORBIDDEN em require(COLLECTIONS) e nada é gravado; Administrador da unidade, 2xx")
    void teacherForbiddenAdministratorAllowed() throws Exception {
        var a = tenant();
        var before = state(a);
        var denied = mvc.perform(write(a).secure(true).with(csrf()).with(authentication(login(a, "ROLE_TEACHER")))).andReturn().getResponse();
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(denied.getContentAsString()).contains("FORBIDDEN");
        assertThat(state(a)).isEqualTo(before);
        var ok = mvc.perform(write(a).secure(true).with(csrf()).with(authentication(login(a, "ROLE_ADMINISTRATOR")))).andReturn().getResponse();
        assertThat(ok.getStatus()).isBetween(200, 299);
    }
}
