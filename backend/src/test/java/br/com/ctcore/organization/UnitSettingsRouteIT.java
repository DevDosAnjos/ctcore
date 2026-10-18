package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RNF-31 em GET/PUT /units/{id} e GET /units/mine: perfis, anônimo e unidade de outra organização. */
class UnitSettingsRouteIT extends UnitBase {
    private static String clean(String json) {
        return json.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
    }

    @Test
    @DisplayName("CA2 / CA5: anônimo 401; Professor, Aluno, Responsável, sem vínculo e Administrador de outra unidade 403 em GET, PUT e mine, sem efeito")
    void denied() throws Exception {
        var a = tenant();
        UUID other = addUnit(a, "Outra", "outra-" + a.orgId(), null);
        String before = adminJdbc().queryForList("select * from unit where id = ?", a.unitId()).toString();
        assertThat(call(read(a.unitId()), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(put(a.unitId(), payload()), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(call(mine(), null).getResponse().getStatus()).isEqualTo(401);
        var noLink = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of());
        for (AccountPrincipal who : new AccountPrincipal[] {who(a, Role.TEACHER), who(a, Role.STUDENT), who(a, Role.GUARDIAN), noLink, admin(a, other)}) {
            assertThat(call(read(a.unitId()), who).getResponse().getStatus()).as(String.valueOf(who.role())).isEqualTo(403);
            var response = call(put(a.unitId(), payload()), who).getResponse();
            assertThat(response.getStatus()).as(String.valueOf(who.role())).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("FORBIDDEN");
        }
        for (AccountPrincipal who : new AccountPrincipal[] {who(a, Role.TEACHER), who(a, Role.STUDENT), who(a, Role.GUARDIAN), noLink}) {
            assertThat(call(mine(), who).getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(adminJdbc().queryForList("select * from unit where id = ?", a.unitId()).toString()).isEqualTo(before);
        assertThat(audit(a, "unit.update")).isEmpty();
    }

    @Test
    @DisplayName("CA5: Administrador altera a própria unidade (200) e lê a sua")
    void administratorOwnUnit() throws Exception {
        var a = tenant();
        var admin = admin(a, a.unitId());
        assertThat(call(read(a.unitId()), admin).getResponse().getStatus()).isEqualTo(200);
        var response = call(put(a.unitId(), payload()), admin).getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        assertThat(audit(a, "unit.update")).hasSize(1);
        assertThat(audit(a, "unit.update").get(0).get("actor_profile")).isEqualTo("ADMINISTRATOR");
    }

    @Test
    @DisplayName("CA16: usuário de A com o id de unidade de B recebe 404 igual a id inexistente, em GET e PUT, e B permanece idêntica")
    void crossOrganization() throws Exception {
        var a = tenant();
        var b = tenant();
        String beforeB = adminJdbc().queryForList("select u.*, s.* from unit u join unit_settings s on s.unit_id = u.id where u.id = ?", b.unitId()).toString();
        for (AccountPrincipal who : new AccountPrincipal[] {who(a, Role.COORDINATOR), admin(a, a.unitId())}) {
            var foreign = call(read(b.unitId()).header("X-Organization-Id", b.orgId().toString()).param("organizationId", b.orgId().toString()), who)
                    .getResponse();
            var missing = call(read(UUID.randomUUID()), who).getResponse();
            assertThat(foreign.getStatus()).isEqualTo(404);
            assertThat(missing.getStatus()).isEqualTo(404);
            assertThat(clean(foreign.getContentAsString()).replaceAll("[0-9a-f-]{36}", "X")).isEqualTo(clean(missing.getContentAsString()).replaceAll("[0-9a-f-]{36}", "X"));
            assertThat(call(put(b.unitId(), payload()), who).getResponse().getStatus()).isEqualTo(404);
            assertThat(call(put(UUID.randomUUID(), payload()), who).getResponse().getStatus()).isEqualTo(404);
        }
        var mineOfA = call(mine(), who(a, Role.COORDINATOR)).getResponse().getContentAsString();
        assertThat(mineOfA).contains(a.unitId().toString()).doesNotContain(b.unitId().toString());
        assertThat(adminJdbc().queryForList("select u.*, s.* from unit u join unit_settings s on s.unit_id = u.id where u.id = ?", b.unitId()).toString())
                .isEqualTo(beforeB);
        assertThat(audit(b, "unit.update")).isEmpty();
    }
}
