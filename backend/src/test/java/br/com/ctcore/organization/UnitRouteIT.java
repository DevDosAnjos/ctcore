package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RNF-31 nas duas rotas: perfis sem permissão, anônimo e unidade de outra organização por id adivinhado. */
class UnitRouteIT extends UnitBase {
    @Test
    @DisplayName("CA4 / CA5: anônimo 401; Administrador, Professor, Aluno, Responsável e sem vínculo 403 em GET e PATCH, sem efeito")
    void deniedProfiles() throws Exception {
        var a = tenant();
        var before = unitRow(a.unitId());
        assertThat(call(list(), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(adjust(a.unitId(), "x-" + a.orgId()), null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(list(), who(a, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
            var response = call(adjust(a.unitId(), "x-" + a.orgId()), who(a, role)).getResponse();
            assertThat(response.getStatus()).as(role.name()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("FORBIDDEN");
        }
        var noLink = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of());
        assertThat(call(list(), noLink).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(adjust(a.unitId(), "x-" + a.orgId()), noLink).getResponse().getStatus()).isEqualTo(403);
        assertThat(unitRow(a.unitId())).isEqualTo(before);
    }

    @Test
    @DisplayName("CA6: a lista de A não traz B (nem com organização forjada) e o ajuste de unidade de B por A dá 404 igual a id inexistente")
    void crossOrganization() throws Exception {
        var a = tenant();
        var b = tenant();
        var beforeB = unitRow(b.unitId());
        var coordinator = who(a, Role.COORDINATOR);
        var list = call(list().header("X-Organization-Id", b.orgId().toString()).param("organizationId", b.orgId().toString()), coordinator).getResponse();
        assertThat(list.getStatus()).isEqualTo(200);
        assertThat(list.getContentAsString()).contains(a.unitId().toString()).doesNotContain(b.unitId().toString());
        var foreign = call(adjust(b.unitId(), "invadido-" + b.orgId()), coordinator).getResponse();
        var missing = call(adjust(UUID.randomUUID(), "invadido-" + b.orgId()), coordinator).getResponse();
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\"", ""))
                .isEqualTo(missing.getContentAsString().replaceAll("\"traceId\":\"[^\"]*\"", ""));
        assertThat(unitRow(b.unitId())).isEqualTo(beforeB);
        assertThat(audit(b, "unit.short_name.update")).isEmpty();
    }
}
