package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Codes;
import br.com.ctcore.identity.Role;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ORG-07: desativação de unidade pela rota, com o banco real e a porta de impedimentos substituída. */
class UnitDeactivationIT extends UnitBase {
    @MockitoBean UnitDeactivationBlockers blockers;

    /** Conta com vínculo ativo, sessão real e um token de reautenticação de DEACTIVATE_UNIT. */
    private class Member {
        final UUID account = UUID.randomUUID();
        final UUID session = UUID.randomUUID();
        final UUID link = UUID.randomUUID();
        final String reauth = "reauth-" + account;
        final Role role;
        final Tenant tenant;

        Member(Tenant tenant, Role role) {
            this.tenant = tenant;
            this.role = role;
            adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Synthetic', 'ACTIVE')", account, account + "@synthetic.example");
            adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')",
                    link, account, tenant.orgId(), role.name());
            adminJdbc().update("insert into account_session (id, account_id, token_hash, active_link_id, expires_at) "
                    + "values (?, ?, ?, ?, now() + interval '1 hour')", session, account, Codes.hash("session-" + account), link);
            token("DEACTIVATE_UNIT", reauth);
            if (role == Role.COORDINATOR) {
                adminJdbc().update("insert into account_two_factor (account_id, secret_encrypted, enabled_at) values (?, 'x', now())", account);
            }
        }

        void token(String action, String value) {
            adminJdbc().update("insert into reauth_token (account_id, session_id, action, token_hash, expires_at) "
                    + "values (?, ?, ?, ?, now() + interval '5 minutes')", account, session, action, Codes.hash(value));
        }

