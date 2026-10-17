package br.com.ctcore.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** CA8: saúde com banco real; contêiner próprio, porque o teste o derruba. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@DirtiesContext
class HealthIT {

    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16.4-alpine");

    static {
        DB.start();
        var admin = new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                DB.getJdbcUrl(), DB.getUsername(), DB.getPassword()));
        admin.execute("create role ctcore_app login password 'synthetic_runtime' nosuperuser nobypassrls");
        admin.execute("grant usage on schema public to ctcore_app");
        admin.execute("alter default privileges in schema public grant select, insert, update, delete on tables to ctcore_app");
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ctcore_app");
        registry.add("spring.datasource.password", () -> "synthetic_runtime");
        registry.add("spring.flyway.url", DB::getJdbcUrl);
        registry.add("spring.flyway.user", DB::getUsername);
        registry.add("spring.flyway.password", DB::getPassword);
    }

    @Autowired TestRestTemplate rest;
    @LocalManagementPort int mgmtPort;

    private ResponseEntity<String> get(String path) {
        return rest.getForEntity("http://localhost:" + mgmtPort + path, String.class);
    }

    @Test
    @DisplayName("CA8 / R6: com banco, health, liveness e readiness dão 200 UP sem detalhes; com o banco parado, readiness dá 503")
    void health() {
        for (String p : List.of("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness")) {
            ResponseEntity<String> r = get(p);
            assertThat(r.getStatusCode().value()).as(p).isEqualTo(200);
            assertThat(r.getBody()).contains("\"status\":\"UP\"").doesNotContain("components", "details");
        }
        DB.stop();
        assertThat(get("/actuator/health/readiness").getStatusCode().value()).isEqualTo(503);
    }
}
