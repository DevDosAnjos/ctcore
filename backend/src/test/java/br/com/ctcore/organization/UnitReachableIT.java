package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ORG-05: GET /units/reachable por perfil e por organização, com o banco real. */
class UnitReachableIT extends UnitBase {
    private AccountPrincipal as(Tenant t, Role role, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
    }

    private com.fasterxml.jackson.databind.JsonNode reach(AccountPrincipal who) throws Exception {
        var response = call(MockMvcRequestBuilders.get("/api/v1/units/reachable"), who).getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return body(response);
    }

    @Test
    @DisplayName("CA1 / CA2 / CA3: Coordenador vê as ativas e allUnits; Administrador e Professor só as suas; outra organização não aparece")
    void reachable() throws Exception {
        var a = tenant();
        var b = tenant();
        UUID second = addUnit(a, "Segunda", "segunda-" + a.orgId(), null);
        UUID third = addUnit(a, "Terceira", "terceira-" + a.orgId(), null);
        UUID off = addUnit(a, "Inativa", "inativa-" + a.orgId(), null);
        adminJdbc().update("update unit set active = false where id = ?", off);

        var all = reach(as(a, Role.COORDINATOR));
        assertThat(all.path("allUnits").asBoolean()).isTrue();
        assertThat(all.path("units")).hasSize(3);
        assertThat(all.toString()).doesNotContain(off.toString()).doesNotContain(b.unitId().toString());
        assertThat(all.path("units").get(0).fieldNames().next()).isNotNull();

        var admin = reach(as(a, Role.ADMINISTRATOR, a.unitId(), second));
        assertThat(admin.path("allUnits").asBoolean()).isFalse();
        assertThat(admin.path("units")).hasSize(2);
        assertThat(admin.toString()).doesNotContain(third.toString());

        var teacher = reach(as(a, Role.TEACHER, third, off));
        assertThat(teacher.path("allUnits").asBoolean()).isFalse();
        assertThat(teacher.path("units")).hasSize(1);
        assertThat(teacher.path("units").get(0).path("id").asText()).isEqualTo(third.toString());

        var other = call(MockMvcRequestBuilders.get("/api/v1/units/reachable").param("organizationId", a.orgId().toString()),
                as(b, Role.COORDINATOR)).getResponse();
        assertThat(other.getContentAsString()).contains(b.unitId().toString()).doesNotContain(a.unitId().toString());
    }

    @Test
    @DisplayName("CA4: anônimo 401; Aluno, Responsável e sem vínculo 403")
    void denied() throws Exception {
        var a = tenant();
        var get = MockMvcRequestBuilders.get("/api/v1/units/reachable");
        assertThat(call(get, null).getResponse().getStatus()).isEqualTo(401);
        var noLink = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of());
        for (AccountPrincipal who : new AccountPrincipal[] {who(a, Role.STUDENT), who(a, Role.GUARDIAN), noLink}) {
            assertThat(call(get, who).getResponse().getStatus()).isEqualTo(403);
        }
    }
}
