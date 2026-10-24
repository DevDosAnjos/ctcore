package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/** RN-COM-13 (#78): ler e alterar a preferência de comunicados do vínculo ativo, com auditoria sem dado pessoal. */
@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class EmailPreferencesIT extends AuthBase {
    @Test
    @DisplayName("CA8: aluno lê ligada, desliga e liga; cada troca grava uma linha de auditoria sem e-mail")
    void readAndChange() throws Exception {
        UUID org = org();
        UUID link = link(account("aluno@prefs.example"), org, "STUDENT", "ACTIVE");
        Cookie s = sessionOf("aluno@prefs.example");

        assertThat(body(get("/api/v1/auth/email-preferences", s).andReturn()).path("announcements").asBoolean()).isTrue();
        var off = put("/api/v1/auth/email-preferences", Map.of("announcements", false), s).andReturn();
        assertThat(off.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(off).path("announcements").asBoolean()).isFalse();
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, link)).isFalse();
        assertThat(body(get("/api/v1/auth/email-preferences", s).andReturn()).path("announcements").asBoolean()).isFalse();
        assertThat(count("select count(*) from audit_log where action = 'account.email_preferences_changed' and organization_id = ?", org)).isEqualTo(1);
        assertThat(admin.queryForObject("select coalesce(string_agg(before_data::text || after_data::text || coalesce(ip, ''), ''), '') from audit_log where organization_id = ?",
                String.class, org)).doesNotContain("prefs.example");

        put("/api/v1/auth/email-preferences", Map.of("announcements", true), s);
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, link)).isTrue();
    }

    @Test
    @DisplayName("CA11: valor que não seja booleano, nulo ou ausente responde 422 e nada muda")
    void invalidValue() throws Exception {
        UUID org = org();
        UUID link = link(account("guardian@prefs.example"), org, "GUARDIAN", "ACTIVE");
        Cookie s = sessionOf("guardian@prefs.example");
        for (Object bad : new Object[]{"false", 0, "sim"}) {
            assertThat(put("/api/v1/auth/email-preferences", Map.of("announcements", bad), s).andReturn().getResponse().getStatus()).isEqualTo(422);
        }
        assertThat(put("/api/v1/auth/email-preferences", Map.of(), s).andReturn().getResponse().getStatus()).isEqualTo(422);
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, link)).isTrue();
    }
}
