package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class TwoFactorIsolationIT extends AuthBase {
    static final List<String> ROUTES = List.of("/api/v1/auth/two-factor/setup", "/api/v1/auth/two-factor/enable",
            "/api/v1/auth/two-factor/backup-codes", "/api/v1/auth/reauth");

    @Autowired AccountTransactions accountTx;
    @Autowired JdbcClient jdbc;

    @Test
    @DisplayName("CA18: sem sessão as rotas dão 401 e sem o cabeçalho CSRF a escrita é recusada")
    void anonymousAndCsrf() throws Exception {
        for (String url : ROUTES) {
            assertThat(post(url, Map.of("code", "123456", "password", "x", "action", "FULL_EXPORT"), null).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
        assertThat(send(MockMvcRequestBuilders.delete("/api/v1/auth/two-factor"), Map.of("password", "x", "code", "1"), null).andReturn()
                .getResponse().getStatus()).isEqualTo(401);

        link(account("a@x.com"), org(), "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("a@x.com");
        for (String url : ROUTES) {
            MvcResult r = mvc.perform(MockMvcRequestBuilders.post(url).secure(true).cookie(s)
                    .contentType("application/json").content("{\"code\":\"123456\",\"password\":\"x\",\"action\":\"FULL_EXPORT\"}")).andReturn();
            assertThat(r.getResponse().getStatus()).isEqualTo(403);
        }
        MvcResult noCsrf = mvc.perform(MockMvcRequestBuilders.delete("/api/v1/auth/two-factor").secure(true).cookie(s)
                .contentType("application/json").content("{\"password\":\"x\",\"code\":\"1\"}")).andReturn();
        assertThat(noCsrf.getResponse().getStatus()).isEqualTo(403);
        assertThat(count("select count(*) from account_two_factor")).isZero();
    }

    @Test
    @DisplayName("CA18: A não vê nem altera segredo, códigos nem token de B, em nenhum perfil")
    void accountsStaySeparate() throws Exception {
        UUID orgA = org(), orgB = org();
        UUID b = account("b@x.com");
        link(b, orgB, "ADMINISTRATOR", "ACTIVE");
        Cookie sb = sessionOf("b@x.com");
        var onB = activate(sb, "b@x.com");
        String reauthB = body(post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", PASSWORD, "code", nextCode(onB.secret())), sb).andReturn())
                .path("reauthToken").asText();
        String secretB = admin.queryForObject("select secret_encrypted from account_two_factor where account_id = ?", String.class, b);

        Set<String> codesOfB = hashes(b);
        Map<UUID, Set<String>> untouched = new java.util.HashMap<>(Map.of(b, codesOfB));
        int n = 0;
        for (String role : List.of("COORDINATOR", "ADMINISTRATOR", "TEACHER", "STUDENT", "GUARDIAN")) {
            String email = "p" + n++ + "@x.com";
            UUID p = account(email);
            link(p, orgA, role, "ACTIVE");
            Cookie s = sessionOf(email);
            // own account only: setup answers a secret that is not B's, and B's code does not enable it
            MvcResult setup = post("/api/v1/auth/two-factor/setup", null, s).andReturn();
            assertThat(setup.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(setup).path("secret").asText()).isNotEqualTo(Base32.encode(onB.secret()));
            assertThat(post("/api/v1/auth/two-factor/enable", Map.of("code", Totp.code(onB.secret(), Totp.step(clock.now))), s).andReturn()
                    .getResponse().getStatus()).isEqualTo(401);
            assertThat(post("/api/v1/auth/two-factor/backup-codes", null, s).andReturn()
                    .getResponse().getStatus()).isEqualTo(409);
            assertThat(post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn()
                    .getResponse().getStatus()).isEqualTo(role.equals("COORDINATOR") ? 403 : 200);
            assertThat(body(get("/api/v1/auth/session", s).andReturn()).toString()).doesNotContain("b@x.com", secretB, reauthB, onB.codes().get(0));

            // two-step on for this profile: generating answers 200 and swaps only its own codes
            activate(s, email);
            Set<String> before = hashes(p);
            assertThat(before).hasSize(10);
            MvcResult regen = post("/api/v1/auth/two-factor/backup-codes", null, s).andReturn();
            assertThat(regen.getResponse().getStatus()).isEqualTo(200);
            assertThat(body(regen).path("backupCodes")).hasSize(10);
            Set<String> after = hashes(p);
            assertThat(after).hasSize(10).doesNotContainAnyElementsOf(before);
            for (var other : untouched.entrySet()) assertThat(hashes(other.getKey())).as("conta " + other.getKey()).isEqualTo(other.getValue());
            untouched.put(p, after);
        }
        assertThat(admin.queryForObject("select secret_encrypted from account_two_factor where account_id = ?", String.class, b)).isEqualTo(secretB);
        assertThat(hashes(b)).isEqualTo(codesOfB);
        assertThat(count("select count(*) from account_backup_code where account_id = ? and used_at is null", b)).isEqualTo(10);
        assertThat(count("select count(*) from reauth_token where account_id = ? and used_at is null", b)).isEqualTo(1);
    }

    private Set<String> hashes(UUID account) {
        return new java.util.HashSet<>(admin.queryForList("select code_hash from account_backup_code where account_id = ?", String.class, account));
    }

    @Test
    @DisplayName("CA18 / RLS: com o escopo de uma conta, segredo, códigos, bilhetes e tokens de outra não aparecem")
    void rowLevelSecurity() throws Exception {
        UUID orgA = org(), orgB = org();
        UUID a = account("a@x.com"), b = account("b@x.com");
        link(a, orgA, "ADMINISTRATOR", "ACTIVE");
        link(b, orgB, "ADMINISTRATOR", "ACTIVE");
        enableTwoFactor(a, "a@x.com");
        enableTwoFactor(b, "b@x.com");
        admin.update("insert into account_backup_code (account_id, code_hash) values (?, 'h')", a);
        admin.update("insert into login_ticket (account_id, token_hash, expires_at) values (?, 'th', now() + interval '5 minutes')", a);
        Cookie sa = sessionOf("a@x.com");
        post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", PASSWORD, "code", nextCode(secrets.get("a@x.com"))), sa);

        for (String table : List.of("account_two_factor", "account_backup_code", "login_ticket", "reauth_token")) {
            assertThat(accountTx.forAccount(b, () -> jdbc.sql("select count(*) from " + table + " where account_id = :a")
                    .param("a", a).query(Long.class).single())).as(table).isZero();
            assertThat(accountTx.forAccount(a, () -> jdbc.sql("select count(*) from " + table).query(Long.class).single())).as(table).isPositive();
        }
        // the ticket is also found by its hash, and only by it
        assertThat(accountTx.forInvite("th", () -> jdbc.sql("select count(*) from login_ticket").query(Long.class).single())).isEqualTo(1);
        assertThat(accountTx.forInvite("outro", () -> jdbc.sql("select count(*) from login_ticket").query(Long.class).single())).isZero();
    }
}
