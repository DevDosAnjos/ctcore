package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.integration.Outbox;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class AuthRoutesIT extends AuthBase {
    @Autowired InviteService invites;

    String codeIn(String marker) {
        List<Outbox.Message> mails = outbox.list();
        Matcher m = Pattern.compile(marker + "/(\\S+)").matcher(mails.get(mails.size() - 1).content());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    @Test
    @DisplayName("CA2: não existe rota que crie conta; login com e-mail desconhecido não cria conta")
    void noRegistration() throws Exception {
        long before = count("select count(*) from account");
        post("/api/v1/auth/register", Map.of("email", "x@x.com", "password", PASSWORD), null).andReturn();
        assertThat(post("/api/v1/auth/register", Map.of("email", "x@x.com"), null).andReturn().getResponse().getStatus()).isIn(401, 404);
        assertThat(get("/api/v1/auth/register", null).andReturn().getResponse().getStatus()).isIn(401, 404);
        assertThat(login("ghost@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from account")).isEqualTo(before);
    }

    @Test
    @DisplayName("CA13, CA15: cookie SESSION sem Max-Age, só o hash no banco, 12 horas para gestão, 30 dias para portal, logout")
    void sessionLifetime() throws Exception {
        UUID org = org();
        UUID mgr = account("mgr@x.com"), stu = account("stu@x.com");
        UUID mgrLink = link(mgr, org, "ADMINISTRATOR", "ACTIVE");
        link(stu, org, "STUDENT", "ACTIVE");

        MvcResult r = login("mgr@x.com", PASSWORD);
        String header = r.getResponse().getHeader("Set-Cookie");
        assertThat(header).startsWith("SESSION=").contains("HttpOnly", "Secure", "SameSite=Lax").doesNotContain("Max-Age", "Expires");
        assertThat(body(r).path("activeLinkId").asText()).isEqualTo(mgrLink.toString());
        Cookie cookie = r.getResponse().getCookie("SESSION");
        assertThat(count("select count(*) from account_session where token_hash = ?", cookie.getValue())).isZero();
        assertThat(count("select count(*) from account_session where token_hash = ?", Codes.hash(cookie.getValue()))).isEqualTo(1);

        assertThat(get("/api/v1/auth/session", null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(body(get("/api/v1/auth/session", null).andReturn()).path("code").asText()).isEqualTo("UNAUTHENTICATED");
        MvcResult view = get("/api/v1/auth/session", cookie).andReturn();
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(view).path("account").path("email").asText()).isEqualTo("mgr@x.com");
        assertThat(body(get("/api/v1/probe-org", cookie).andReturn()).path("organizationId").asText()).isEqualTo(org.toString());

        clock.now = clock.now.plus(Duration.ofHours(11)).plusSeconds(59 * 60);
        assertThat(get("/api/v1/auth/session", cookie).andReturn().getResponse().getStatus()).isEqualTo(200);
        clock.now = clock.now.plus(Duration.ofHours(12)).plusSeconds(60);
        assertThat(get("/api/v1/auth/session", cookie).andReturn().getResponse().getStatus()).isEqualTo(401);

        clock.now = java.time.Instant.parse("2026-10-18T12:00:00Z");
        Cookie portal = sessionOf("stu@x.com");
        clock.now = clock.now.plus(Duration.ofDays(29)).plus(Duration.ofHours(23));
        assertThat(get("/api/v1/auth/session", portal).andReturn().getResponse().getStatus()).isEqualTo(200);
        Cookie other = sessionOf("stu@x.com");
        clock.now = clock.now.plus(Duration.ofDays(30)).plusSeconds(60);
        assertThat(get("/api/v1/auth/session", other).andReturn().getResponse().getStatus()).isEqualTo(401);

        clock.now = java.time.Instant.parse("2026-10-20T12:00:00Z");
        Cookie out = sessionOf("mgr@x.com");
        MvcResult logout = post("/api/v1/auth/logout", null, out).andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("SESSION=", "Max-Age=0");
        assertThat(get("/api/v1/auth/session", out).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from audit_log where action like 'session%' or action like 'login%' or action like 'logout%'")).isZero();
    }

    @Test
    @DisplayName("CA15, CA16: com dois vínculos o ativo fica vazio e a rota de organização dá 403 até escolher; vínculo alheio dá 404")
    void linkChoice() throws Exception {
        UUID orgA = org(), orgB = org(), orgC = org();
        UUID multi = account("multi@x.com"), other = account("other@x.com");
        UUID linkA = link(multi, orgA, "ADMINISTRATOR", "ACTIVE");
        UUID linkB = link(multi, orgB, "STUDENT", "ACTIVE");
        UUID foreign = link(other, orgC, "ADMINISTRATOR", "ACTIVE");

        MvcResult r = login("multi@x.com", PASSWORD);
        assertThat(body(r).path("activeLinkId").isNull()).isTrue();
        assertThat(body(r).path("links")).hasSize(2);
        Cookie cookie = r.getResponse().getCookie("SESSION");
        assertThat(get("/api/v1/probe-org", cookie).andReturn().getResponse().getStatus()).isEqualTo(403);

        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", foreign), cookie).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", UUID.randomUUID()), cookie).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(get("/api/v1/probe-org", cookie).andReturn().getResponse().getStatus()).isEqualTo(403);

        MvcResult chosen = put("/api/v1/auth/session/link", Map.of("linkId", linkB), cookie).andReturn();
        assertThat(chosen.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(chosen).path("activeLinkId").asText()).isEqualTo(linkB.toString());
        assertThat(body(get("/api/v1/probe-org", cookie).andReturn()).path("organizationId").asText()).isEqualTo(orgB.toString());
        put("/api/v1/auth/session/link", Map.of("linkId", linkA), cookie).andReturn();
        assertThat(body(get("/api/v1/probe-org", cookie).andReturn()).path("organizationId").asText()).isEqualTo(orgA.toString());
    }

    @Test
    @DisplayName("CA6, CA7, CA9, CA14: nome da organização em login, sessão e escolha; vínculo encerrado dá 404; outra sessão intacta; sem auditoria")
    void linkChoiceNamesIsolationAndNoAudit() throws Exception {
        UUID orgA = org("Academia Alfa"), orgB = org("Academia Beta"), orgC = org("Academia Gama");
        UUID multi = account("multi@x.com"), other = account("other@x.com");
        UUID linkA = link(multi, orgA, "ADMINISTRATOR", "ACTIVE");
        UUID linkB = link(multi, orgB, "STUDENT", "ACTIVE");
        UUID ended = link(multi, orgC, "TEACHER", "ENDED");
        link(other, orgC, "ADMINISTRATOR", "ACTIVE");
        long audits = count("select count(*) from audit_log");

        MvcResult r = login("multi@x.com", PASSWORD);
        Cookie one = r.getResponse().getCookie("SESSION"), two = sessionOf("multi@x.com");
        for (com.fasterxml.jackson.databind.JsonNode view : List.of(body(r), body(get("/api/v1/auth/session", one).andReturn()))) {
            assertThat(view.path("links")).hasSize(2);
            for (com.fasterxml.jackson.databind.JsonNode l : view.path("links")) {
                assertThat(l.path("organizationName").asText()).isEqualTo(l.path("organizationId").asText().equals(orgA.toString()) ? "Academia Alfa" : "Academia Beta");
            }
            assertThat(view.toString()).doesNotContain("Academia Gama", orgC.toString(), "other@x.com");
        }

        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", ended), one).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", linkA), null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(body(get("/api/v1/auth/session", one).andReturn()).path("activeLinkId").isNull()).isTrue();

        MvcResult chosen = put("/api/v1/auth/session/link", Map.of("linkId", linkB), one).andReturn();
        assertThat(chosen.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(chosen).path("links").get(0).has("organizationName")).isTrue();
        assertThat(body(chosen).toString()).doesNotContain("Academia Gama");
        assertThat(body(get("/api/v1/auth/session", two).andReturn()).path("activeLinkId").isNull()).isTrue();
        assertThat(count("select count(*) from audit_log")).isEqualTo(audits);
    }

    @Test
    @DisplayName("CA10, CA11: senha errada, e-mail inexistente, convite não aceito e conta desativada dão resposta idêntica")
    void identicalLoginFailures() throws Exception {
        UUID org = org();
        UUID ok = account("ok@x.com"), off = account("off@x.com");
        link(ok, org, "ADMINISTRATOR", "ACTIVE");
        link(off, org, "ADMINISTRATOR", "ACTIVE");
        admin.update("update account set status = 'DEACTIVATED' where id = ?", off);
        admin.update("insert into account (email, status) values ('invited@x.com', 'INVITED')");

        String wrong = shape(login("ok@x.com", "Errada123"));
        assertThat(wrong).startsWith("401|INVALID_CREDENTIALS|E-mail ou senha incorretos.");
        assertThat(shape(login("ghost@x.com", "Errada123"))).isEqualTo(wrong);
        assertThat(shape(login("invited@x.com", PASSWORD))).isEqualTo(wrong);
        assertThat(shape(login("off@x.com", PASSWORD))).isEqualTo(wrong);
        assertThat(count("select count(*) from account_session")).isZero();
    }

    @Test
    @DisplayName("CA9: 5 erros travam por 15 minutos (também e-mail inexistente), IP limita, desafio recusado não conta, tudo registrado")
    void lockout() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        for (String email : new String[]{"ana@x.com", "ghost@x.com"}) {
            for (int i = 0; i < 5; i++) {
                assertThat(login(email, "Errada123").getResponse().getStatus()).isEqualTo(401);
            }
            assertThat(login(email, PASSWORD).getResponse().getStatus()).isEqualTo(429);
            assertThat(login(email, "Errada123").getResponse().getStatus()).isEqualTo(429);
        }
        clock.now = clock.now.plus(Duration.ofMinutes(15)).plusSeconds(1);
        assertThat(login("ana@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from login_attempt where result = 'LOCKED'")).isEqualTo(4);
        assertThat(count("select count(*) from login_attempt where result = 'SUCCESS'")).isEqualTo(1);

        Mockito.when(challenge.verify(Mockito.any())).thenReturn(false);
        MvcResult refused = login("bia@x.com", PASSWORD);
        assertThat(shape(refused)).startsWith("400|CHALLENGE_FAILED");
        Mockito.when(challenge.verify(Mockito.any())).thenReturn(true);
        assertThat(count("select count(*) from login_attempt where email = 'bia@x.com' and result = 'CHALLENGE_FAILED'")).isEqualTo(1);
        assertThat(count("select count(*) from login_attempt where email = 'bia@x.com' and result = 'WRONG_PASSWORD'")).isZero();

        clock.now = clock.now.plus(Duration.ofHours(1));
        for (int i = 0; i < 20; i++) {
            assertThat(login("ip" + i + "@x.com", "Errada123").getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(login("another@x.com", "Errada123").getResponse().getStatus()).isEqualTo(429);
        assertThat(count("select count(*) from login_attempt where result = 'IP_LIMITED'")).isEqualTo(1);
        assertThat(count("select count(*) from login_attempt where ip is not null")).isEqualTo(count("select count(*) from login_attempt"));
    }

    @Test
    @DisplayName("CA8, CA14, CA17: trocar a senha logado encerra a outra sessão, mantém esta, avisa por e-mail e audita sem a senha")
    void changePassword() throws Exception {
        UUID org = org();
        UUID ana = account("ana@x.com");
        link(ana, org, "ADMINISTRATOR", "ACTIVE");
        Cookie a = sessionOf("ana@x.com"), b = sessionOf("ana@x.com");

        assertThat(shape(post("/api/v1/auth/password", Map.of("currentPassword", "Errada123", "newPassword", "Novasenha1"), a).andReturn()))
                .startsWith("400|VALIDATION_ERROR");
        assertThat(shape(post("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "abcdefg1"), a).andReturn()))
                .startsWith("400|PASSWORD_WEAK");
        assertThat(shape(post("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "Password1"), a).andReturn()))
                .startsWith("400|PASSWORD_LEAKED|Essa senha é muito conhecida. Escolha outra.");
        assertThat(get("/api/v1/auth/session", b).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(post("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "Novasenha1"), a).andReturn()
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(get("/api/v1/auth/session", b).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(get("/api/v1/auth/session", a).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(login("ana@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        assertThat(login("ana@x.com", "Novasenha1").getResponse().getStatus()).isEqualTo(200);

        assertThat(admin.queryForObject("select password_hash from account where id = ?", String.class, ana)).startsWith("$argon2id$");
        assertThat(outbox.list()).hasSize(1);
        assertThat(outbox.list().get(0).recipient()).isEqualTo("ana@x.com");
        assertThat(outbox.list().get(0).content()).doesNotContain("Novasenha1", PASSWORD);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.password_changed'", org)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.sessions_ended'", org)).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and (after_data::text ~* '(senha|password|token|code)' "
                + "or before_data::text ~* '(senha|password|token|code)')", org)).isZero();
    }

    @Test
    @DisplayName("CA10, CA12, CA14: recuperação com a mesma resposta, novo pedido cancela o anterior, senha fraca não consome, uso único e sessão iniciada")
    void passwordReset() throws Exception {
        UUID org = org();
        UUID ana = account("ana@x.com"), off = account("off@x.com");
        link(ana, org, "ADMINISTRATOR", "ACTIVE");
        admin.update("update account set status = 'DEACTIVATED' where id = ?", off);

        String same = null;
        for (String email : new String[]{"ana@x.com", "ghost@x.com", "off@x.com"}) {
            MvcResult r = post("/api/v1/auth/password-reset", Map.of("email", email, "challengeToken", "t"), null).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            String shape = r.getResponse().getContentAsString();
            assertThat(shape).contains("Se esse e-mail tiver uma conta, você vai receber um link em instantes.");
            assertThat(same == null || same.equals(shape)).isTrue();
            same = shape;
        }
        assertThat(outbox.list()).hasSize(1);
        String first = codeIn("redefinir-senha");

        Cookie old = sessionOf("ana@x.com");
        post("/api/v1/auth/password-reset", Map.of("email", "Ana@x.com", "challengeToken", "t"), null);
        String second = codeIn("redefinir-senha");
        assertThat(second).isNotEqualTo(first);
        assertThat(shape(post("/api/v1/auth/password-reset/" + first, Map.of("password", "Novasenha1"), null).andReturn())).startsWith("410|RESET_LINK_INVALID");

        assertThat(shape(post("/api/v1/auth/password-reset/" + second, Map.of("password", "abcdefg1"), null).andReturn())).startsWith("400|PASSWORD_WEAK");
        clock.now = clock.now.plus(Duration.ofMinutes(59));
        MvcResult done = post("/api/v1/auth/password-reset/" + second, Map.of("password", "Novasenha1"), null).andReturn();
        assertThat(done.getResponse().getStatus()).isEqualTo(200);
        Cookie fresh = done.getResponse().getCookie("SESSION");
        assertThat(fresh).isNotNull();
        assertThat(get("/api/v1/auth/session", fresh).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(get("/api/v1/auth/session", old).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(post("/api/v1/auth/password-reset/" + second, Map.of("password", "Novasenha2"), null).andReturn().getResponse().getStatus()).isEqualTo(410);
        assertThat(login("ana@x.com", "Novasenha1").getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.password_reset'", org)).isEqualTo(1);

        post("/api/v1/auth/password-reset", Map.of("email", "ana@x.com", "challengeToken", "t"), null);
        String late = codeIn("redefinir-senha");
        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        assertThat(post("/api/v1/auth/password-reset/" + late, Map.of("password", "Novasenha3"), null).andReturn().getResponse().getStatus()).isEqualTo(410);
    }

    @Test
    @DisplayName("CA4, CA5: conta desativada não recebe e-mail; o link vale até 1 hora exata e 1 hora cravada já é 410")
    void resetLinkBoundary() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        UUID off = account("off@x.com");
        link(off, org, "ADMINISTRATOR", "ACTIVE");
        admin.update("update account set status = 'DEACTIVATED' where id = ?", off);
        post("/api/v1/auth/password-reset", Map.of("email", "off@x.com", "challengeToken", "t"), null);
        assertThat(outbox.list()).isEmpty();

        post("/api/v1/auth/password-reset", Map.of("email", "ana@x.com", "challengeToken", "t"), null);
        String code = codeIn("redefinir-senha");
        clock.now = clock.now.plus(Duration.ofHours(1));
        assertThat(shape(post("/api/v1/auth/password-reset/" + code, Map.of("password", "Novasenha1"), null).andReturn())).startsWith("410|RESET_LINK_INVALID");
        clock.now = clock.now.minusSeconds(1);
        assertThat(post("/api/v1/auth/password-reset/" + code, Map.of("password", "Novasenha1"), null).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA12: conta com convite pendente recebe o convite de novo, não o link de senha")
    void pendingInviteGetsInviteAgain() throws Exception {
        UUID org = org();
        UUID coordinator = account("coord@x.com");
        var issued = invites.invite(new Inviter(coordinator, Role.COORDINATOR, Set.of()), org, "nova@x.com", "Nova", Role.ADMINISTRATOR, Set.of(), null);
        outbox.clear();
        post("/api/v1/auth/password-reset", Map.of("email", "nova@x.com", "challengeToken", "t"), null);
        assertThat(outbox.list()).hasSize(1);
        assertThat(outbox.list().get(0).content()).contains("/convite/").doesNotContain("redefinir-senha");
        String again = codeIn("convite");
        assertThat(again).isNotEqualTo(issued.code());
        assertThat(shape(get("/api/v1/auth/invitations/" + issued.code(), null).andReturn())).startsWith("410|INVITE_INVALID").contains("CANCELED");
        assertThat(get("/api/v1/auth/invitations/" + again, null).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA5, CA6: aceite pelas rotas: conta nova define senha e entra; usado, cancelado, vencido e desconhecido dão INVITE_INVALID")
    void acceptInvite() throws Exception {
        UUID org = org();
        Inviter coordinator = new Inviter(account("coord@x.com"), Role.COORDINATOR, Set.of());
        var issued = invites.invite(coordinator, org, "nova@x.com", "Nova", Role.ADMINISTRATOR, Set.of(), null);

        MvcResult info = get("/api/v1/auth/invitations/" + issued.code(), null).andReturn();
        assertThat(info.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(info).path("role").asText()).isEqualTo("ADMINISTRATOR");
        assertThat(body(info).path("existingAccount").asBoolean()).isFalse();
        assertThat(body(info).path("email").asText()).isEqualTo("nova@x.com");
        assertThat(body(info).path("name").asText()).isEqualTo("Nova");
        assertThat(java.time.OffsetDateTime.parse(body(info).path("expiresAt").asText()).toInstant()).isEqualTo(clock.now.plus(Duration.ofDays(7)));

        assertThat(shape(post("/api/v1/auth/invitations/" + issued.code() + "/accept", Map.of("password", "Password1"), null).andReturn()))
                .startsWith("400|PASSWORD_LEAKED");
        MvcResult accepted = post("/api/v1/auth/invitations/" + issued.code() + "/accept", Map.of("password", PASSWORD), null).andReturn();
        assertThat(accepted.getResponse().getStatus()).isEqualTo(200);
        assertThat(accepted.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(body(accepted).path("activeLinkId").asText()).isEqualTo(issued.linkId().toString());

        String used = shape(get("/api/v1/auth/invitations/" + issued.code(), null).andReturn());
        assertThat(used).startsWith("410|INVITE_INVALID").contains("USED").doesNotContain("nova@x.com", "expiresAt");
        assertThat(shape(post("/api/v1/auth/invitations/" + issued.code() + "/accept", Map.of("password", PASSWORD), null).andReturn())).isEqualTo(used);
        String unknown = shape(get("/api/v1/auth/invitations/nao-existe", null).andReturn());
        assertThat(unknown).startsWith("410|INVITE_INVALID").doesNotContain("USED", "EXPIRED", "CANCELED");

        var late = invites.invite(coordinator, org, "tarde@x.com", "Tarde", Role.ADMINISTRATOR, Set.of(), null);
        clock.now = clock.now.plus(Duration.ofDays(7)).plusSeconds(1);
        assertThat(shape(get("/api/v1/auth/invitations/" + late.code(), null).andReturn())).contains("EXPIRED");
    }

    @Test
    @DisplayName("CA5: e-mail já cadastrado aceita com a senha atual; senha errada não ativa e conta como falha")
    void acceptWithExistingAccount() throws Exception {
        UUID orgA = org(), orgB = org();
        UUID ana = account("ana@x.com");
        link(ana, orgA, "TEACHER", "ACTIVE");
        Inviter coordinator = new Inviter(account("coord@x.com"), Role.COORDINATOR, Set.of());
        long accounts = count("select count(*) from account");
        var issued = invites.invite(coordinator, orgB, "ANA@x.com", "Ana", Role.ADMINISTRATOR, Set.of(), null);
        assertThat(count("select count(*) from account")).isEqualTo(accounts);

        assertThat(body(get("/api/v1/auth/invitations/" + issued.code(), null).andReturn()).path("existingAccount").asBoolean()).isTrue();
        assertThat(shape(post("/api/v1/auth/invitations/" + issued.code() + "/accept", Map.of("password", "Errada123"), null).andReturn()))
                .startsWith("401|INVALID_CREDENTIALS");
        assertThat(count("select count(*) from login_attempt where email = 'ana@x.com' and result = 'WRONG_PASSWORD'")).isEqualTo(1);
        assertThat(admin.queryForObject("select status from account_link where id = ?", String.class, issued.linkId())).isEqualTo("PENDING");

        MvcResult ok = post("/api/v1/auth/invitations/" + issued.code() + "/accept", Map.of("password", PASSWORD), null).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(admin.queryForObject("select status from account_link where id = ?", String.class, issued.linkId())).isEqualTo("ACTIVE");
        assertThat(body(ok).path("links")).hasSize(2);
        assertThat(body(ok).path("activeLinkId").isNull()).isTrue();
    }
}
