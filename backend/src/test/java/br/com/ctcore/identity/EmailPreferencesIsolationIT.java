package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/** CA11 (#78): a rota de preferência é só do portal e só age no vínculo ativo da sessão. */
@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class EmailPreferencesIsolationIT extends AuthBase {
    @Test
    @DisplayName("CA11: sem sessão 401; Administrador, Professor e Coordenador 403 e nada é gravado")
    void profiles() throws Exception {
        assertThat(get("/api/v1/auth/email-preferences", null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(put("/api/v1/auth/email-preferences", Map.of("announcements", false), null).andReturn().getResponse().getStatus()).isEqualTo(401);

        UUID org = org();
        int n = 0;
        for (String role : new String[]{"COORDINATOR", "ADMINISTRATOR", "TEACHER"}) {
            String email = "g" + n++ + "@iso.example";
            UUID id = account(email);
            UUID link = link(id, org, role, "ACTIVE");
            if (role.equals("COORDINATOR")) {
                enableTwoFactor(id, email);
            }
            Cookie s = sessionOf(email);
            assertThat(get("/api/v1/auth/email-preferences", s).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(put("/api/v1/auth/email-preferences", Map.of("announcements", false), s).andReturn().getResponse().getStatus()).isEqualTo(403);
            assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, link)).isTrue();
        }
    }

    @Test
    @DisplayName("CA11: desligar na organização A não altera o vínculo da mesma conta na B nem o de outra conta na A")
    void organizationsStaySeparate() throws Exception {
        UUID a = org(), b = org();
        UUID acc = account("ana@iso.example");
        UUID linkA = link(acc, a, "STUDENT", "ACTIVE");
        UUID linkB = link(acc, b, "GUARDIAN", "ACTIVE");
        UUID otherA = link(account("bia@iso.example"), a, "STUDENT", "ACTIVE");
        Cookie s = sessionOf("ana@iso.example");
        assertThat(put("/api/v1/auth/session/link", Map.of("linkId", linkA), s).andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(put("/api/v1/auth/email-preferences", Map.of("announcements", false), s).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, linkA)).isFalse();
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, linkB)).isTrue();
        assertThat(admin.queryForObject("select announcement_email from account_link where id = ?", Boolean.class, otherA)).isTrue();
        assertThat(count("select count(*) from audit_log where action = 'account.email_preferences_changed' and organization_id = ?", b)).isZero();
    }
}
