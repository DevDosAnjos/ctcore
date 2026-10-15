package br.com.ctcore.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class MigrationIT extends PostgresIT {

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;

    @Test
    @DisplayName("CA2: a V1 foi aplicada com sucesso ao subir a aplicação")
    void v1Applied() {
        Boolean success = jdbc.queryForObject(
                "select success from flyway_schema_history where version = '1'", Boolean.class);
        assertThat(success).isTrue();
    }

    @Test
    @DisplayName("RNF-30 / CA3: migração já aplicada com conteúdo alterado faz a subida falhar por checksum")
    void changedMigrationFailsValidation() {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("checksum_check").createSchemas(true)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load();
        flyway.migrate();
        jdbc.update("update checksum_check.flyway_schema_history set checksum = checksum + 1 where version = '1'");

        assertThatThrownBy(flyway::migrate).isInstanceOf(FlywayValidateException.class);
    }

    @Test
    @DisplayName("CA4: id uuid gerado pelo banco volta como UUID, diferente a cada linha")
    void uuidGeneratedByDatabase() {
        jdbc.execute("create table it_uuid (id uuid primary key default gen_random_uuid(), n int)");
        jdbc.update("insert into it_uuid (n) values (1), (2)");

        List<UUID> ids = jdbc.queryForList("select id from it_uuid", UUID.class);

        assertThat(ids).hasSize(2).doesNotHaveDuplicates().doesNotContainNull();
    }

    @Test
    @DisplayName("CA5: timestamptz mantém o instante, JSON sai com Z, JVM e Clock em UTC")
    void utcEverywhere() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        jdbc.execute("create table it_time (at timestamptz not null)");
        jdbc.update("insert into it_time (at) values (?)", OffsetDateTime.ofInstant(now, ZoneOffset.ofHours(-3)));

        OffsetDateTime read = jdbc.queryForObject("select at from it_time", OffsetDateTime.class);

        assertThat(read.toInstant()).isEqualTo(now);
        assertThat(mapper.writeValueAsString(now)).endsWith("Z\"");
        assertThat(ZoneId.systemDefault().getId()).isIn("UTC", "Z", "Etc/UTC");
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
