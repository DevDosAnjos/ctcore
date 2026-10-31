package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class TwoFactorAuditIT extends AuthBase {
    private long audited(UUID org, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ? and ip is not null and entity_id is not null", org, action);
    }

    @Test
    @DisplayName("RN-ACE-17, RN-ACE-18, RN-ACE-21: CA19: ligar, códigos novos, código de reserva, confirmação aceita e recusada, desligar: auditados sem segredo, código, senha ou token")
    void everyChangeIsAudited() throws Exception {
        UUID org = org();
        UUID a = account("a@x.com");
        link(a, org, "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("a@x.com");
        var on = activate(s, "a@x.com");
        assertThat(audited(org, "account.two_factor_enabled")).isEqualTo(1);

        var fresh = body(post("/api/v1/auth/two-factor/backup-codes", null, s).andReturn());
        assertThat(audited(org, "account.backup_codes_regenerated")).isEqualTo(1);

        String backup = fresh.path("backupCodes").get(0).asText();
        String token = body(post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", PASSWORD, "code", backup), s).andReturn())
                .path("reauthToken").asText();
        assertThat(audited(org, "account.backup_code_used")).isEqualTo(1);
        assertThat(audited(org, "account.reauth_confirmed")).isEqualTo(1);

        post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", "SenhaErrada9"), s);
        assertThat(audited(org, "account.reauth_failed")).isEqualTo(1);

        send(MockMvcRequestBuilders.delete("/api/v1/auth/two-factor"), Map.of("password", PASSWORD, "code", nextCode(on.secret())), s);
        assertThat(audited(org, "account.two_factor_disabled")).isEqualTo(1);

        String everything = admin.queryForObject("select coalesce(string_agg(t::text, ' '), '') from audit_log t where organization_id = ?", String.class, org);
        assertThat(everything).doesNotContain(Base32.encode(on.secret()), PASSWORD, "SenhaErrada9", token, backup)
                .doesNotContain(on.codes()).doesNotContain(fresh.path("backupCodes").get(1).asText());
    }
}