        AccountPrincipal principal() {
            return new AccountPrincipal(account, session, link, tenant.orgId(), role, Set.of(tenant.unitId()));
        }
    }

    private static MockHttpServletRequestBuilder deactivate(UUID unit, String confirmation, String token) {
        var request = MockMvcRequestBuilders.post("/api/v1/units/" + unit + "/deactivation").contentType("application/json")
                .content("{\"confirmation\":" + (confirmation == null ? "null" : "\"" + confirmation + "\"") + "}");
        return token == null ? request : request.header("X-Reauth-Token", token);
    }

    private static MockHttpServletRequestBuilder check(UUID unit) {
        return MockMvcRequestBuilders.get("/api/v1/units/" + unit + "/deactivation-check");
    }

    private MockHttpServletResponse send(Member who, UUID unit, String confirmation, String token) throws Exception {
        return call(deactivate(unit, confirmation, token), who.principal()).getResponse();
    }

    private boolean active(UUID unit) {
        return adminJdbc().queryForObject("select active from unit where id = ?", Boolean.class, unit);
    }

    private int audits(Tenant t) {
        return audit(t, "unit.deactivated").size();
    }

    private int unusedTokens(Member m) {
        return adminJdbc().queryForObject("select count(*) from reauth_token where account_id = ? and used_at is null", Integer.class, m.account);
    }

    /** Organização com duas unidades ativas: a da frente (alvo) e uma segunda. */
    private record Setup(Tenant tenant, UUID target, Member coordinator) {}

    private Setup setup() {
        var t = tenant();
        addUnit(t, "Outra", "outra-" + t.orgId(), "Niterói");
        return new Setup(t, t.unitId(), new Member(t, Role.COORDINATOR));
    }

    @Test
    @DisplayName("CA1 / CA5 / CA9: desativa com token e nome em outra caixa, mantém o histórico, tira de reachable e audita sem segredo")
    void deactivates() throws Exception {
        var s = setup();
        var response = send(s.coordinator(), s.target(), "  uNiT ", s.coordinator().reauth);

        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(204);
        assertThat(active(s.target())).isFalse();
        assertThat(call(read(s.target()), s.coordinator().principal()).getResponse().getContentAsString()).contains("\"active\":false");
        var listed = body(call(list(), s.coordinator().principal()).getResponse());
        assertThat(listed).hasSize(2);
        var reachable = call(MockMvcRequestBuilders.get("/api/v1/units/reachable"), s.coordinator().principal()).getResponse().getContentAsString();
        assertThat(reachable).doesNotContain(s.target().toString());
        var rows = audit(s.tenant(), "unit.deactivated");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("entity_id")).isEqualTo(s.target().toString());
        assertThat(rows.get(0).toString()).contains("\"active\": true").contains("\"active\": false").doesNotContain(s.coordinator().reauth, "password");
        // segunda chamada: já desativada
        s.coordinator().token("DEACTIVATE_UNIT", "again" + s.coordinator().account);
        assertThat(send(s.coordinator(), s.target(), "Unit", "again" + s.coordinator().account).getStatus()).isEqualTo(409);
        assertThat(audits(s.tenant())).isEqualTo(1);
    }

    @Test
    @DisplayName("CA2: Administrador, Professor e conta do portal recebem 403 nas duas rotas e nada muda")
    void profiles() throws Exception {
        var s = setup();
        for (Role role : List.of(Role.ADMINISTRATOR, Role.TEACHER, Role.STUDENT)) {
            var other = new Member(s.tenant(), role);
            assertThat(send(other, s.target(), "Unit", other.reauth).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(call(check(s.target()), other.principal()).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(call(deactivate(s.target(), "Unit", s.coordinator().reauth), null).getResponse().getStatus()).isEqualTo(401);
        assertThat(active(s.target())).isTrue();
        assertThat(audits(s.tenant())).isZero();
    }

    @Test
    @DisplayName("CA3 / CA4: com alunos e turmas ativos responde UNIT_HAS_BLOCKERS com as contagens, sem gastar o token; o check devolve o mesmo")
    void blocked() throws Exception {
        var s = setup();
        when(blockers.activeStudents(any())).thenReturn(2);
        when(blockers.activeClasses(any())).thenReturn(1);

        var response = send(s.coordinator(), s.target(), "Unit", s.coordinator().reauth);
        assertThat(response.getStatus()).isEqualTo(409);
        String error = response.getContentAsString();
        assertThat(error).contains("UNIT_HAS_BLOCKERS").contains("{\"field\":\"activeStudents\",\"message\":\"2\"}")
                .contains("{\"field\":\"activeClasses\",\"message\":\"1\"}").contains("{\"field\":\"lastActiveUnit\",\"message\":\"false\"}");
        assertThat(active(s.target())).isTrue();
        assertThat(unusedTokens(s.coordinator())).isEqualTo(1);
        assertThat(audits(s.tenant())).isZero();

        var check = body(call(check(s.target()), s.coordinator().principal()).getResponse());
        assertThat(check.path("activeStudents").asInt()).isEqualTo(2);
        assertThat(check.path("activeClasses").asInt()).isEqualTo(1);
        assertThat(check.path("canDeactivate").asBoolean()).isFalse();

        when(blockers.activeStudents(any())).thenReturn(0);
        when(blockers.activeClasses(any())).thenReturn(0);
        var free = body(call(check(s.target()), s.coordinator().principal()).getResponse());
        assertThat(free.path("canDeactivate").asBoolean()).isTrue();
        assertThat(free.path("lastActiveUnit").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA5: confirmação em branco ou diferente dá erro em confirmation e nada muda")
    void confirmation() throws Exception {
        var s = setup();
        for (String word : new String[] {"", "   ", "Outra", "Unit extra", null}) {
            var response = send(s.coordinator(), s.target(), word, s.coordinator().reauth);
            assertThat(response.getStatus()).as(String.valueOf(word)).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("\"field\":\"confirmation\"");
        }
        assertThat(active(s.target())).isTrue();
        assertThat(unusedTokens(s.coordinator())).isEqualTo(1);
        assertThat(audits(s.tenant())).isZero();
    }

    @Test
    @DisplayName("CA6: sem token, de outra ação, de outra sessão, vencido ou usado dá REAUTH_REQUIRED; o token serve uma vez")
    void reauth() throws Exception {
        var s = setup();
        var c = s.coordinator();
        var stranger = new Member(tenant(), Role.COORDINATOR);
        c.token("FULL_EXPORT", "other-action" + c.account);
        adminJdbc().update("insert into reauth_token (account_id, session_id, action, token_hash, expires_at) values (?, ?, 'DEACTIVATE_UNIT', ?, now() - interval '1 minute')",
                c.account, c.session, Codes.hash("expired" + c.account));
        for (String token : new String[] {null, "other-action" + c.account, "expired" + c.account, "unknown", stranger.reauth}) {
            var response = send(c, s.target(), "Unit", token);
            assertThat(response.getStatus()).as(String.valueOf(token)).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("REAUTH_REQUIRED");
        }
        assertThat(active(s.target())).isTrue();
        assertThat(audits(s.tenant())).isZero();
        assertThat(send(c, s.target(), "Unit", c.reauth).getStatus()).isEqualTo(204);
        // o mesmo token não serve de novo, nem em outra unidade
        var third = addUnit(s.tenant(), "Terceira", "terceira-" + s.tenant().orgId(), "Rio");
        assertThat(send(c, third, "Terceira", c.reauth).getStatus()).isEqualTo(403);
        assertThat(active(third)).isTrue();
    }

    @Test
    @DisplayName("CA7: a única unidade ativa não desativa (lastActiveUnit) e desativadas não contam como outras")
    void lastUnit() throws Exception {
        var t = tenant();
        var c = new Member(t, Role.COORDINATOR);
        var response = send(c, t.unitId(), "Unit", c.reauth);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getContentAsString()).contains("UNIT_HAS_BLOCKERS").contains("{\"field\":\"lastActiveUnit\",\"message\":\"true\"}");
        assertThat(body(call(check(t.unitId()), c.principal()).getResponse()).path("lastActiveUnit").asBoolean()).isTrue();
        assertThat(active(t.unitId())).isTrue();
        assertThat(unusedTokens(c)).isEqualTo(1);

        var second = addUnit(t, "Segunda", "segunda-" + t.orgId(), "Rio");
        assertThat(send(c, second, "Segunda", c.reauth).getStatus()).isEqualTo(204);
        c.token("DEACTIVATE_UNIT", "next" + c.account);
        assertThat(send(c, t.unitId(), "Unit", "next" + c.account).getStatus()).isEqualTo(409);
        assertThat(active(t.unitId())).isTrue();
    }

    @Test
    @DisplayName("CA8: unidade de outra organização ou inexistente responde 404 nas duas rotas, e a outra segue ativa")
    void isolation() throws Exception {
        var s = setup();
        var other = tenant();
        addUnit(other, "Outra", "outra2-" + other.orgId(), "Rio");
        for (UUID id : new UUID[] {other.unitId(), UUID.randomUUID()}) {
            assertThat(send(s.coordinator(), id, "Unit", s.coordinator().reauth).getStatus()).isEqualTo(404);
            assertThat(call(check(id), s.coordinator().principal()).getResponse().getStatus()).isEqualTo(404);
        }
        assertThat(active(other.unitId())).isTrue();
        assertThat(unusedTokens(s.coordinator())).isEqualTo(1);
        assertThat(audits(other)).isZero();
    }
}
