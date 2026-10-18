package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Codes;
import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-ACE-06 (desativar), RN-ORG-07 e RN-ACE-20, com o banco real. */
class StaffUsersDeactivateIT extends StaffBase {
    private int sessions(UUID account) {
        return adminJdbc().queryForObject("select count(*) from account_session where account_id = ?", Integer.class, account);
    }

    private void session(Member who) {
        adminJdbc().update("insert into account_session (id, account_id, token_hash, active_link_id, expires_at) values (?, ?, ?, ?, now() + interval '1 hour')",
                UUID.randomUUID(), who.account(), Codes.hash("s-" + who.account()), who.link());
    }

    @Test
    @DisplayName("CA12: desativar o último Administrador ativo da unidade é 409 e nada muda; com outro ativo funciona, com outro pendente não")
    void lastAdministrator() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        var only = member(t, Role.ADMINISTRATOR, "Unica", "ACTIVE", t.unitId());
        var pending = member(t, Role.ADMINISTRATOR, "Pendente", "PENDING", t.unitId());
        var refused = call(deactivate(only.link()), coordinator);
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(body(refused).path("code").asText()).isEqualTo("LAST_UNIT_ADMINISTRATOR");
        assertThat(status(only.link())).isEqualTo("ACTIVE");
        adminJdbc().update("update account_link set status = 'ACTIVE' where id = ?", pending.link());
        assertThat(call(deactivate(only.link()), coordinator).getStatus()).isEqualTo(204);
        assertThat(status(only.link())).isEqualTo("ENDED");
    }

    @Test
    @DisplayName("CA14: desativar encerra o vínculo, audita e, sendo o último de gestão, acaba com as sessões; vínculo em outra organização fica")
    void endsLinkAndSessions() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        member(t, Role.ADMINISTRATOR, "Fica", "ACTIVE", t.unitId());
        var leaving = member(t, Role.ADMINISTRATOR, "Sai", "ACTIVE", t.unitId());
        session(leaving);
        var elsewhere = tenant();
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, 'STUDENT', 'ACTIVE')", leaving.account(), elsewhere.orgId());
        assertThat(call(deactivate(leaving.link()), coordinator).getStatus()).isEqualTo(204);
        assertThat(status(leaving.link())).isEqualTo("ENDED");
        assertThat(audit(t, "link.ended")).hasSize(1);
        assertThat(sessions(leaving.account())).isZero();
        assertThat(adminJdbc().queryForObject("select status from account where id = ?", String.class, leaving.account())).isEqualTo("ACTIVE");
        assertThat(adminJdbc().queryForObject("select status from account_link where organization_id = ? and account_id = ?", String.class,
                elsewhere.orgId(), leaving.account())).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("CA14: com outro vínculo de gestão ativo, as sessões ficam")
    void keepsSessionsWithAnotherManagementLink() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        member(t, Role.ADMINISTRATOR, "Fica", "ACTIVE", t.unitId());
        var leaving = member(t, Role.ADMINISTRATOR, "Sai", "ACTIVE", t.unitId());
        var elsewhere = tenant();
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, 'ADMINISTRATOR', 'ACTIVE')", leaving.account(), elsewhere.orgId());
        session(leaving);
        assertThat(call(deactivate(leaving.link()), coordinator).getStatus()).isEqualTo(204);
        assertThat(sessions(leaving.account())).isEqualTo(1);
    }

    @Test
    @DisplayName("CA15: desativar convite pendente cancela o convite, encerra o vínculo e audita invite.canceled")
    void pendingInviteIsCanceled() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        var email = "p-" + t.orgId() + "@synthetic.example";
        var link = UUID.fromString(body(call(invite(inviteBody(email, "Pendente", t.unitId())), coordinator)).path("linkId").asText());
        assertThat(call(deactivate(link), coordinator).getStatus()).isEqualTo(204);
        assertThat(status(link)).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite where link_id = ? and canceled_at is null", Integer.class, link)).isZero();
        assertThat(audit(t, "invite.canceled")).hasSize(1);
    }

    @Test
    @DisplayName("CA16: o Administrador não desativa Professor com unidade fora do alcance (403); o de unidade só dele sim; a si mesmo é 409; o Coordenador é 403")
    void administratorScope() throws Exception {
        var t = tenant();
        var b = addUnit(t, "B", true);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId());
        var two = member(t, Role.TEACHER, "Dupla", "ACTIVE", t.unitId(), b);
        var solo = member(t, Role.TEACHER, "Solo", "ACTIVE", t.unitId());
        assertThat(call(deactivate(two.link()), admin).getStatus()).isEqualTo(403);
        assertThat(status(two.link())).isEqualTo("ACTIVE");
        assertThat(call(deactivate(solo.link()), admin).getStatus()).isEqualTo(204);
        assertThat(status(solo.link())).isEqualTo("ENDED");
        assertThat(call(deactivate(admin.link()), admin).getStatus()).isEqualTo(409);
        var coordinator = coordinator(t);
        assertThat(call(deactivate(coordinator.link()), admin).getStatus()).isEqualTo(403);
        assertThat(call(deactivate(coordinator.link()), coordinator).getStatus()).isEqualTo(409);
        assertThat(status(coordinator.link())).isEqualTo("ACTIVE");
    }
}
