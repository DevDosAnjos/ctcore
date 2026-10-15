package br.com.ctcore.logging;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.monitoring.TestProbes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Aplicação inteira (sem banco): o que sai no console é JSON, com traceId e sem dado sensível. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ctcore.scheduler.enabled=false",
        "management.server.port=0",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
@Import(TestProbes.class)
@ExtendWith(OutputCaptureExtension.class)
class LogOutputTest {

    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired TestRestTemplate rest;
    @Autowired Environment env;

    private static List<JsonNode> jsonLines(CapturedOutput out) throws Exception {
        List<JsonNode> lines = new ArrayList<>();
        for (String l : out.getOut().split("\\R")) {
            if (l.startsWith("{")) {
                lines.add(JSON.readTree(l));
            }
        }
        return lines;
    }

    @Test
    @DisplayName("CA1, CA4: linha é JSON com campos básicos; traceId igual ao X-Trace-Id e não vaza para a próxima requisição")
    void jsonWithTraceId(CapturedOutput out) throws Exception {
        ResponseEntity<String> first = rest.getForEntity("/probe/ok", String.class);
        ResponseEntity<String> second = rest.getForEntity("/probe/ok", String.class);
        List<JsonNode> lines = jsonLines(out).stream().filter(n -> "probe".equals(n.path("logger_name").asText())).toList();
        assertThat(lines).hasSize(4);
        JsonNode a = lines.get(0);
        assertThat(a.has("@timestamp")).isTrue();
        assertThat(a.has("level")).isTrue();
        assertThat(a.has("logger_name")).isTrue();
        assertThat(a.has("message")).isTrue();
        assertThat(a.path("traceId").asText()).isEqualTo(first.getHeaders().getFirst("X-Trace-Id"));
        assertThat(lines.get(2).path("traceId").asText()).isEqualTo(second.getHeaders().getFirst("X-Trace-Id"));
        assertThat(first.getHeaders().getFirst("X-Trace-Id")).isNotEqualTo(second.getHeaders().getFirst("X-Trace-Id"));
    }

    @Test
    @DisplayName("CA2, CA3: senha logada pela aplicação sai mascarada; id e UUID intactos")
    void masksInOutput(CapturedOutput out) {
        rest.getForEntity("/probe/ok", String.class);
        assertThat(out.getOut()).doesNotContain("SenhaSecreta123")
                .contains("id=12345", "550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    @DisplayName("CA5, CA2: erro 500 tem o mesmo traceId no corpo, no cabeçalho e no log; pilha sem dado sensível")
    void errorTrace(CapturedOutput out) throws Exception {
        ResponseEntity<String> res = rest.getForEntity("/probe/fail?secret=QuerySegredo", String.class);
        String header = res.getHeaders().getFirst("X-Trace-Id");
        assertThat(JSON.readTree(res.getBody()).path("traceId").asText()).isEqualTo(header);
        JsonNode err = jsonLines(out).stream()
                .filter(n -> "ERROR".equals(n.path("level").asText()) && header.equals(n.path("traceId").asText()))
                .findFirst().orElseThrow();
        assertThat(err.toString()).contains("falhou").doesNotContain("TokenSecreto999", "123.456.789-09");
    }

    @Test
    @DisplayName("CA6 / RNF-11: Authorization, Cookie e query com segredo não aparecem em log (401, 403, 500)")
    void noRequestDataInLogs(CapturedOutput out) {
        HttpHeaders h = new HttpHeaders();
        h.add("X-Forwarded-Proto", "https");
        h.add("Authorization", "Bearer HeaderSegredo777");
        h.add("Cookie", "JSESSIONID=CookieSegredo888");
        HttpEntity<String> req = new HttpEntity<>("corpo=CorpoSegredo666", h);
        assertThat(rest.exchange("/api/v1/x?secret=QuerySegredo", HttpMethod.GET, req, String.class).getStatusCode().value()).isEqualTo(401);
        assertThat(rest.exchange("/api/v1/x?secret=QuerySegredo", HttpMethod.POST, req, String.class).getStatusCode().value()).isEqualTo(403);
        assertThat(rest.exchange("/probe/fail?secret=QuerySegredo", HttpMethod.GET, req, String.class).getStatusCode().value()).isEqualTo(500);
        assertThat(out.getOut()).doesNotContain("HeaderSegredo777", "CookieSegredo888", "QuerySegredo", "CorpoSegredo666");
    }

    @Test
    @DisplayName("CA7 / RNF-11: arquivo de log em JSON, rotação diária e guarda de 30 dias")
    void fileConfig() {
        assertThat(env.getProperty("logging.file.name")).endsWith("ctcore.log");
        assertThat(env.getProperty("logging.logback.rollingpolicy.file-name-pattern")).contains("%d{yyyy-MM-dd}");
        assertThat(env.getProperty("logging.logback.rollingpolicy.max-history")).isEqualTo("30");
        assertThat(env.getProperty("logging.structured.format.file")).isEqualTo("logstash");
        assertThat(env.getProperty("logging.structured.format.console")).isEqualTo("logstash");
    }
}
