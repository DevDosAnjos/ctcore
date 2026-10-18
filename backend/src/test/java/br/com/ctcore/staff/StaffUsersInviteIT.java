package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-ACE-06: convidar e reenviar, com o banco real. */
class StaffUsersInviteIT extends StaffBase {
    private int links(Tenant t, String email) {
        return adminJdbc().queryForObject("select count(*) from account_link l join account a on a.id = l.account_id "
                + "where l.organization_id = ? and a.email = ?", Integer.class, t.orgId(), email);
    }

    @Test
    @DisplayName("CA5: o Coordenador convida um Administrador: 201, vínculo PENDING nas unidades, convite emitido e auditoria sem e-mail nem código")
    void coordinatorInvites() throws Exception {
        var t = tenant();
        var email = "novo-" + UUID.randomUUID() + "@synthetic.example";
        var response = call(invite(inviteBody(email, "Novo Admin", t.unitId())), coordinator(t));
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        var row = body(response);
        assertThat(row.path("status").asText()).isEqualTo("PENDING");
        assertThat(row.path("role").asText()).isEqualTo("ADMINISTRATOR");
        assertThat(row.path("units").get(0).path("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite where link_id = ?::uuid and canceled_at is null and used_at is null",
                Integer.class, row.path("linkId").asText())).isEqualTo(1);
        var audit = audit(t, "invite.created");
        assertThat(audit).hasSize(1);
        assertThat(audit.toString()).doesNotContain(email).doesNotContainIgnoringCase("code_hash");
    }

    @Test
    @DisplayName("CA6: o Administrador só convida Professor em unidade dele; role do corpo é ignorado; unidade de outra organização é 400 para o Coordenador")
    void reachAndImplicitRole() throws Exception {
        var t = tenant();
        var other = addUnit(t, "Outra", true);
        var admin = member(t, Role.ADMINISTRATOR, "Ana Admin", "ACTIVE", t.unitId());
        var email = "prof-" + UUID.randomUUID() + "@synthetic.example";
        assertThat(call(invite(inviteBody(email, "Prof", other)), admin).getStatus()).isEqualTo(403);
        assertThat(links(t, email)).isZero();
        var withRole = "{\"role\":\"COORDINATOR\",\"email\":\"" + email + "\",\"name\":\"Prof\",\"unitIds\":[\"" + t.unitId() + "\"]}";
        var response = call(invite(withRole), admin);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(body(response).path("role").asText()).isEqualTo("TEACHER");
        var coordinator = coordinator(t);
        var foreign = tenant();
        assertThat(call(invite(inviteBody("x-" + email, "Prof", foreign.unitId())), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(invite(inviteBody("not-an-email", "Prof", t.unitId())), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(invite(inviteBody("y-" + email, " ", t.unitId())), coordinator).getStatus()).isEqualTo(400);
        assertThat(call(invite("{\"email\":\"z-" + email + "\",\"name\":\"Prof\",\"unitIds\":[]}"), coordinator).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA7: e-mail de outra organização cria só o vínculo; mesmo perfil já ligado é 409 ALREADY_LINKED; conta desativada é 403")
    void existingAccounts() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        var elsewhere = tenant();
        var known = member(elsewhere, Role.ADMINISTRATOR, "Conhecida", "ACTIVE", elsewhere.unitId());
        assertThat(call(invite(inviteBody(known.email(), "Conhecida", t.unitId())), coordinator).getStatus()).isEqualTo(201);
        assertThat(adminJdbc().queryForObject("select count(*) from account where email = ?", Integer.class, known.email())).isEqualTo(1);
        var again = call(invite(inviteBody(known.email(), "Conhecida", t.unitId())), coordinator);
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(body(again).path("code").asText()).isEqualTo("ALREADY_LINKED");
        var gone = member(elsewhere, Role.ADMINISTRATOR, "Desativada", "ACTIVE", elsewhere.unitId());
        adminJdbc().update("update account set status = 'DEACTIVATED' where id = ?", gone.account());
        assertThat(call(invite(inviteBody(gone.email(), "Desativada", t.unitId())), coordinator).getStatus()).isEqualTo(403);
        assertThat(links(t, gone.email())).isZero();
    }

    @Test
    @DisplayName("CA8: com 199 vínculos de gestão o convite funciona; com 200 é 409 STAFF_LIMIT_REACHED e nada é criado")
    void staffLimit() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        adminJdbc().update("with a as (insert into account (id, email, name, status) select gen_random_uuid(), 'f' || g || '-' || ?::text || '@synthetic.example', "
                + "'F' || g, 'ACTIVE' from generate_series(1, 198) g returning id) "
                + "insert into account_link (account_id, organization_id, role, status) select id, ?, 'TEACHER', 'ACTIVE' from a", t.orgId().toString(), t.orgId());
        assertThat(call(invite(inviteBody("a-" + t.orgId() + "@synthetic.example", "A", t.unitId())), coordinator).getStatus()).isEqualTo(201);
        var email = "b-" + t.orgId() + "@synthetic.example";
        var full = call(invite(inviteBody(email, "B", t.unitId())), coordinator);
        assertThat(full.getStatus()).isEqualTo(409);
        assertThat(body(full).path("code").asText()).isEqualTo("STAFF_LIMIT_REACHED");
        assertThat(links(t, email)).isZero();
    }

    @Test
    @DisplayName("CA9: reenviar cancela o convite anterior e audita; o quarto no dia é 429; ACTIVE é 404")
    void resendRules() throws Exception {
        var t = tenant();
        var coordinator = coordinator(t);
        var email = "p-" + t.orgId() + "@synthetic.example";
        var link = UUID.fromString(body(call(invite(inviteBody(email, "Pendente", t.unitId())), coordinator)).path("linkId").asText());
        for (int i = 0; i < 3; i++) assertThat(call(resend(link), coordinator).getStatus()).as("resend " + i).isEqualTo(204);
        assertThat(call(resend(link), coordinator).getStatus()).isEqualTo(429);
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite where link_id = ? and canceled_at is null", Integer.class, link)).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from account_invite where link_id = ? and canceled_at is not null", Integer.class, link)).isEqualTo(3);
        assertThat(audit(t, "invite.resent")).hasSize(3);
        var active = member(t, Role.ADMINISTRATOR, "Ativa", "ACTIVE", t.unitId());
        assertThat(call(resend(active.link()), coordinator).getStatus()).isEqualTo(404);
    }
}
