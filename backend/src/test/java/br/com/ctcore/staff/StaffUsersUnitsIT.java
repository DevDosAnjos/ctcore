package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-ACE-06 (alterar unidades) e RN-ORG-07, com o banco real. */
class StaffUsersUnitsIT extends StaffBase {
    @Test
    @DisplayName("CA10: o Coordenador troca a lista inteira, a auditoria guarda antes e depois; vazio, outra organização e unidade inativa são 400")
    void coordinatorReplacesTheList() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var c = addUnit(t, "C", true);
        var off = addUnit(t, "Off", false);
        var coordinator = coordinator(t);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId(), b);
        member(t, Role.ADMINISTRATOR, "Outra Admin", "ACTIVE", t.unitId(), b, c);
        var response = call(units(admin.link(), t.unitId(), c), coordinator);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        assertThat(unitsOf(admin.link())).containsExactlyInAnyOrder(t.unitId(), c);
        assertThat(body(response).path("units")).hasSize(2);
        var audit = audit(t, "link.units_changed");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).get("b").toString()).contains(b.toString());
        assertThat(audit.get(0).get("a").toString()).contains(c.toString());
        assertThat(call(units(admin.link()), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(units(admin.link(), tenant().unitId()), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(units(admin.link(), off), coordinator).getStatus()).isEqualTo(400);
        assertThat(unitsOf(admin.link())).containsExactlyInAnyOrder(t.unitId(), c);
    }

    @Test
    @DisplayName("CA11: o Administrador mexe só nas unidades dele e as outras do Professor ficam; ficar sem unidade é 400; unidade fora do alcance é 403")
    void administratorKeepsTheOthers() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var d = addUnit(t, "D", true);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId(), d);
        var teacher = member(t, Role.TEACHER, "Prof", "ACTIVE", t.unitId(), b);
        assertThat(call(units(teacher.link(), t.unitId(), d), admin).getStatus()).isEqualTo(200);
        assertThat(unitsOf(teacher.link())).containsExactlyInAnyOrder(t.unitId(), b, d);
        assertThat(call(units(teacher.link(), d), admin).getStatus()).isEqualTo(200);
        assertThat(unitsOf(teacher.link())).containsExactlyInAnyOrder(b, d);
        var solo = member(t, Role.TEACHER, "Solo", "ACTIVE", t.unitId());
        assertThat(call(units(solo.link()), admin).getStatus()).isEqualTo(400);
        assertThat(call(units(solo.link(), b), admin).getStatus()).isEqualTo(403);
        assertThat(unitsOf(solo.link())).containsExactly(t.unitId());
    }

    @Test
    @DisplayName("CA12: tirar a unidade do último Administrador ativo é 409 LAST_UNIT_ADMINISTRATOR e nada muda; segundo ativo libera, segundo pendente não")
    void lastAdministrator() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var coordinator = coordinator(t);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId(), b);
        var pending = member(t, Role.ADMINISTRATOR, "Pendente", "PENDING", t.unitId());
        var refused = call(units(admin.link(), b), coordinator);
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(body(refused).path("code").asText()).isEqualTo("LAST_UNIT_ADMINISTRATOR");
        assertThat(body(refused).path("fields").get(0).path("message").asText()).isEqualTo(t.unitId().toString());
        assertThat(unitsOf(admin.link())).containsExactlyInAnyOrder(t.unitId(), b);
        adminJdbc().update("update account_link set status = 'ACTIVE' where id = ?", pending.link());
        assertThat(call(units(admin.link(), b), coordinator).getStatus()).isEqualTo(200);
        assertThat(unitsOf(admin.link())).containsExactly(b);
    }
}
