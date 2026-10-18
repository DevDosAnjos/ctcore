package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-ORG-07 / D7: o Coordenador nomeia a si mesmo Administrador, com o banco real. */
class StaffUsersAppointmentIT extends StaffBase {
    @Test
    @DisplayName("CA13: nomear a si mesmo destrava a desativação, é idempotente e audita; Administrador é 403; unidade inativa ou de outra organização é 400")
    void appointment() throws Exception {
        var t = tenant();
        var off = addUnit(t, "Off", false);
        var coordinator = coordinator(t);
        var only = member(t, Role.ADMINISTRATOR, "Unica", "ACTIVE", t.unitId());
        assertThat(call(deactivate(only.link()), coordinator).getStatus()).isEqualTo(409);

        var admin = member(t, Role.ADMINISTRATOR, "Outra", "ACTIVE", t.unitId());
        assertThat(call(appoint(t.unitId()), admin).getStatus()).isEqualTo(403);
        assertThat(call(appoint(off), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(appoint(tenant().unitId()), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(appoint(), coordinator).getStatus()).isEqualTo(400);
        adminJdbc().update("update account_link set status = 'ENDED' where id = ?", admin.link());

        var response = call(appoint(t.unitId()), coordinator);
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        assertThat(body(response).path("status").asText()).isEqualTo("ACTIVE");
        assertThat(call(appoint(t.unitId()), coordinator).getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select count(*) from account_link where account_id = ? and organization_id = ? and role = 'ADMINISTRATOR'",
                Integer.class, coordinator.account(), t.orgId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from account_link_unit lu join account_link l on l.id = lu.link_id "
                + "where l.account_id = ? and lu.unit_id = ?", Integer.class, coordinator.account(), t.unitId())).isEqualTo(1);
        assertThat(audit(t, "link.coordinator_appointed")).hasSize(2);

        assertThat(call(deactivate(only.link()), coordinator).getStatus()).isEqualTo(204);
    }
}
