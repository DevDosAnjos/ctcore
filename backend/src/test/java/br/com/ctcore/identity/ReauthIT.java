package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class ReauthIT extends AuthBase {
    static final String REAUTH = "/api/v1/auth/reauth";

    @Autowired CriticalActionGuard guard;

    UUID account;

    Cookie session(String email) throws Exception {
        account = account(email);
        link(account, org(), "ADMINISTRATOR", "ACTIVE");
        return sessionOf(email);
    }

    AccountPrincipal principal(UUID sessionId) {
        return new AccountPrincipal(account, sessionId, null, null, Role.ADMINISTRATOR, Set.of());
    }

    UUID sessionId(Cookie c) {
        return admin.queryForObject("select id from account_session where token_hash = ?", UUID.class, Codes.hash(c.getValue()));
    }

    @Test
    @DisplayName("RN-ACE-21: CA16: senha certa devolve token de 5 minutos; só a senha quando as duas etapas estão desligadas")
    void passwordOnly() throws Exception {
        Cookie s = session("a@x.com");
        MvcResult r = post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(r).path("reauthToken").asText()).isNotBlank();
        assertThat(java.time.Instant.parse(body(r).path("expiresAt").asText())).isEqualTo(clock.now.plusSeconds(300));
        assertThat(count("select count(*) from reauth_token where token_hash = ?", body(r).path("reauthToken").asText())).isZero();
        assertThat(count("select count(*) from reauth_token where token_hash = ?", Codes.hash(body(r).path("reauthToken").asText()))).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-ACE-21: CA16: senha errada, código ausente ou errado (com duas etapas) e ação fora da lista não dão token; os erros contam para a trava")
    void refusals() throws Exception {
        Cookie s = session("a@x.com");
        assertThat(shape(post(REAUTH, Map.of("action", "FULL_EXPORT", "password", "errada"), s).andReturn())).startsWith("401|INVALID_CREDENTIALS");
        assertThat(post(REAUTH, Map.of("action", "OUTRA", "password", PASSWORD), s).andReturn().getResponse().getStatus()).isEqualTo(400);
        var on = activate(s, "a@x.com");
        assertThat(shape(post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn())).startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(shape(post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD, "code", "000000"), s).andReturn()))
                .startsWith("401|INVALID_TWO_FACTOR_CODE");
        assertThat(count("select count(*) from reauth_token")).isZero();
        assertThat(count("select count(*) from login_attempt where result = 'WRONG_CODE'")).isEqualTo(2);
        assertThat(count("select count(*) from login_attempt where result = 'WRONG_PASSWORD'")).isEqualTo(1);

        MvcResult ok = post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD, "code", nextCode(on.secret())), s).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        // a backup code serves as the code too
        assertThat(post(REAUTH, Map.of("action", "DEACTIVATE_UNIT", "password", PASSWORD, "code", on.codes().get(0)), s).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
        // 5 wrong passwords in a row lock the e-mail, here too
        for (int i = 0; i < 5; i++) {
            post(REAUTH, Map.of("action", "FULL_EXPORT", "password", "errada"), s);
        }
        assertThat(post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn().getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("RN-ACE-21: CA17: o guarda passa uma vez; outra vez, outra ação, outra sessão, vencido ou ausente dão REAUTH_REQUIRED")
    void guard() throws Exception {
        Cookie s = session("a@x.com");
        UUID sid = sessionId(s);
        String token = body(post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn()).path("reauthToken").asText();

        assertThatThrownBy(() -> guard.consume(principal(sid), CriticalAction.CANCEL_SUBSCRIPTION, token)).isInstanceOf(IdentityException.class)
                .extracting(e -> ((IdentityException) e).code()).isEqualTo(IdentityException.Code.REAUTH_REQUIRED);
        Cookie other = sessionOf("a@x.com");
        assertThatThrownBy(() -> guard.consume(principal(sessionId(other)), CriticalAction.FULL_EXPORT, token)).isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> guard.consume(principal(sid), CriticalAction.FULL_EXPORT, null)).isInstanceOf(IdentityException.class);
        assertThatThrownBy(() -> guard.consume(principal(sid), CriticalAction.FULL_EXPORT, "inventado")).isInstanceOf(IdentityException.class);

        guard.consume(principal(sid), CriticalAction.FULL_EXPORT, token);
        assertThatThrownBy(() -> guard.consume(principal(sid), CriticalAction.FULL_EXPORT, token)).isInstanceOf(IdentityException.class);

        String late = body(post(REAUTH, Map.of("action", "ANONYMIZE_STUDENT", "password", PASSWORD), s).andReturn()).path("reauthToken").asText();
        clock.now = clock.now.plusSeconds(301);
        assertThatThrownBy(() -> guard.consume(principal(sid), CriticalAction.ANONYMIZE_STUDENT, late)).isInstanceOf(IdentityException.class);
    }

    @Test
    @DisplayName("RN-ACE-21: encerrar a sessão apaga os tokens dela")
    void tokenDiesWithSession() throws Exception {
        Cookie s = session("a@x.com");
        post(REAUTH, Map.of("action", "FULL_EXPORT", "password", PASSWORD), s);
        assertThat(count("select count(*) from reauth_token")).isEqualTo(1);
        post("/api/v1/auth/logout", null, s);
        assertThat(count("select count(*) from reauth_token")).isZero();
    }
}
