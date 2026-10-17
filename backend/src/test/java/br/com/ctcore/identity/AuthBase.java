package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.integration.ChallengeVerifier;
import br.com.ctcore.integration.Outbox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shared data and HTTP helpers for the /auth route tests: real server, real database, controllable clock. */
@AutoConfigureMockMvc
abstract class AuthBase extends PostgresIT {
    static final String PASSWORD = "Abcdefg1";

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        AccountIT.MutableClock testClock() {
            return new AccountIT.MutableClock();
        }
    }

    /** A route of an organization, to see which organization the session reaches. TestComponent keeps the scan of other tests away from it. */
    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class OrgProbe {
        @GetMapping("/api/v1/probe-org")
        Map<String, Object> organization() {
            return Map.of("organizationId", String.valueOf(br.com.ctcore.isolation.OrganizationContext.organizationId()));
        }
    }

    @MockitoBean ChallengeVerifier challenge;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired Outbox outbox;
    @Autowired AccountIT.MutableClock clock;

    JdbcTemplate admin = adminJdbc();
    List<UUID> orgs = new ArrayList<>();
    String passwordHash;

    @BeforeEach
    void baseSetup() {
        Mockito.when(challenge.verify(Mockito.any())).thenReturn(true);
        clock.now = java.time.Instant.parse("2026-10-17T12:00:00Z");
        outbox.clear();
        passwordHash = encoder.encode(PASSWORD);
    }

    @AfterEach
    void baseClean() {
        for (String t : new String[]{"login_attempt", "account_session", "password_reset", "account_invite", "account_link_unit", "account_link"}) {
            admin.update("delete from " + t);
        }
        admin.execute("alter table audit_log disable trigger user");
        for (UUID org : orgs) {
            admin.update("delete from audit_log where organization_id = ?", org);
        }
        admin.execute("alter table audit_log enable trigger user");
        admin.update("delete from account");
        for (UUID org : orgs) {
            admin.update("delete from unit where organization_id = ?", org);
            admin.update("delete from organization where id = ?", org);
        }
    }

    UUID org() {
        UUID id = UUID.randomUUID();
        admin.update("insert into organization (id, name) values (?, 'Org')", id);
        orgs.add(id);
        return id;
    }

    UUID unit(UUID org) {
        UUID id = UUID.randomUUID();
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", id, org, "u-" + id);
        return id;
    }

    /** Active account with the shared test password. */
    UUID account(String email) {
        UUID id = UUID.randomUUID();
        admin.update("insert into account (id, email, name, password_hash, status) values (?, ?, 'Nome', ?, 'ACTIVE')", id, email, passwordHash);
        return id;
    }

    UUID link(UUID account, UUID org, String role, String status) {
        UUID id = UUID.randomUUID();
        admin.update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, ?)",
                id, account, org, role, status);
        return id;
    }

    // ---- HTTP ----
    ResultActions send(MockHttpServletRequestBuilder request, Object body, Cookie session) throws Exception {
        request.secure(true).with(csrf());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (session != null) request.cookie(session);
        return mvc.perform(request);
    }

    ResultActions post(String url, Object body, Cookie session) throws Exception {
        return send(MockMvcRequestBuilders.post(url), body, session);
    }

    ResultActions get(String url, Cookie session) throws Exception {
        return send(MockMvcRequestBuilders.get(url), null, session);
    }

    ResultActions put(String url, Object body, Cookie session) throws Exception {
        return send(MockMvcRequestBuilders.put(url), body, session);
    }

    MvcResult login(String email, String password) throws Exception {
        return post("/api/v1/auth/login", Map.of("email", email, "password", password, "challengeToken", "t"), null).andReturn();
    }

    Cookie sessionOf(String email) throws Exception {
        MvcResult r = login(email, PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r.getResponse().getCookie("SESSION");
    }

    JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    /** Status plus error code and message, ignoring the trace id: what a caller can tell apart. */
    String shape(MvcResult r) throws Exception {
        JsonNode b = body(r);
        return r.getResponse().getStatus() + "|" + b.path("code").asText() + "|" + b.path("message").asText() + "|" + b.path("fields");
    }

    long count(String sql, Object... args) {
        return admin.queryForObject(sql, Long.class, args);
    }
}
