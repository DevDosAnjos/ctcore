package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * RNF-31 nas quatro rotas. O molde RouteIsolationModelIT parte de ids na URL (leitura de B por A igual a "não existe");
 * aqui não há id (D1), então as mesmas garantias são afirmadas pelo vínculo ativo: A só enxerga e altera A, mesmo forjando B.
 */
class OrganizationRouteIT extends OrganizationBase {
    private List<MockHttpServletRequestBuilder> writes() throws Exception {
        return List.of(patch("{\"name\":\"Hacked\",\"color\":\"#1a56db\",\"holderDocument\":\"52998224725\"}"),
                putLogo(LogoImageTest.image("png", 512, 512), "x.png"), deleteLogo());
    }

    @Test
    @DisplayName("CA2: anônimo 401; sem vínculo, Professor e portal 403 em todas as rotas, sem efeito")
    void anonymousAndDeniedProfiles() throws Exception {
        var a = tenant();
        var before = row(a);
        assertThat(call(get(), null).getResponse().getStatus()).isEqualTo(401);
        for (var w : writes()) assertThat(call(w, null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(get(), who(a, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
            for (var w : writes()) assertThat(call(w, who(a, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        var noLink = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of());
        assertThat(call(get(), noLink).getResponse().getStatus()).isEqualTo(403);
        assertThat(row(a)).isEqualTo(before);
    }

    @Test
    @DisplayName("CA4: Administrador lê (200) mas recebe 403 em PATCH, PUT e DELETE do logo, e nada muda")
    void administratorReadsOnly() throws Exception {
        var a = tenant();
        var before = row(a);
        assertThat(call(get(), who(a, Role.ADMINISTRATOR)).getResponse().getStatus()).isEqualTo(200);
        for (var w : writes()) {
            var response = call(w, who(a, Role.ADMINISTRATOR)).getResponse();
            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("FORBIDDEN");
        }
        assertThat(row(a)).isEqualTo(before);
    }

    @Test
    @DisplayName("CA14: organizationId forjado em cabeçalho, parâmetro e corpo é ignorado; B permanece idêntica")
    void forgedOrganizationIsIgnored() throws Exception {
        var a = tenant();
        var b = tenant();
        var beforeB = row(b);
        var coordinator = who(a, Role.COORDINATOR);
        var forged = patch("{\"name\":\"Only A\",\"organizationId\":\"" + b.orgId() + "\",\"id\":\"" + b.orgId() + "\"}")
                .header("X-Organization-Id", b.orgId().toString()).param("organizationId", b.orgId().toString());
        assertThat(call(forged, coordinator).getResponse().getStatus()).isEqualTo(200);
        assertThat(row(b)).isEqualTo(beforeB);
        var read = call(get().header("X-Organization-Id", b.orgId().toString()).param("organizationId", b.orgId().toString()), coordinator).getResponse();
        assertThat(read.getContentAsString()).contains("Only A").doesNotContain("Synthetic " + b.orgId());
        assertThat(adminJdbc().queryForObject("select name from organization where id = ?", String.class, a.orgId())).isEqualTo("Only A");
        assertThat(call(get().header("X-Organization-Id", a.orgId().toString()), who(b, Role.COORDINATOR)).getResponse().getContentAsString())
                .contains("Synthetic " + b.orgId());
        // identificador de organização na URL não é rota: 404, igual a qualquer caminho inexistente
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/organization/" + b.orgId()), coordinator).getResponse().getStatus()).isEqualTo(404);
    }
}
