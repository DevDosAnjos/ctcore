package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.integration.Outbox;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class NewAccessIT extends AuthBase {
    static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36";
    static final String FIREFOX = "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0";

    Cookie loginWith(String email, String agent) throws Exception {
        var r = send(MockMvcRequestBuilders.post("/api/v1/auth/login").header("User-Agent", agent),
                java.util.Map.of("email", email, "password", PASSWORD, "challengeToken", "t"), null).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r.getResponse().getCookie("SESSION");
    }

    List<Outbox.Message> newAccessMails() {
        return outbox.list().stream().filter(m -> m.subject().equals("Novo acesso à sua conta")).toList();
    }

    @Test
    @DisplayName("RN-ACE-22: CA8: 1º login sem aviso; outro aparelho avisa com aparelho, cidade e hora de Brasília; mesmo aparelho depois de sair não avisa")
    void newDevice() throws Exception {
        account("ana@x.com");
        Cookie first = loginWith("ana@x.com", CHROME);
        assertThat(newAccessMails()).isEmpty();
        assertThat(count("select count(*) from account_device")).isEqualTo(1);

        loginWith("ana@x.com", FIREFOX);
        assertThat(newAccessMails()).hasSize(1);
        String text = newAccessMails().get(0).content();
        assertThat(text).contains("Firefox em Linux", "local não identificado", "17/10/2026 09:00");
        assertThat(newAccessMails().get(0).recipient()).isEqualTo("ana@x.com");

        post("/api/v1/auth/logout", null, first).andReturn();
        loginWith("ana@x.com", CHROME);
        assertThat(newAccessMails()).hasSize(1);
    }
}
