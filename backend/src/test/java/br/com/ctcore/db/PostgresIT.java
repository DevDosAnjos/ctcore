package br.com.ctcore.db;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base dos testes de integração: PostgreSQL 16 real (Testcontainers), um contêiner para toda a execução. */
@SpringBootTest
public abstract class PostgresIT {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine");

    static {
        POSTGRES.start();
        adminJdbc().execute("create role ctcore_app login password 'synthetic_runtime' nosuperuser nobypassrls");
        adminJdbc().execute("grant usage on schema public to ctcore_app");
        adminJdbc().execute("alter default privileges in schema public grant select, insert, update, delete on tables to ctcore_app");
    }

    public static org.springframework.jdbc.core.JdbcTemplate adminJdbc() {
        return new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ctcore_app");
        registry.add("spring.datasource.password", () -> "synthetic_runtime");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "1");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }
}
