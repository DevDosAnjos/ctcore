package br.com.ctcore.isolation;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.organization.Organizations;
import br.com.ctcore.features.FeatureFlags;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
@Import(OrganizationIsolationIT.Probe.class)
class OrganizationIsolationIT extends PostgresIT {
    @Autowired Organizations organizations;
    @Autowired FeatureFlags flags;
    @Autowired OrganizationTransactions transactions;
    @Autowired JdbcTemplate runtime;
    @Autowired MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping("/api/v1/isolation-probe") String scope() { return OrganizationContext.organizationId().toString(); }
        @GetMapping("/api/v1/isolation-failure") String failure() { throw new IllegalStateException("synthetic failure"); }
        @GetMapping("/actuator/isolation-public-probe") String infrastructure() { return "ok"; }
    }

    Organizations.InitialOrganization create() {
        UUID id = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(id, "Synthetic", UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        return input;
    }

    @Test
    @DisplayName("RN-ORG-02 / RNF-06: explicit filters and transaction-local settings survive pool reuse and rollback")
    void filtersAndTransactions() throws Exception {
        var a = create(); var b = create();
        assertThat(organizations.settings(a.id())).isEmpty();
        assertThat(organizations.limits(a.id())).isEmpty();
        assertThat(flags.isEnabled(a.id(), "proof")).isFalse();
        assertThatThrownBy(() -> flags.setEnabled(a.id(), "proof", true)).isInstanceOf(AccessDeniedException.class);
        assertThat(transactions.execute(() -> runtime.queryForObject("select count(*) from organization", Integer.class))).isZero();
        try (var scope = Scopes.open(a.id(), a.unitId())) {
            assertThat(organizations.settings(a.id())).isPresent();
            assertThat(organizations.settings(b.id())).isEmpty();
            assertThat(organizations.limits(b.id())).isEmpty();
            assertThat(organizations.unitSettings(b.id(), b.unitId())).isEmpty();
            assertThat(organizations.unitSettings(a.id(), b.unitId())).isEmpty();
            assertThat(organizations.unitSettings(a.id(), a.unitId())).isPresent();
            flags.setEnabled(a.id(), "proof", true);
            assertThat(flags.isEnabled(b.id(), "proof")).isFalse();
            assertThatThrownBy(() -> organizations.initialize(b, Organizations.Defaults.standard())).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> transactions.execute(() -> transactions.provision(b.id(), () -> null)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThat(transactions.execute(() -> transactions.execute(() ->
                    runtime.queryForObject("select id from organization", UUID.class)))).isEqualTo(a.id());
            assertThatThrownBy(() -> transactions.execute(() -> {
                runtime.update("insert into organization_feature (organization_id, feature_key, enabled) values (?, 'rollback', true)", a.id());
                throw new IllegalStateException("rollback");
            })).hasMessage("rollback");
        }
        assertThat(adminJdbc().queryForObject("select count(*) from organization_feature where feature_key = 'rollback'", Integer.class)).isZero();
        assertThat(runtime.queryForObject("select nullif(current_setting('app.organization_id', true), '')", String.class)).isNull();
        assertThatThrownBy(() -> transactions.execute(() -> transactions.provision(a.id(), () -> null)))
                .isInstanceOf(AccessDeniedException.class);
        try (var scope = Scopes.open(b.id(), null)) {
            assertThat(organizations.unitSettings(b.id(), b.unitId())).isEmpty();
            assertThat(transactions.execute(() -> runtime.queryForObject("select id from organization", UUID.class))).isEqualTo(b.id());
        }
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { try (var scope = Scopes.open(a.id(), null)) {
                return transactions.execute(() -> runtime.queryForObject("select id from organization", UUID.class));
            }});
            var second = executor.submit(() -> { try (var scope = Scopes.open(b.id(), null)) {
                return transactions.execute(() -> runtime.queryForObject("select id from organization", UUID.class));
            }});
            assertThat(first.get()).isEqualTo(a.id());
            assertThat(second.get()).isEqualTo(b.id());
        }
    }

    private org.springframework.security.core.Authentication auth(Object principal, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, "synthetic", AuthorityUtils.createAuthorityList(role));
    }

    @Test
    @DisplayName("RNF-06: authenticated claims alone establish scope; headers, roles and foreign units do not")
    void requestScope() throws Exception {
        var a = create(); var b = create();
        String path = "/api/v1/isolation-probe";
        mvc.perform(get(path).secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).secure(true).with(authentication(auth("unscoped", "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        for (String role : new String[]{"ROLE_MANAGER", "ROLE_TEACHER"}) {
            var principal = new Scopes(a.id(), a.unitId(), Set.of(a.unitId()));
            mvc.perform(get(path).secure(true).with(authentication(auth(principal, role)))
                    .header("X-Organization-Id", b.id()).param("organizationId", b.id().toString()))
                    .andExpect(status().isOk()).andExpect(content().string(a.id().toString()));
            assertThat(OrganizationContext.organizationId()).isNull();
        }
        for (var invalid : new Scopes[]{new Scopes(a.id(), a.unitId(), Set.of()),
                new Scopes(a.id(), b.unitId(), Set.of(b.unitId())), new Scopes(null, null, Set.of())}) {
            mvc.perform(get(path).secure(true).with(authentication(auth(invalid, "ROLE_ADMIN"))))
                    .andExpect(status().isForbidden());
            assertThat(OrganizationContext.organizationId()).isNull();
        }
        var identity = auth(new Scopes(a.id(), null, Set.of()), "ROLE_MANAGER");
        mvc.perform(get("/api/v1/isolation-failure").secure(true).with(authentication(identity)))
                .andExpect(status().isInternalServerError());
        assertThat(OrganizationContext.organizationId()).isNull();
        mvc.perform(get(path).secure(true).with(authentication(identity))).andExpect(status().isOk());
        mvc.perform(get("/actuator/isolation-public-probe")).andExpect(status().isOk());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mvc.perform(get(path).secure(true).with(authentication(identity)))
                    .andExpect(content().string(a.id().toString())));
            var secondIdentity = auth(new Scopes(b.id(), null, Set.of()), "ROLE_TEACHER");
            var second = executor.submit(() -> mvc.perform(get(path).secure(true).with(authentication(secondIdentity)))
                    .andExpect(content().string(b.id().toString())));
            first.get(); second.get();
        }
    }
}
