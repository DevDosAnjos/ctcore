package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-ACE-06: a lista da equipe, com o banco real. */
class StaffUsersListIT extends StaffBase {
    @Test
    @DisplayName("CA1: o Coordenador vê só os Administradores, com unidades, último acesso, situação e canManage")
    void coordinatorSeesAdministrators() throws Exception {
        var t = tenant();
        var second = addUnit(t, "Zeta", true);
        var coordinator = coordinator(t);
        var ana = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId(), second);
        member(t, Role.ADMINISTRATOR, "Bruno Admin", "PENDING", t.unitId());
        member(t, Role.TEACHER, "Paulo Prof", "ACTIVE", t.unitId());
        adminJdbc().update("update account set last_access_at = now() where id = ?", ana.account());
        var other = tenant();
        member(other, Role.ADMINISTRATOR, "Intruso Admin", "ACTIVE", other.unitId());

        var response = call(list(""), coordinator);
        assertThat(response.getStatus()).isEqualTo(200);
        var rows = body(response);
        assertThat(rows).hasSize(2);
        assertThat(names(rows)).containsExactly("Ana Admin", "Bruno Admin");
        var first = rows.get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder("linkId", "name", "email", "role", "units", "lastAccessAt",
                "status", "canManage");
        assertThat(first.path("units").findValuesAsText("name")).containsExactly("Unit", "Zeta");
        assertThat(first.path("lastAccessAt").isNull()).isFalse();
        assertThat(first.path("status").asText()).isEqualTo("ACTIVE");
        assertThat(first.path("canManage").asBoolean()).isTrue();
        assertThat(rows.get(1).path("lastAccessAt").isNull()).isTrue();
        assertThat(rows.get(1).path("status").asText()).isEqualTo("PENDING");
        assertThat(response.getContentAsString().toLowerCase()).doesNotContain("hash", "token", "password");
    }

    @Test
    @DisplayName("CA2: o Administrador vê os Professores com ao menos uma das suas unidades, e canManage só se todas forem dele")
    void administratorSeesTeachers() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var c = addUnit(t, "C", true);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId());
        member(t, Role.TEACHER, "Solo", "ACTIVE", t.unitId());
        member(t, Role.TEACHER, "Dupla", "ACTIVE", t.unitId(), b);
        member(t, Role.TEACHER, "Longe", "ACTIVE", c);
        member(t, Role.ADMINISTRATOR, "Outro Admin", "ACTIVE", t.unitId());

        var rows = body(call(list(""), admin));
        assertThat(names(rows)).containsExactly("Dupla", "Solo");
        assertThat(rows.get(0).path("canManage").asBoolean()).isFalse();
        assertThat(rows.get(0).path("units")).hasSize(2);
        assertThat(rows.get(1).path("canManage").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("CA3: Professor, Aluno e Responsável recebem 403; sem sessão, 401")
    void otherProfilesAreDenied() throws Exception {
        var t = tenant();
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(list(""), member(t, role, "X " + role, "ACTIVE", t.unitId())).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(call(list(""), null).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA4: vínculo ENDED só aparece com ?status=ENDED; filtro inválido é 400")
    void endedNeedsTheFilter() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        member(t, Role.ADMINISTRATOR, "Ativa", "ACTIVE", t.unitId());
        member(t, Role.ADMINISTRATOR, "Saiu", "ENDED", t.unitId());
        assertThat(names(body(call(list(""), coordinator)))).containsExactly("Ativa");
        assertThat(names(body(call(list("?status=ENDED"), coordinator)))).containsExactly("Saiu");
        assertThat(call(list("?status=XYZ"), coordinator).getStatus()).isEqualTo(400);
    }
}
