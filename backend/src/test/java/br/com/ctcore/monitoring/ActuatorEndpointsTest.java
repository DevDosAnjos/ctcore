package br.com.ctcore.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

/** Aplicação inteira (sem banco), porta de gestão separada e aleatória. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ctcore.scheduler.enabled=false",
        "management.server.port=0",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"})
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
@Import(TestProbes.class)
class ActuatorEndpointsTest {

    @Autowired TestRestTemplate rest;
    @Autowired OperationalMetrics metrics;
    @LocalManagementPort int mgmtPort;

    private ResponseEntity<String> mgmt(String path) {
        return rest.getForEntity("http://localhost:" + mgmtPort + path, String.class);
    }

    @Test
    @DisplayName("CA11 / R9: só health e prometheus; env, beans, heapdump, metrics e loggers dão 404")
    void onlyHealthAndPrometheus() {
        for (String p : List.of("/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics", "/actuator/loggers")) {
            assertThat(mgmt(p).getStatusCode().value()).as(p).isEqualTo(404);
        }
        for (String p : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            ResponseEntity<String> r = mgmt(p);
            assertThat(r.getStatusCode().value()).as(p).isEqualTo(200);
            assertThat(r.getBody()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
        }
    }

    @Test
    @DisplayName("CA12 / R10: porta principal não atende o actuator; /api/v1 exige autenticação")
    void mainPortClosed() {
        org.springframework.http.HttpHeaders https = new org.springframework.http.HttpHeaders();
        https.add("X-Forwarded-Proto", "https");
        assertThat(rest.exchange("/actuator/health", org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(https), String.class).getStatusCode().value()).isIn(401, 404);
        assertThat(rest.exchange("/api/v1/x", org.springframework.http.HttpMethod.GET, new org.springframework.http.HttpEntity<>(https), String.class).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA9, CA10, CA12: prometheus traz http_server_requests com 200, 500 e buckets; métricas de negócio; sem dado pessoal")
    void prometheus() {
        rest.getForEntity("/probe/ok", String.class);
        rest.getForEntity("/probe/fail", String.class);
        String before = mgmt("/actuator/prometheus").getBody();
        assertThat(before).contains("http_server_requests_seconds_bucket", "status=\"200\"", "status=\"500\"",
                "ctcore_email_queue_pending 0.0", "ctcore_webhook_failures_total 0.0");
        metrics.webhookFailed();
        metrics.emailQueue(3);
        String after = mgmt("/actuator/prometheus").getBody();
        assertThat(after).contains("ctcore_email_queue_pending 3.0", "ctcore_webhook_failures_total 1.0");
        assertThat(after).doesNotContainPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                .doesNotContainPattern("[\\w.]+@[\\w.]+\\.\\w+").doesNotContainPattern("\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}");
    }
}
