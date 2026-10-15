package br.com.ctcore.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SeedDataIT extends PostgresIT {

    static final Path SEED_DIR = Path.of("..", "infra", "seed");
    static final String LIST = "select organization_id::text || feature_key || enabled from organization_feature order by 1";

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from organization_feature");
    }

    /** Aplica os .sql do diretório em ordem do nome, como o seed.sh. */
    static void apply(JdbcTemplate jdbc, Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
        assertThat(files).as("arquivos de seed em " + dir.toAbsolutePath()).isNotEmpty();
        for (Path f : files) {
            jdbc.execute(Files.readString(f, StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("CA1: 3 organizações fictícias com recursos ligados e desligados")
    void createsOrganizations() throws IOException {
        apply(jdbc, SEED_DIR);
        assertThat(jdbc.queryForObject("select count(distinct organization_id) from organization_feature", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from organization_feature where enabled", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from organization_feature where not enabled", Integer.class)).isPositive();
        assertThat(jdbc.queryForObject(
                "select count(*) from organization_feature where organization_id = md5('ctcore-seed-org-1')::uuid", Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("CA2: segunda execução não muda contagem nem conteúdo")
    void idempotent() throws IOException {
        apply(jdbc, SEED_DIR);
        List<String> first = jdbc.queryForList(LIST, String.class);
        apply(jdbc, SEED_DIR);
        assertThat(jdbc.queryForList(LIST, String.class)).isEqualTo(first);
    }

    @Test
    @DisplayName("CA3: valor alterado depois do primeiro run é mantido")
    void keepsChanges() throws IOException {
        apply(jdbc, SEED_DIR);
        jdbc.update("update organization_feature set enabled = not enabled where organization_id = md5('ctcore-seed-org-1')::uuid and feature_key = 'online_payments'");
        List<String> changed = jdbc.queryForList(LIST, String.class);
        apply(jdbc, SEED_DIR);
        assertThat(jdbc.queryForList(LIST, String.class)).isEqualTo(changed);
    }

    @Test
    @DisplayName("CA4: banco limpo e reaplicado dá os mesmos identificadores e valores")
    void reproducible() throws IOException {
        apply(jdbc, SEED_DIR);
        List<String> first = jdbc.queryForList(LIST, String.class);
        jdbc.update("delete from organization_feature");
        apply(jdbc, SEED_DIR);
        assertThat(jdbc.queryForList(LIST, String.class)).isEqualTo(first);
    }

    @Test
    @DisplayName("CA6: arquivos aplicados em ordem do nome")
    void order(@org.junit.jupiter.api.io.TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("02-b.sql"),
                "update organization_feature set enabled = false where feature_key = 'x';");
        Files.writeString(tmp.resolve("01-a.sql"),
                "insert into organization_feature (organization_id, feature_key, enabled) values (md5('t')::uuid, 'x', true);");
        apply(jdbc, tmp);
        assertThat(jdbc.queryForObject("select enabled from organization_feature where feature_key = 'x'", Boolean.class)).isFalse();
    }
}
