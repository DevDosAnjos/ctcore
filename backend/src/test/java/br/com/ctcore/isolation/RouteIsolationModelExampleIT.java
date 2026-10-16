package br.com.ctcore.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import br.com.ctcore.features.FeatureFlags;
import br.com.ctcore.organization.Organizations;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exemplo funcional do molde (RNF-05, RNF-31): liga so os pontos de extensao. */
@Import({RouteIsolationModelExampleIT.Example.class, RouteIsolationModelExampleIT.Unprotected.class})
class RouteIsolationModelExampleIT extends RouteIsolationModelIT {
    @RestController
    static class Example {
        @Autowired Organizations organizations;
        @Autowired FeatureFlags flags;

        @GetMapping("/api/v1/model-example/organizations/{org}/units/{unit}/settings")
        ResponseEntity<Object> read(@PathVariable UUID org, @PathVariable UUID unit) {
            return organizations.unitSettings(org, unit).<ResponseEntity<Object>>map(row -> ResponseEntity.ok(java.util.Map.of("unitId", unit)))
                    .orElse(ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
        @PutMapping("/api/v1/model-example/organizations/{org}/features/proof")
        void write(@PathVariable UUID org) { flags.setEnabled(org, "proof", true); }
    }

    /** Mesma escrita, sem @PreAuthorize: o molde tem de perceber. */
    @RestController
    static class Unprotected {
        @Autowired FeatureFlags flags;

        @PutMapping("/api/v1/model-example/unprotected/{org}/features/proof")
        void write(@PathVariable UUID org) { flags.setEnabled(org, "proof", true); }
    }

    @Override protected MockHttpServletRequestBuilder read(Tenant t) {
        return get("/api/v1/model-example/organizations/" + t.orgId() + "/units/" + t.unitId() + "/settings");
    }
    @Override protected MockHttpServletRequestBuilder readMissing() {
        return get("/api/v1/model-example/organizations/" + UUID.randomUUID() + "/units/" + UUID.randomUUID() + "/settings");
    }
    @Override protected MockHttpServletRequestBuilder write(Tenant t) {
        return put("/api/v1/model-example/organizations/" + t.orgId() + "/features/proof");
    }
    @Override protected Set<String> allowedRoles() { return Set.of("ROLE_MANAGER", "ROLE_ADMIN"); }
    @Override protected Set<String> deniedRoles() { return Set.of("ROLE_TEACHER"); }
    @Override protected Object state(Tenant t) {
        return adminJdbc().queryForList("select feature_key, enabled from organization_feature where organization_id = ?", t.orgId())
                .stream().map(Object::toString).toList();
    }

    @Test
    @DisplayName("RNF-05: without @PreAuthorize the denied profile reaches the write, so the model catches the missing protection")
    void modelCatchesMissingPreAuthorize() throws Exception {
        var a = tenant();
        var before = state(a);
        var response = mvc.perform(put("/api/v1/model-example/unprotected/" + a.orgId() + "/features/proof")
                .secure(true).with(csrf()).with(authentication(login(a, "ROLE_TEACHER")))).andReturn().getResponse();
        assertThat(response.getStatus()).isBetween(200, 299);
        assertThat(state(a)).isNotEqualTo(before);
    }
}
