package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Codes;
import br.com.ctcore.identity.Role;
import br.com.ctcore.identity.SessionCookie;
import br.com.ctcore.identity.TwoFactorService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ORG-06: transferência da coordenação pela rota, com o banco real. */
class CoordinationTransferIT extends OrganizationBase {
    @Autowired TwoFactorService twoFactor;

    /** Conta com vínculo ativo, sessão real e um token de reautenticação de TRANSFER_COORDINATION. */
    private class Member {
        final UUID account = UUID.randomUUID();
        final UUID session = UUID.randomUUID();
        final UUID link = UUID.randomUUID();
        final String email = account + "@synthetic.example";
        final String sessionToken = "session-" + account;
        final String reauth = "reauth-" + account;
        final Role role;
        final Tenant tenant;

        Member(Tenant tenant, Role role, String linkStatus) {
            this.tenant = tenant;
            this.role = role;
            adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Synthetic', 'ACTIVE')", account, email);
            adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, ?)",
                    link, account, tenant.orgId(), role.name(), linkStatus);
            adminJdbc().update("insert into account_session (id, account_id, token_hash, active_link_id, expires_at) "
                    + "values (?, ?, ?, ?, now() + interval '1 hour')", session, account, Codes.hash(sessionToken), link);
            token("TRANSFER_COORDINATION", reauth);
            if (role == Role.COORDINATOR) twoFactor();
        }

        void twoFactor() {
            adminJdbc().update("insert into account_two_factor (account_id, secret_encrypted, enabled_at) values (?, 'x', now())", account);
        }

        void token(String action, String value) {
            adminJdbc().update("insert into reauth_token (account_id, session_id, action, token_hash, expires_at) "
                    + "values (?, ?, ?, ?, now() + interval '5 minutes')", account, session, action, Codes.hash(value));
        }

        AccountPrincipal principal() {
            return new AccountPrincipal(account, session, link, tenant.orgId(), role, Set.of(tenant.unitId()));
        }

        String role() {
            return adminJdbc().queryForObject("select role from account_link where id = ?", String.class, link);
        }

        List<UUID> units() {
            return adminJdbc().queryForList("select unit_id from account_link_unit where link_id = ?", UUID.class, link);
        }
    }

    private static MockHttpServletRequestBuilder transfer(String email, String confirmation, String token) {
        var request = MockMvcRequestBuilders.post("/api/v1/organization/coordination-transfer").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"confirmation\":\"" + confirmation + "\"}");
        return token == null ? request : request.header("X-Reauth-Token", token);
    }

    private MockHttpServletResponse send(Member from, String email, String confirmation, String token) throws Exception {
        return call(transfer(email, confirmation, token), from.principal()).getResponse();
    }

    private int emails(String to) {
        return adminJdbc().queryForObject("select count(*) from email_message where template = 'COORDINATION_TRANSFERRED' and to_email = ?",
                Integer.class, to);
    }

    private int audits(Tenant t) {
        return audit(t).size();
    }

    private List<java.util.Map<String, Object>> audit(Tenant t) {
        return adminJdbc().queryForList("select actor_profile, before_data::text as b, after_data::text as a from audit_log "
                + "where organization_id = ? and action = 'organization.coordination_transferred'", t.orgId());
    }

    /** Nada mudou: perfis, e-mail e auditoria. */
    private void untouched(Member coordinator, Member admin) {
        assertThat(coordinator.role()).isEqualTo("COORDINATOR");
        assertThat(admin.role()).isEqualTo("ADMINISTRATOR");
        assertThat(emails(admin.email)).isZero();
        assertThat(audits(coordinator.tenant)).isZero();
    }

    @Test
    @DisplayName("RN-ORG-06 / CA1 / CA6 / CA8 / CA11: troca os perfis, dá todas as unidades ao antigo, tira as do novo, envia o e-mail e audita sem segredo")
    void transfers() throws Exception {
        var t = tenant();
        adminJdbc().update("insert into unit (organization_id, name, short_name) values (?, 'Outra', ?)", t.orgId(), "outra-" + t.orgId());
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", b.link, t.orgId(), t.unitId());

        var response = send(a, "  " + b.email.toUpperCase() + " ", "  transferir ", a.reauth);

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(204);
        assertThat(a.role()).isEqualTo("ADMINISTRATOR");
        assertThat(b.role()).isEqualTo("COORDINATOR");
        assertThat(a.units()).hasSize(2);
        assertThat(b.units()).isEmpty();
        assertThat(emails(b.email)).isEqualTo(1);
        var rows = audit(t);
        assertThat(rows).hasSize(1);
        String trail = rows.get(0).toString();
        assertThat(trail).contains(a.account.toString(), b.account.toString()).doesNotContain(a.reauth, b.email, "password");
    }

    @Test
    @DisplayName("CA2: Administrador, Professor e conta do portal recebem 403 e nada muda")
    void profiles() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        for (Role role : List.of(Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT)) {
            var other = new Member(t, role, "ACTIVE");
            assertThat(send(other, b.email, "TRANSFERIR", other.reauth).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(call(transfer(b.email, "TRANSFERIR", a.reauth), null).getResponse().getStatus()).isEqualTo(401);
        untouched(a, b);
    }

    @Test
    @DisplayName("CA3: sem token, de outra ação, de outra sessão, vencido ou usado dá REAUTH_REQUIRED; o token serve uma vez")
    void reauth() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        a.token("FULL_EXPORT", "other-action");
        adminJdbc().update("insert into reauth_token (account_id, session_id, action, token_hash, expires_at) values (?, ?, 'TRANSFER_COORDINATION', ?, now() - interval '1 minute')",
                a.account, a.session, Codes.hash("expired"));
        for (String token : new String[] {null, "other-action", "expired", "unknown", b.reauth}) {
            var response = send(a, b.email, "TRANSFERIR", token);
            assertThat(response.getStatus()).as(String.valueOf(token)).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("REAUTH_REQUIRED");
        }
        untouched(a, b);
        assertThat(send(a, b.email, "TRANSFERIR", a.reauth).getStatus()).isEqualTo(204);
        // o antigo Coordenador agora é Administrador: o mesmo token usado não serve nem a ele nem ao novo
        assertThat(send(b, a.email, "TRANSFERIR", a.reauth).getStatus()).isEqualTo(403);
        assertThat(b.role()).isEqualTo("COORDINATOR");
    }

    @Test
    @DisplayName("CA4: confirmação em branco ou diferente de TRANSFERIR dá erro em confirmation e nada muda")
    void confirmation() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        for (String word : new String[] {"", "   ", "transfere", "TRANSFERIR já"}) {
            var response = send(a, b.email, word, a.reauth);
            assertThat(response.getStatus()).as(word).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("\"field\":\"confirmation\"");
        }
        untouched(a, b);
        // a recusa não gasta o token
        assertThat(send(a, b.email, "TRANSFERIR", a.reauth).getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("CA5 / CA12: e-mail inexistente, de outra organização, de Professor, pendente, encerrado ou o próprio dá erro em email e nada muda")
    void target() throws Exception {
        var t = tenant();
        var other = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        var outsider = new Member(other, Role.ADMINISTRATOR, "ACTIVE");
        var otherCoordinator = new Member(other, Role.COORDINATOR, "ACTIVE");
        String[] emails = {"ninguem@synthetic.example", outsider.email, new Member(t, Role.TEACHER, "ACTIVE").email,
                new Member(t, Role.ADMINISTRATOR, "PENDING").email, new Member(t, Role.ADMINISTRATOR, "ENDED").email, a.email, ""};
        for (String email : emails) {
            var response = send(a, email, "TRANSFERIR", a.reauth);
            assertThat(response.getStatus()).as(email).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("\"field\":\"email\"");
        }
        untouched(a, b);
        assertThat(send(otherCoordinator, b.email, "TRANSFERIR", otherCoordinator.reauth).getStatus()).isEqualTo(400);
        assertThat(outsider.role()).isEqualTo("ADMINISTRATOR");
        assertThat(otherCoordinator.role()).isEqualTo("COORDINATOR");
        assertThat(audits(other)).isZero();
    }

    @Test
    @DisplayName("RN-ORG-06 / CA6: falha no meio da troca desfaz tudo e não envia e-mail")
    void atomic() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        // o último passo da troca (dar as unidades ao antigo Coordenador) falha: os perfis já trocados voltam
        adminJdbc().execute("create or replace function atomic_probe_fail() returns trigger language plpgsql as $$ begin raise exception 'probe'; end $$");
        adminJdbc().execute("create trigger atomic_probe before insert on account_link_unit for each row when (new.organization_id = '" + t.orgId()
                + "') execute function atomic_probe_fail()");
        try {
            assertThat(send(a, b.email, "TRANSFERIR", a.reauth).getStatus()).isGreaterThanOrEqualTo(500);
        } finally {
            adminJdbc().execute("drop trigger atomic_probe on account_link_unit");
            adminJdbc().execute("drop function atomic_probe_fail()");
        }
        untouched(a, b);
    }

    @Test
    @DisplayName("RN-ORG-06 / RN-ORG-07: Coordenador que se nomeou Administrador transfere a coordenação sem erro e fica com um só vínculo ativo, de Administrador, com todas as unidades")
    void selfAppointedCoordinatorTransfers() throws Exception {
        var t = tenant();
        adminJdbc().update("insert into unit (organization_id, name, short_name) values (?, 'Outra', ?)", t.orgId(), "outra-" + t.orgId());
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", b.link, t.orgId(), t.unitId());
        var appointment = call(MockMvcRequestBuilders.post("/api/v1/users/coordinator-appointment").contentType("application/json")
                .content("{\"unitIds\":[\"" + t.unitId() + "\"]}"), a.principal()).getResponse();
        assertThat(appointment.getStatus()).as(appointment.getContentAsString()).isEqualTo(200);

        var response = send(a, b.email, "TRANSFERIR", a.reauth);

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(204);
        assertThat(roles(a.account)).containsExactlyInAnyOrder("ADMINISTRATOR:ACTIVE", "COORDINATOR:ENDED");
        assertThat(roles(b.account)).containsExactly("COORDINATOR:ACTIVE");
        assertThat(adminJdbc().queryForList("select lu.unit_id from account_link_unit lu join account_link l on l.id = lu.link_id "
                + "where l.account_id = ? and l.status = 'ACTIVE'", UUID.class, a.account)).hasSize(2);
        assertThat(adminJdbc().queryForObject("select count(*) from account_link where organization_id = ? and role = 'COORDINATOR' and status = 'ACTIVE'",
                Integer.class, t.orgId())).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select active_link_id from account_session where id = ?", UUID.class, a.session))
                .isEqualTo(adminJdbc().queryForObject("select id from account_link where account_id = ? and role = 'ADMINISTRATOR'", UUID.class, a.account));
    }

    @Test
    @DisplayName("RN-ORG-06: o novo Coordenador que já teve uma linha de Coordenador reaproveita essa linha e o vínculo de Administrador dele é encerrado")
    void targetWithOldCoordinatorRow() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", b.link, t.orgId(), t.unitId());
        adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, 'COORDINATOR', 'ENDED')", b.account, t.orgId());

        var response = send(a, b.email, "TRANSFERIR", a.reauth);

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(204);
        assertThat(roles(b.account)).containsExactlyInAnyOrder("COORDINATOR:ACTIVE", "ADMINISTRATOR:ENDED");
        assertThat(b.units()).isEmpty();
        assertThat(roles(a.account)).containsExactly("ADMINISTRATOR:ACTIVE");
        assertThat(adminJdbc().queryForObject("select active_link_id from account_session where id = ?", UUID.class, b.session))
                .isEqualTo(adminJdbc().queryForObject("select id from account_link where account_id = ? and role = 'COORDINATOR'", UUID.class, b.account));
    }

    private List<String> roles(UUID account) {
        return adminJdbc().queryForList("select role || ':' || status from account_link where account_id = ?", String.class, account);
    }

    @Test
    @DisplayName("CA7: o banco recusa o segundo Coordenador ativo na mesma organização e aceita em outra")
    void singleCoordinator() {
        var t = tenant();
        var other = tenant();
        new Member(t, Role.COORDINATOR, "ACTIVE");
        new Member(other, Role.COORDINATOR, "ACTIVE");
        assertThatThrownBy(() -> new Member(t, Role.COORDINATOR, "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
        new Member(t, Role.COORDINATOR, "ENDED");
    }

    @Test
    @DisplayName("CA9 / CA10: sessões já abertas valem o novo perfil na hora e o novo Coordenador sem duas etapas é barrado")
    void immediate() throws Exception {
        var t = tenant();
        var a = new Member(t, Role.COORDINATOR, "ACTIVE");
        var b = new Member(t, Role.ADMINISTRATOR, "ACTIVE");
        assertThat(twoFactor.setupRequired(b.account)).isFalse();
        assertThat(send(a, b.email, "TRANSFERIR", a.reauth).getStatus()).isEqualTo(204);

        assertThat(twoFactor.setupRequired(b.account)).isTrue();
        assertThat(withSession(get(), b).getStatus()).isEqualTo(403);
        b.twoFactor();
        assertThat(withSession(get(), a).getStatus()).isEqualTo(200);
        assertThat(withSession(patch("{\"name\":\"Novo nome\"}"), a).getStatus()).isEqualTo(403);
        assertThat(withSession(get(), b).getStatus()).isEqualTo(200);
        assertThat(withSession(patch("{\"name\":\"Novo nome\"}").header("If-Match", "0"), b).getStatus()).isEqualTo(200);
        assertThat(twoFactor.setupRequired(a.account)).isFalse();
    }

    private MockHttpServletResponse withSession(MockHttpServletRequestBuilder request, Member who) throws Exception {
        request.secure(true).cookie(new Cookie(SessionCookie.NAME, who.sessionToken))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf());
        return mvc.perform(request).andReturn().getResponse();
    }
}
