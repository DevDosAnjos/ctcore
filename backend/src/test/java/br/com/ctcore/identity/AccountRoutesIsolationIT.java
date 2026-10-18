package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** CA14: the new account routes act on the session account only, in every profile and organization. */
@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class AccountRoutesIsolationIT extends AuthBase {
    @Test
    @DisplayName("CA14: sem sessão as cinco rotas dão 401 e sem CSRF a escrita é recusada")
    void anonymousAndCsrf() throws Exception {
        String sid = UUID.randomUUID().toString();
        assertThat(get("/api/v1/auth/sessions", null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(delete("/api/v1/auth/sessions", null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(delete("/api/v1/auth/sessions/" + sid, null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(post("/api/v1/auth/email-change", Map.of("newEmail", "n@x.com", "password", "x"), null).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(post("/api/v1/auth/email-change/abc/confirm", null, null).andReturn().getResponse().getStatus()).isEqualTo(401);

        link(account("a@x.com"), org(), "ADMINISTRATOR", "ACTIVE");
        Cookie s = sessionOf("a@x.com");
        assertThat(mvc.perform(MockMvcRequestBuilders.delete("/api/v1/auth/sessions").secure(true).cookie(s)).andReturn().getResponse().getStatus()).isEqualTo(403);
        assertThat(mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/email-change/abc/confirm").secure(true).cookie(s)).andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA14: em qualquer perfil, A não vê nem encerra sessão de B nem confirma pedido de e-mail de B")
    void accountsStaySeparate() throws Exception {
        UUID orgA = org(), orgB = org();
        link(account("b@x.com"), orgB, "ADMINISTRATOR", "ACTIVE");
        Cookie sb = sessionOf("b@x.com");
        UUID idB = admin.queryForObject("select id from account_session where token_hash = ?", UUID.class, Codes.hash(sb.getValue()));
        assertThat(post("/api/v1/auth/email-change", Map.of("newEmail", "bnovo@x.com", "password", PASSWORD), sb).andReturn().getResponse().getStatus()).isEqualTo(202);
        Matcher m = Pattern.compile("confirmar-email/(\\S+)").matcher(outbox.list().get(0).content());
        assertThat(m.find()).isTrue();
        String codeB = m.group(1);

        int n = 0;
        for (String role : new String[]{"COORDINATOR", "ADMINISTRATOR", "TEACHER", "STUDENT", "GUARDIAN"}) {
            String email = "p" + n++ + "@x.com";
            UUID id = account(email);
            link(id, orgA, role, "ACTIVE");
            if (role.equals("COORDINATOR")) {
                enableTwoFactor(id, email);
            }
            Cookie s = sessionOf(email);
            assertThat(body(get("/api/v1/auth/sessions", s).andReturn()).toString()).doesNotContain(idB.toString());
            assertThat(delete("/api/v1/auth/sessions/" + idB, s).andReturn().getResponse().getStatus()).isEqualTo(404);
            assertThat(post("/api/v1/auth/email-change/" + codeB + "/confirm", null, s).andReturn().getResponse().getStatus()).isEqualTo(410);
            assertThat(delete("/api/v1/auth/sessions", s).andReturn().getResponse().getStatus()).isEqualTo(204);
        }
        assertThat(get("/api/v1/auth/session", sb).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(count("select count(*) from account where email = 'b@x.com'")).isEqualTo(1);
        assertThat(count("select count(*) from account_email_change where used_at is null and canceled_at is null")).isEqualTo(1);
    }
}
