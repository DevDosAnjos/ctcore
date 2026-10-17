package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.isolation.Scopes;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

/** CA16, adapted from the #15 model (D6): the routes of /auth belong to an account, not to an organization resource. */
@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class AuthIsolationIT extends AuthBase {
    @Autowired InviteService invites;
    @Autowired OrganizationTransactions orgTx;
    @Autowired AccountTransactions accountTx;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("CA16: a conta de A só vê os próprios vínculos, o contexto vem do vínculo (não de parâmetro) e vínculo de B dá 404")
    void sessionsAndLinksStaySeparate() throws Exception {
        UUID orgA = org(), orgB = org();
        UUID adminA = account("admin-a@x.com"), studentA = account("student-a@x.com"), adminB = account("admin-b@x.com");
        UUID linkAdminA = link(adminA, orgA, "ADMINISTRATOR", "ACTIVE");
        link(studentA, orgA, "STUDENT", "ACTIVE");
        UUID linkAdminB = link(adminB, orgB, "ADMINISTRATOR", "ACTIVE");

        Cookie a = sessionOf("admin-a@x.com"), b = sessionOf("admin-b@x.com"), s = sessionOf("student-a@x.com");
        MvcResult view = get("/api/v1/auth/session", a).andReturn();
        assertThat(view.getResponse().getContentAsString()).contains(linkAdminA.toString(), orgA.toString())
                .doesNotContain(linkAdminB.toString(), orgB.toString(), "admin-b@x.com", "student-a@x.com");

        for (Cookie cookie : List.of(a, s)) {
            MvcResult probe = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/probe-org")
                    .secure(true).cookie(cookie).header("X-Organization-Id", orgB.toString()).param("organizationId", orgB.toString())).andReturn();
            assertThat(body(probe).path("organizationId").asText()).isEqualTo(orgA.toString());
        }
        assertThat(body(get("/api/v1/probe-org", b).andReturn()).path("organizationId").asText()).isEqualTo(orgB.toString());

        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", linkAdminB), a).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(body(get("/api/v1/auth/session", a).andReturn()).path("activeLinkId").asText()).isEqualTo(linkAdminA.toString());
        assertThat(get("/api/v1/probe-org", null).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA8: cada perfil com dois vínculos escolhe o próprio e passa a ter o papel dele; vínculo de outro perfil de outra conta dá 404")
    void everyRoleChoosesOwnLink() throws Exception {
        UUID orgA = org("Alfa"), orgB = org("Beta"), orgC = org("Gama");
        UUID stranger = link(account("stranger@x.com"), orgC, "ADMINISTRATOR", "ACTIVE");
        int n = 0;
        for (String role : List.of("COORDINATOR", "ADMINISTRATOR", "TEACHER", "STUDENT", "GUARDIAN")) {
            String email = "r" + n++ + "@x.com";
            UUID acc = account(email);
            link(acc, orgB, role.equals("STUDENT") ? "GUARDIAN" : "STUDENT", "ACTIVE");
            UUID own = link(acc, orgA, role, "ACTIVE");
            Cookie c = sessionOf(email);
            assertThat(put("/api/v1/auth/session/link", Map.of("linkId", stranger), c).andReturn().getResponse().getStatus()).isEqualTo(404);
            MvcResult r = put("/api/v1/auth/session/link", Map.of("linkId", own), c).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(r).path("activeLinkId").asText()).isEqualTo(own.toString());
            assertThat(body(get("/api/v1/probe-org", c).andReturn()).path("organizationId").asText()).isEqualTo(orgA.toString());
            assertThat(body(r).path("links").findValuesAsText("role")).contains(role);
        }
    }

    @Test
    @DisplayName("CA16: sessão encerrada de uma conta não alcança a outra; a troca de senha de A não toca a sessão de B")
    void passwordChangeDoesNotTouchOthers() throws Exception {
        UUID orgA = org(), orgB = org();
        link(account("a@x.com"), orgA, "ADMINISTRATOR", "ACTIVE");
        link(account("b@x.com"), orgB, "ADMINISTRATOR", "ACTIVE");
        Cookie a = sessionOf("a@x.com"), a2 = sessionOf("a@x.com"), b = sessionOf("b@x.com");

        post("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "Novasenha1"), a);
        assertThat(get("/api/v1/auth/session", a2).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(get("/api/v1/auth/session", b).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(login("b@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA16 / RLS: com o escopo de B, vínculos e convites de A não aparecem; com o escopo da conta, só os dela")
    void rowLevelSecurity() {
        UUID orgA = org(), orgB = org();
        UUID unitA = unit(orgA);
        UUID adminA = account("a@x.com"), adminB = account("b@x.com");
        link(adminA, orgA, "ADMINISTRATOR", "ACTIVE");
        link(adminB, orgB, "ADMINISTRATOR", "ACTIVE");
        var issued = invites.invite(new Inviter(adminA, Role.COORDINATOR, Set.of()), orgA, "novo@x.com", "Novo", Role.ADMINISTRATOR, Set.of(), null);

        try (var ignored = Scopes.open(orgB, null)) {
            assertThat(orgTx.execute(() -> jdbc.sql("select count(*) from account_link where organization_id = :o").param("o", orgA)
                    .query(Long.class).single())).isZero();
            assertThat(orgTx.execute(() -> jdbc.sql("select count(*) from account_invite").query(Long.class).single())).isZero();
            assertThat(orgTx.execute(() -> jdbc.sql("select count(*) from account_link").query(Long.class).single())).isEqualTo(1);
        }
        try (var ignored = Scopes.open(orgA, unitA)) {
            assertThat(orgTx.execute(() -> jdbc.sql("select count(*) from account_invite where id = :i").param("i", issued.inviteId())
                    .query(Long.class).single())).isEqualTo(1);
        }
        assertThat(accountTx.forAccount(adminB, () -> jdbc.sql("select count(*) from account_link where organization_id = :o")
                .param("o", orgA).query(Long.class).single())).isZero();
        assertThat(accountTx.forAccount(adminB, () -> jdbc.sql("select count(*) from account_invite").query(Long.class).single())).isZero();
        assertThat(accountTx.forInvite(Codes.hash(issued.code()), () -> jdbc.sql("select count(*) from account_invite")
                .query(Long.class).single())).isEqualTo(1);
    }

    @Test
    @DisplayName("CA13, CA14: a recuperação de A não toca a conta, as sessões nem a auditoria de B, o link de A não vale para B, e qualquer perfil logado vê o mesmo")
    void passwordResetStaysWithTheAccount() throws Exception {
        UUID orgA = org(), orgB = org();
        UUID a = account("a@x.com"), b = account("b@x.com");
        link(a, orgA, "ADMINISTRATOR", "ACTIVE");
        link(b, orgB, "ADMINISTRATOR", "ACTIVE");
        Cookie sessionB = sessionOf("b@x.com");
        String hashB = admin.queryForObject("select password_hash from account where id = ?", String.class, b);

        post("/api/v1/auth/password-reset", Map.of("email", "a@x.com", "challengeToken", "t"), null);
        post("/api/v1/auth/password-reset", Map.of("email", "b@x.com", "challengeToken", "t"), null);
        String codeA = resetCode(outbox.list().get(0).content());
        String codeB = resetCode(outbox.list().get(1).content());

        // each role calls the public routes with its own session and gets the same answer as someone without one
        String anon = body(post("/api/v1/auth/password-reset", Map.of("email", "ghost@x.com", "challengeToken", "t"), null).andReturn()).toString();
        int n = 0;
        for (String role : List.of("COORDINATOR", "ADMINISTRATOR", "TEACHER", "STUDENT", "GUARDIAN")) {
            String email = "p" + n++ + "@x.com";
            link(account(email), orgA, role, "ACTIVE");
            Cookie c = sessionOf(email);
            MvcResult r = post("/api/v1/auth/password-reset", Map.of("email", "ghost@x.com", "challengeToken", "t"), c).andReturn();
            assertThat(body(r).toString()).isEqualTo(anon).doesNotContain("a@x.com", "b@x.com", orgA.toString(), orgB.toString());
            assertThat(shape(post("/api/v1/auth/password-reset/nao-existe", Map.of("password", "Novasenha1"), c).andReturn()))
                    .startsWith("410|RESET_LINK_INVALID").doesNotContain("a@x.com", "b@x.com", orgA.toString(), orgB.toString());
        }

        MvcResult done = post("/api/v1/auth/password-reset/" + codeA, Map.of("password", "Novasenha1"), null).andReturn();
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        assertThat(done.getResponse().getContentAsString()).doesNotContain("b@x.com", orgB.toString());
        assertThat(admin.queryForObject("select password_hash from account where id = ?", String.class, b)).isEqualTo(hashB);
        assertThat(get("/api/v1/auth/session", sessionB).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(login("b@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.password_reset'", orgA)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.password_reset'", orgB)).isZero();
        assertThat(count("select count(*) from password_reset where account_id = ? and used_at is null and canceled_at is null", b)).isEqualTo(1);
        // B's own link still works for B after A's was used
        assertThat(post("/api/v1/auth/password-reset/" + codeB, Map.of("password", "Outrasenha1"), null).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private static String resetCode(String mail) {
        String tail = mail.substring(mail.indexOf("redefinir-senha/") + "redefinir-senha/".length());
        return tail.lines().findFirst().orElseThrow().trim().split(" ")[0];
    }

    @Test
    @DisplayName("CA5: o login de A não altera o último acesso de B")
    void lastAccessStaysWithTheAccount() throws Exception {
        UUID org = org();
        UUID a = account("a@x.com"), b = account("b@x.com");
        link(a, org, "ADMINISTRATOR", "ACTIVE");
        link(b, org, "ADMINISTRATOR", "ACTIVE");
        sessionOf("a@x.com");
        assertThat(count("select count(*) from account where id = ? and last_access_at is not null", a)).isEqualTo(1);
        assertThat(count("select count(*) from account where id = ? and last_access_at is null", b)).isEqualTo(1);
    }
}
