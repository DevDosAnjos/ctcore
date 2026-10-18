package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class TwoFactorLoginIT extends AuthBase {
    static final String STEP2 = "/api/v1/auth/login/two-factor";

    UUID id;
    byte[] secret;

    void setUpAccount() {
        id = account("a@x.com");
        link(id, org(), "ADMINISTRATOR", "ACTIVE");
        secret = enableTwoFactor(id, "a@x.com");
        secrets.remove("a@x.com"); // the test drives the second step by hand
    }

    String ticket() throws Exception {
        MvcResult r = login("a@x.com", PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return body(r).path("ticket").asText();
    }

    MvcResult step2(String ticket, String code) throws Exception {
        return post(STEP2, Map.of("ticket", ticket, "code", code), null).andReturn();
    }

    @Test
    @DisplayName("RN-ACE-17: CA9, CA10: senha certa devolve bilhete sem cookie nem sessão nem último acesso; TOTP válido entra; bilhete não serve de novo")
    void totpLogin() throws Exception {
        setUpAccount();
        MvcResult r = login("a@x.com", PASSWORD);
        assertThat(body(r).path("twoFactorRequired").asBoolean()).isTrue();
        assertThat(r.getResponse().getCookie("SESSION")).isNull();
        assertThat(count("select count(*) from account_session")).isZero();
        assertThat(count("select count(*) from account where last_access_at is not null")).isZero();
        String t = body(r).path("ticket").asText();
        assertThat(count("select count(*) from login_ticket where token_hash = ?", Codes.hash(t))).isEqualTo(1);
        assertThat(count("select count(*) from login_ticket where token_hash = ?", t)).isZero();
        assertThat(shape(login("a@x.com", "errada"))).startsWith("401|INVALID_CREDENTIALS");

        MvcResult ok = step2(t, nextCode(secret));
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        Cookie session = ok.getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();
        assertThat(body(ok).path("account").path("email").asText()).isEqualTo("a@x.com");
        assertThat(get("/api/v1/auth/session", session).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from account where last_access_at is not null")).isEqualTo(1);
        assertThat(shape(step2(t, nextCode(secret)))).startsWith("401|TWO_FACTOR_TICKET_INVALID");
    }

    @Test
    @DisplayName("RN-ACE-18: CA11: código de reserva entra e é gasto; os outros nove continuam valendo")
    void backupCodeLogin() throws Exception {
        setUpAccount();
        Cookie s = step2(ticket(), nextCode(secret)).getResponse().getCookie("SESSION");
        var codes = body(post("/api/v1/auth/two-factor/backup-codes", Map.of("password", PASSWORD, "code", nextCode(secret)), s).andReturn()).path("backupCodes");
        String used = codes.get(0).asText();
        assertThat(step2(ticket(), used).getResponse().getStatus()).isEqualTo(200);
        assertThat(shape(step2(ticket(), used))).startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(count("select count(*) from account_backup_code where used_at is null")).isEqualTo(9);
        assertThat(step2(ticket(), codes.get(1).asText().toLowerCase()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-ACE-17: CA12: TOTP já aceito não vale de novo no mesmo passo, por outro bilhete")
    void noTotpReuse() throws Exception {
        setUpAccount();
        String code = nextCode(secret);
        assertThat(step2(ticket(), code).getResponse().getStatus()).isEqualTo(200);
        assertThat(shape(step2(ticket(), code))).startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(step2(ticket(), nextCode(secret)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-ACE-17: CA13: bilhete vencido, inexistente ou de outra conta não dá sessão")
    void invalidTicket() throws Exception {
        setUpAccount();
        UUID other = account("b@x.com");
        link(other, org(), "ADMINISTRATOR", "ACTIVE");
        byte[] otherSecret = enableTwoFactor(other, "b@x.com");
        String t = ticket();
        clock.now = clock.now.plusSeconds(301);
        String now = Totp.code(secret, Totp.step(clock.now));
        assertThat(shape(step2(t, now))).startsWith("401|TWO_FACTOR_TICKET_INVALID");
        assertThat(shape(step2("nao-existe", now))).startsWith("401|TWO_FACTOR_TICKET_INVALID");
        // the ticket of A with the code of B: wrong code, no session
        assertThat(shape(step2(ticket(), Totp.code(otherSecret, Totp.step(clock.now))))).startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(count("select count(*) from account_session")).isZero();
    }

    @Test
    @DisplayName("RN-ACE-10: CA14: 5 erros travam o e-mail por 15 minutos (inclusive com a senha certa) e matam o bilhete; erro igual para errado e usado")
    void lockout() throws Exception {
        setUpAccount();
        String t = ticket();
        String wrong = shape(step2(t, "000000"));
        assertThat(wrong).startsWith("401|INVALID_TWO_FACTOR_CODE");
        for (int i = 0; i < 3; i++) {
            assertThat(shape(step2(t, "00000" + i))).isEqualTo(wrong);
        }
        assertThat(shape(step2(t, "111111"))).isEqualTo(wrong);
        assertThat(count("select count(*) from login_ticket where used_at is not null and failed_attempts = 5")).isEqualTo(1);
        assertThat(login("a@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(429);
        assertThat(step2(t, Totp.code(secret, Totp.step(clock.now))).getResponse().getStatus()).isEqualTo(429);
        clock.now = clock.now.plusSeconds(16 * 60);
        assertThat(login("a@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-ACE-10: CA14: 20 falhas no mesmo IP em 15 minutos limitam o IP")
    void ipLimit() throws Exception {
        setUpAccount();
        for (int i = 0; i < 20; i++) {
            admin.update("insert into login_attempt (email, ip, occurred_at, result) values (?, '127.0.0.1', ?, 'WRONG_CODE')",
                    "x" + i + "@x.com", java.sql.Timestamp.from(clock.now));
        }
        assertThat(step2("qualquer", "000000").getResponse().getStatus()).isEqualTo(429);
        assertThat(login("a@x.com", PASSWORD).getResponse().getStatus()).isEqualTo(429);
    }
}
