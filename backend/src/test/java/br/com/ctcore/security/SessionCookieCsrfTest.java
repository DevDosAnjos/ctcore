package br.com.ctcore.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sobe a aplicação inteira (sem banco) para provar o cookie de sessão no servidor real e o CSRF. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ctcore.scheduler.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
@AutoConfigureMockMvc
@Import(SessionCookieCsrfTest.Probe.class)
class SessionCookieCsrfTest {

    static final AtomicInteger writes = new AtomicInteger();

    @RestController
    static class Probe {
        @GetMapping("/probe-session/login")
        String login(HttpServletRequest request) {
            return request.getSession(true).getId();
        }

        @GetMapping("/probe-session/read")
        String read() {
            return "ok";
        }

        @PostMapping("/probe-session/write")
        void write() {
            writes.incrementAndGet();
        }
    }

    /** Login simulado: rota que só existe no teste, fora da cadeia de segurança principal. */
    @TestConfiguration
    static class OpenLogin {
        @Bean
        @Order(0)
        SecurityFilterChain open(HttpSecurity http) throws Exception {
            return http.securityMatcher("/probe-session/login")
                    .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .csrf(AbstractHttpConfigurer::disable)
                    .build();
        }
    }

    @Autowired TestRestTemplate rest;
    @Autowired MockMvc mvc;

    @Test
    @DisplayName("CA13 / RNF-07: cookie de sessão tem HttpOnly, Secure e SameSite=Lax")
    void sessionCookieFlags() {
        HttpHeaders h = new HttpHeaders();
        h.add("X-Forwarded-Proto", "https");
        ResponseEntity<String> res = rest.exchange("/probe-session/login", HttpMethod.GET, new HttpEntity<>(h), String.class);
        String cookie = res.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(cookie).startsWith("SESSION=").contains("HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    @DisplayName("CA14 / RNF-07: mesmo sem sessão (401), a primeira resposta já traz o cookie XSRF-TOKEN legível pelo SPA")
    void xsrfCookieBeforeLogin() throws Exception {
        Cookie xsrf = mvc.perform(get("/api/v1/x").secure(true))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.getValue()).isNotBlank();
        assertThat(xsrf.isHttpOnly()).isFalse();
    }

    @Test
    @WithMockUser
    @DisplayName("CA14 / RNF-07: fluxo do SPA: devolve o cookie XSRF-TOKEN em X-XSRF-TOKEN e grava; sem ou com outro valor, 403")
    void spaCsrfFlow() throws Exception {
        Cookie xsrf = mvc.perform(get("/probe-session/read").secure(true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        int before = writes.get();

        mvc.perform(post("/probe-session/write").secure(true).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isOk());
        assertThat(writes.get()).isEqualTo(before + 1);

        mvc.perform(post("/probe-session/write").secure(true).cookie(xsrf)).andExpect(status().isForbidden());
        mvc.perform(post("/probe-session/write").secure(true).cookie(xsrf).header("X-XSRF-TOKEN", "outro-valor"))
                .andExpect(status().isForbidden());
        assertThat(writes.get()).isEqualTo(before + 1);
    }

    @Test
    @WithMockUser
    @DisplayName("CA14 / RNF-07: POST sem token CSRF é recusado com 403 e nada é alterado (o aceite com token está em spaCsrfFlow)")
    void csrfRequired() throws Exception {
        // Sem o csrf() do spring-security-test: ele troca o repositório do filtro compartilhado e o cookie some.
        int before = writes.get();
        mvc.perform(post("/probe-session/write").secure(true))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.changed").value(false));
        assertThat(writes.get()).isEqualTo(before);
    }
}
