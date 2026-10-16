package br.com.ctcore.isolation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.organization.Organizations;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Molde de teste de isolamento e permissao (RNF-05, RNF-31). Para copiar para uma rota nova,
 * veja "Teste-modelo de isolamento" em backend/README.md: estenda esta classe, importe o controller
 * da rota com @Import e implemente apenas os pontos de extensao abaixo.
 */
@AutoConfigureMockMvc
public abstract class RouteIsolationModelIT extends PostgresIT {
    /** Uma organizacao de teste com sua unidade. */
    public record Tenant(UUID orgId, UUID unitId) {}

    @Autowired protected Organizations organizations;
    @Autowired protected MockMvc mvc;

    // ---- pontos de extensao ----
    /** Leitura do recurso do alvo (ids do alvo na URL). */
    protected abstract MockHttpServletRequestBuilder read(Tenant target);
    /** Leitura de um recurso que nao existe (ids aleatorios): o 404 de referencia. */
    protected abstract MockHttpServletRequestBuilder readMissing();
    /** Alteracao do recurso do alvo. */
    protected abstract MockHttpServletRequestBuilder write(Tenant target);
    /** Papeis (ROLE_...) que podem alterar. */
    protected abstract Set<String> allowedRoles();
    /** Papeis que nao podem alterar. */
    protected abstract Set<String> deniedRoles();
    /** Estado do recurso do alvo lido como administrador, fora da RLS. */
    protected abstract Object state(Tenant target);
    /** Texto proprio do alvo que nunca pode aparecer na resposta a outra organizacao. */
    protected String secret(Tenant target) { return target.unitId().toString(); }

    // ---- molde ----
    protected Tenant tenant() {
        UUID id = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(id, "Synthetic", UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        return new Tenant(input.id(), input.unitId());
    }

    protected Authentication login(Tenant tenant, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new Scopes(tenant.orgId(), tenant.unitId(), Set.of(tenant.unitId())), "synthetic",
                AuthorityUtils.createAuthorityList(role));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, Authentication who, Tenant forged) throws Exception {
        request.secure(true).with(csrf());
        if (who != null) request.with(authentication(who));
        if (forged != null) request.header("X-Organization-Id", forged.orgId()).param("organizationId", forged.orgId().toString());
        return mvc.perform(request);
    }

    private int statusOf(MockHttpServletRequestBuilder request, Authentication who, Tenant forged) throws Exception {
        return call(request, who, forged).andReturn().getResponse().getStatus();
    }

    private String anyRole() { return allowedRoles().iterator().next(); }

    @Test
    @DisplayName("RNF-31: user of A cannot read B, even with guessed ids, and the answer equals a missing id")
    void crossRead() throws Exception {
        var a = tenant(); var b = tenant();
        int missing = statusOf(readMissing(), login(a, anyRole()), null);
        assertThat(missing).isEqualTo(404);
        for (String role : allowedRoles()) {
            var response = call(read(b), login(a, role), null).andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(missing);
            assertThat(response.getContentAsString()).doesNotContain(secret(b));
        }
    }

    @Test
    @DisplayName("RNF-31: user of A cannot change B and B stays untouched")
    void crossWrite() throws Exception {
        var a = tenant(); var b = tenant();
        var before = state(b);
        for (String role : allowedRoles()) {
            assertThat(statusOf(write(b), login(a, role), null)).isIn(403, 404);
            assertThat(state(b)).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("RNF-05 / RNF-31: anonymous gets 401 and a user without organization scope gets 403, with no effect")
    void anonymousAndUnscoped() throws Exception {
        var b = tenant();
        var before = state(b);
        call(read(b), null, null).andExpect(status().isUnauthorized());
        call(write(b), null, null).andExpect(status().isUnauthorized());
        var unscoped = UsernamePasswordAuthenticationToken.authenticated("unscoped", "synthetic",
                AuthorityUtils.createAuthorityList(anyRole()));
        call(read(b), unscoped, null).andExpect(status().isForbidden());
        call(write(b), unscoped, null).andExpect(status().isForbidden());
        assertThat(state(b)).isEqualTo(before);
    }

    @Test
    @DisplayName("RNF-05: denied profile gets 403 and nothing is written, enforced by the server")
    void deniedProfile() throws Exception {
        var a = tenant();
        var before = state(a);
        for (String role : deniedRoles()) {
            call(write(a), login(a, role), null).andExpect(status().isForbidden());
            assertThat(state(a)).isEqualTo(before);
        }
    }

    @Test
    @DisplayName("RNF-05 / RNF-31: allowed profile succeeds only on its own organization")
    void allowedProfile() throws Exception {
        var a = tenant(); var b = tenant();
        var beforeA = state(a);
        var beforeB = state(b);
        for (String role : allowedRoles()) {
            assertThat(statusOf(write(a), login(a, role), null)).isBetween(200, 299);
        }
        assertThat(state(a)).isNotEqualTo(beforeA);
        assertThat(state(b)).isEqualTo(beforeB);
    }

    @Test
    @DisplayName("RNF-05 / RNF-31: forged X-Organization-Id and organizationId do not change the result or grant access")
    void forgedOrganization() throws Exception {
        var a = tenant(); var b = tenant();
        var beforeB = state(b);
        for (String role : deniedRoles()) {
            call(write(a), login(a, role), b).andExpect(status().isForbidden());
            call(write(b), login(a, role), b).andExpect(status().isForbidden());
        }
        for (String role : allowedRoles()) {
            assertThat(statusOf(write(b), login(a, role), b)).isIn(403, 404);
            assertThat(statusOf(read(a), login(a, role), null)).isEqualTo(200);
            assertThat(statusOf(read(a), login(a, role), b)).isEqualTo(200);
        }
        assertThat(state(b)).isEqualTo(beforeB);
    }
}
