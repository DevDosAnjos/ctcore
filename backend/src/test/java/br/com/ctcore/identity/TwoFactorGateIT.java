package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class TwoFactorGateIT extends AuthBase {
    @Test
    @DisplayName("RN-ACE-17: CA5: Coordenador sem duas etapas só alcança sessão, saída e configuração; depois de ativar, o resto responde")
    void coordinatorIsGated() throws Exception {
        link(account("c@x.com"), org(), "COORDINATOR", "ACTIVE");
        Cookie s = sessionOf("c@x.com");

        for (MvcResult r : List.of(get("/api/v1/probe-org", s).andReturn(),
                put("/api/v1/auth/session/link", Map.of("linkId", java.util.UUID.randomUUID()), s).andReturn(),
                post("/api/v1/auth/password", Map.of("currentPassword", PASSWORD, "newPassword", "Novasenha1"), s).andReturn(),
                post("/api/v1/auth/reauth", Map.of("action", "FULL_EXPORT", "password", PASSWORD), s).andReturn())) {
            assertThat(r.getResponse().getStatus()).isEqualTo(403);
            assertThat(body(r).path("code").asText()).isEqualTo("TWO_FACTOR_SETUP_REQUIRED");
        }
        MvcResult view = get("/api/v1/auth/session", s).andReturn();
        assertThat(view.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(view).path("twoFactor").toString()).isEqualTo("{\"enabled\":false,\"required\":true}");

        activate(s, "c@x.com");
        assertThat(get("/api/v1/probe-org", s).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(body(get("/api/v1/auth/session", s).andReturn()).path("twoFactor").toString()).isEqualTo("{\"enabled\":true,\"required\":true}");
    }

    @Test
    @DisplayName("RN-ACE-17: CA5: a saída segue livre para quem ainda não ativou")
    void logoutStaysOpen() throws Exception {
        link(account("c@x.com"), org(), "COORDINATOR", "ACTIVE");
        Cookie s = sessionOf("c@x.com");
        assertThat(post("/api/v1/auth/logout", null, s).andReturn().getResponse().getStatus()).isEqualTo(204);
        assertThat(get("/api/v1/auth/session", s).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RN-ACE-17: CA5: o vínculo de Coordenador em qualquer organização obriga, mesmo com outro vínculo ativo; vínculo encerrado não obriga")
    void anyActiveCoordinatorLink() throws Exception {
        var acc = account("m@x.com");
        link(acc, org(), "STUDENT", "ACTIVE");
        link(acc, org(), "COORDINATOR", "ACTIVE");
        Cookie s = sessionOf("m@x.com");
        assertThat(get("/api/v1/probe-org", s).andReturn().getResponse().getStatus()).isEqualTo(403);

        var ended = account("e@x.com");
        link(ended, org(), "COORDINATOR", "ENDED");
        link(ended, org(), "ADMINISTRATOR", "ACTIVE");
        assertThat(get("/api/v1/probe-org", sessionOf("e@x.com")).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("RN-ACE-17: CA6: os demais perfis não são bloqueados e twoFactor.required é falso")
    void otherRolesAreFree() throws Exception {
        int n = 0;
        for (String role : List.of("ADMINISTRATOR", "TEACHER", "STUDENT", "GUARDIAN")) {
            String email = "r" + n++ + "@x.com";
            link(account(email), org(), role, "ACTIVE");
            Cookie s = sessionOf(email);
            assertThat(get("/api/v1/probe-org", s).andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(body(get("/api/v1/auth/session", s).andReturn()).path("twoFactor").path("required").asBoolean()).isFalse();
        }
    }
}
