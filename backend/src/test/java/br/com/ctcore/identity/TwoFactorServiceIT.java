package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class TwoFactorServiceIT extends AuthBase {
    static final String SETUP = "/api/v1/auth/two-factor/setup", ENABLE = "/api/v1/auth/two-factor/enable",
            DISABLE = "/api/v1/auth/two-factor", CODES = "/api/v1/auth/two-factor/backup-codes";

    private Cookie admin1(String email) throws Exception {
        link(account(email), org(), "ADMINISTRATOR", "ACTIVE");
        return sessionOf(email);
    }

    MvcResult delete(String url, Object body, Cookie session) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), body, session).andReturn();
    }

    @Test
    @DisplayName("RN-ACE-17: CA1, CA2, CA4, CA15: o setup não ativa nada; código inválido não liga; código válido liga e traz 10 códigos; segredo cifrado; nenhum e-mail leva código")
    void setupAndEnable() throws Exception {
        Cookie s = admin1("a@x.com");
        MvcResult setup = post(SETUP, null, s).andReturn();
        assertThat(setup.getResponse().getStatus()).isEqualTo(200);
        String secret = body(setup).path("secret").asText();
        assertThat(body(setup).path("otpauthUri").asText()).startsWith("otpauth://totp/CTCore").contains("secret=" + secret, "issuer=CTCore");
        assertThat(body(get("/api/v1/auth/session", s).andReturn()).path("twoFactor").toString()).isEqualTo("{\"enabled\":false,\"required\":false}");
        assertThat(count("select count(*) from account_two_factor where enabled_at is not null")).isZero();
        String stored = admin.queryForObject("select secret_encrypted from account_two_factor", String.class);
        assertThat(stored).doesNotContain(secret);

        byte[] raw = Base32.decode(secret);
        MvcResult bad = post(ENABLE, Map.of("code", "000000"), s).andReturn();
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(bad).path("code").asText()).isEqualTo("INVALID_TWO_FACTOR_CODE");
        assertThat(count("select count(*) from account_two_factor where enabled_at is not null")).isZero();

        MvcResult ok = post(ENABLE, Map.of("code", nextCode(raw)), s).andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        var codes = new HashSet<String>();
        body(ok).path("backupCodes").forEach(c -> codes.add(c.asText()));
        assertThat(codes).hasSize(10).allMatch(c -> c.matches("[A-Z2-9]{10}"));
        assertThat(count("select count(*) from account_backup_code where used_at is null")).isEqualTo(10);
        assertThat(admin.queryForList("select code_hash from account_backup_code", String.class))
                .doesNotContainAnyElementsOf(codes).allMatch(h -> h.length() == 64);
        assertThat(body(get("/api/v1/auth/session", s).andReturn()).path("twoFactor").path("enabled").asBoolean()).isTrue();
        // on already: setup and enable are refused
        assertThat(post(SETUP, null, s).andReturn().getResponse().getStatus()).isEqualTo(409);
        assertThat(post(ENABLE, Map.of("code", nextCode(raw)), s).andReturn().getResponse().getStatus()).isEqualTo(409);
        // no mail carries a code or the secret (RN-ACE-18)
        assertThat(outbox.list()).noneMatch(m -> m.content().contains(secret) || codes.stream().anyMatch(m.content()::contains));
        assertThat(outbox.list()).allMatch(m -> m.subject().equals("Novo acesso à sua conta"));
    }

    @Test
    @DisplayName("RN-ACE-17: CA7: Administrador desliga com senha e código e apaga tudo; senha errada recusa; Coordenador não desliga")
    void disable() throws Exception {
        Cookie s = admin1("a@x.com");
        var on = activate(s, "a@x.com");
        assertThat(delete(DISABLE, Map.of("password", "errada", "code", nextCode(on.secret())), s).getResponse().getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from account_two_factor where enabled_at is not null")).isEqualTo(1);
        assertThat(delete(DISABLE, Map.of("password", PASSWORD, "code", "000000"), s).getResponse().getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from account_two_factor")).isEqualTo(1);
        assertThat(delete(DISABLE, Map.of("password", PASSWORD, "code", nextCode(on.secret())), s).getResponse().getStatus()).isEqualTo(204);
        assertThat(count("select count(*) from account_two_factor")).isZero();
        assertThat(count("select count(*) from account_backup_code")).isZero();
        assertThat(delete(DISABLE, Map.of("password", PASSWORD, "code", "123456"), s).getResponse().getStatus()).isEqualTo(409);

        UUID coord = account("c@x.com");
        link(coord, org(), "COORDINATOR", "ACTIVE");
        enableTwoFactor(coord, "c@x.com");
        Cookie c = sessionOf("c@x.com");
        MvcResult r = delete(DISABLE, Map.of("password", PASSWORD, "code", nextCode(secrets.get("c@x.com"))), c);
        assertThat(r.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(r).path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(count("select count(*) from account_two_factor where account_id = ?", coord)).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-ACE-18: CA8: códigos novos (senha e código) invalidam os 10 anteriores; sem senha ou com código errado os antigos valem")
    void regenerate() throws Exception {
        Cookie s = admin1("a@x.com");
        var on = activate(s, "a@x.com");
        assertThat(post(CODES, Map.of("password", "errada", "code", nextCode(on.secret())), s).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(post(CODES, Map.of("password", PASSWORD, "code", "000000"), s).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(post(CODES, Map.of("code", nextCode(on.secret())), s).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from account_backup_code where code_hash = ?", Codes.hash(on.codes().get(0)))).isEqualTo(1);

        MvcResult r = post(CODES, Map.of("password", PASSWORD, "code", nextCode(on.secret())), s).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        var fresh = new HashSet<String>();
        body(r).path("backupCodes").forEach(c -> fresh.add(c.asText()));
        assertThat(fresh).hasSize(10).doesNotContainAnyElementsOf(on.codes());
        assertThat(count("select count(*) from account_backup_code")).isEqualTo(10);
        assertThat(count("select count(*) from account_backup_code where code_hash = ?", Codes.hash(on.codes().get(0)))).isZero();
    }
}
