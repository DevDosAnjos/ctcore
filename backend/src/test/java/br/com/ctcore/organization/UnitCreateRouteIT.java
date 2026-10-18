package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RNF-31 em POST /units: perfis sem permissão, anônimo e organização forjada no corpo, sem efeito algum. */
class UnitCreateRouteIT extends UnitBase {
    private static final String BODY = "{\"name\":\"Nova\",\"administrator\":{\"name\":\"Ana\",\"email\":\"ana@synthetic.example\"}}";

    @Test
    @DisplayName("CA2: anônimo 401; Administrador, Professor, Aluno, Responsável e sem vínculo 403, sem unidade, convite nem auditoria")
    void deniedProfiles() throws Exception {
        var a = tenant();
        assertThat(call(create(BODY), null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var response = call(create(BODY), who(a, role)).getResponse();
            assertThat(response.getStatus()).as(role.name()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("FORBIDDEN");
        }
        var noLink = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of());
        assertThat(call(create(BODY), noLink).getResponse().getStatus()).isEqualTo(403);
        assertThat(unitCount(a)).isEqualTo(1);
        assertThat(audit(a, "unit.create")).isEmpty();
        assertThat(adminJdbc().queryForObject("select count(*) from account where email = 'ana@synthetic.example'", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CA3: Coordenador de A com organização de B no corpo, no cabeçalho e na consulta cria só em A; B não muda")
    void crossOrganization() throws Exception {
        var a = tenant();
        var b = tenant();
        var response = call(create("{\"name\":\"Nova\",\"organizationId\":\"" + b.orgId() + "\"}").header("X-Organization-Id", b.orgId().toString())
                .param("organizationId", b.orgId().toString()), who(a, Role.COORDINATOR)).getResponse();
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(unitCount(a)).isEqualTo(2);
        assertThat(unitCount(b)).isEqualTo(1);
        assertThat(audit(b, "unit.create")).isEmpty();
        assertThat(audit(a, "unit.create")).hasSize(1);
    }
}
