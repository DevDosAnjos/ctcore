package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.Cookie;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

@Import({AuthBase.Config.class, AuthBase.OrgProbe.class})
class SessionListIT extends AuthBase {
    static final String URL = "/api/v1/auth/sessions";

    int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    UUID idOf(Cookie c) {
        return admin.queryForObject("select id from account_session where token_hash = ?", UUID.class, Codes.hash(c.getValue()));
    }

    @Test
    @DisplayName("RF-ACE-04: CA10: lista só as sessões vivas da conta, marca a atual e não expõe token, hash nem IP")
    void list() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        link(account("bia@x.com"), org, "TEACHER", "ACTIVE");
        Cookie a1 = sessionOf("ana@x.com"), a2 = sessionOf("ana@x.com"), a3 = sessionOf("ana@x.com");
        sessionOf("bia@x.com");
        admin.update("update account_session set device = 'Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537' where token_hash = ?", Codes.hash(a1.getValue()));
        admin.update("update account_session set expires_at = ? where token_hash = ?", clock.now.minusSeconds(1).atOffset(ZoneOffset.UTC), Codes.hash(a3.getValue()));

        MvcResult r = get(URL, a2).andReturn();
        assertThat(status(r)).isEqualTo(200);
        JsonNode list = body(r);
        assertThat(list).hasSize(2);
        assertThat(list.findValues("current").stream().filter(JsonNode::asBoolean).count()).isEqualTo(1);
        assertThat(list.toString()).contains("Chrome em Windows").doesNotContain(a1.getValue(), a2.getValue(), "token", "hash", "127.0.0.1");
        assertThat(list.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id", "device", "city", "createdAt", "lastUsedAt", "current");
    }

    @Test
    @DisplayName("RF-ACE-04: CA11: encerra outra sessão da conta (204), a atual é 409, sessão de outra conta ou inexistente é 404")
    void endOne() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        link(account("bia@x.com"), org, "TEACHER", "ACTIVE");
        Cookie a1 = sessionOf("ana@x.com"), a2 = sessionOf("ana@x.com"), b = sessionOf("bia@x.com");

        assertThat(shape(delete(URL + "/" + idOf(a2), a2).andReturn())).startsWith("409|SESSION_CURRENT");
        assertThat(status(get("/api/v1/auth/session", a2).andReturn())).isEqualTo(200);
        assertThat(status(delete(URL + "/" + idOf(b), a2).andReturn())).isEqualTo(404);
        assertThat(status(delete(URL + "/" + UUID.randomUUID(), a2).andReturn())).isEqualTo(404);
        assertThat(status(get("/api/v1/auth/session", b).andReturn())).isEqualTo(200);

        assertThat(status(delete(URL + "/" + idOf(a1), a2).andReturn())).isEqualTo(204);
        assertThat(status(get("/api/v1/auth/session", a1).andReturn())).isEqualTo(401);
        assertThat(status(get("/api/v1/auth/session", a2).andReturn())).isEqualTo(200);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.session_ended'", org)).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-ACE-04: CA12, CA13: encerrar as outras mantém a atual e não toca nas sessões de outras contas")
    void endOthers() throws Exception {
        UUID org = org();
        link(account("ana@x.com"), org, "ADMINISTRATOR", "ACTIVE");
        link(account("bia@x.com"), org, "TEACHER", "ACTIVE");
        Cookie a1 = sessionOf("ana@x.com"), a2 = sessionOf("ana@x.com"), a3 = sessionOf("ana@x.com"), b = sessionOf("bia@x.com");

        assertThat(status(delete(URL, a2).andReturn())).isEqualTo(204);
        assertThat(status(get("/api/v1/auth/session", a1).andReturn())).isEqualTo(401);
        assertThat(status(get("/api/v1/auth/session", a3).andReturn())).isEqualTo(401);
        assertThat(status(get("/api/v1/auth/session", a2).andReturn())).isEqualTo(200);
        assertThat(status(get("/api/v1/auth/session", b).andReturn())).isEqualTo(200);
        assertThat(body(get(URL, a2).andReturn())).hasSize(1);
        assertThat(count("select count(*) from audit_log where organization_id = ? and action = 'account.sessions_ended' and ip is not null", org)).isEqualTo(1);
    }
}
