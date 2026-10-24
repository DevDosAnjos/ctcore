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

    /** Gestor com conta (duas etapas ligadas), vínculo ativo e unidades reais, para os testes que dependem de quem recebe notificação (#84). */
    public static br.com.ctcore.identity.AccountPrincipal linked(java.util.UUID orgId, br.com.ctcore.identity.Role role, String emailPrefix, java.util.UUID... units) {
        java.util.UUID account = java.util.UUID.randomUUID(), link = java.util.UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Pessoa Sintética', 'ACTIVE')", account, emailPrefix + account + "@example.com");
        adminJdbc().update("insert into account_two_factor (account_id, secret_encrypted, enabled_at) values (?, 'x', now())", account);
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')", link, account, orgId, role.name());
        for (java.util.UUID unit : units) {
            adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", link, orgId, unit);
        }
        return new br.com.ctcore.identity.AccountPrincipal(account, java.util.UUID.randomUUID(), link, orgId, role, java.util.Set.of(units));
    }

    /** Desfaz {@link #linked}: notificações, preferências, vínculos e contas com o prefixo. */
    public static void cleanLinked(String emailPrefix) {
        String links = "select l.id from account_link l join account a on a.id = l.account_id where a.email like '" + emailPrefix + "%@example.com'";
        adminJdbc().update("delete from notification where link_id in (" + links + ")");
        adminJdbc().update("delete from notification_preference where link_id in (" + links + ")");
        adminJdbc().update("delete from account_link_unit where link_id in (" + links + ")");
        adminJdbc().update("delete from account_link where id in (" + links + ")");
        adminJdbc().update("delete from account where email like '" + emailPrefix + "%@example.com'");
    }
}
