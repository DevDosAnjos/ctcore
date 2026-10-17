package br.com.ctcore.db;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.features.FeatureFlags;
import br.com.ctcore.isolation.Scopes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SeedDataIT extends PostgresIT {

    static final Path SEED_DIR = Path.of("..", "infra", "seed");
    static final String LIST = "select organization_id::text || feature_key || enabled from organization_feature order by 1";

    JdbcTemplate jdbc = adminJdbc();
    @Autowired FeatureFlags flags;

    @BeforeEach
    void clean() {
        // audit_log is append-only and shares the container with AuditServiceIT: test cleanup only.
        jdbc.execute("alter table audit_log disable trigger user");
        jdbc.update("delete from audit_log");
        jdbc.execute("alter table audit_log enable trigger user");
        for (String table : new String[]{"email_message", "account_device", "login_attempt", "account_session", "password_reset", "account_invite",
                "account_link_unit", "account_link", "account"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from organization_feature");
        jdbc.update("delete from unit_settings");
        jdbc.update("delete from unit");
        jdbc.update("delete from organization_settings");
        jdbc.update("delete from organization_limits");
        jdbc.update("delete from organization");
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
    @DisplayName("CA1 / RN-PNL-07: toda chave do seed é aceita pelo FeatureFlags e lida com o valor gravado")
    void readableByApplication() throws IOException {
        apply(jdbc, SEED_DIR);
        List<Map<String, Object>> rows = jdbc.queryForList("select organization_id, feature_key, enabled from organization_feature");
        assertThat(rows).isNotEmpty();
        for (Map<String, Object> r : rows) {
            String key = (String) r.get("feature_key");
            try (var scope = Scopes.open((UUID) r.get("organization_id"), null)) {
                assertThat(flags.isEnabled((UUID) r.get("organization_id"), key)).as(key).isEqualTo(r.get("enabled"));
            }
        }
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
        jdbc.update("update organization_feature set enabled = not enabled where organization_id = md5('ctcore-seed-org-1')::uuid and feature_key = 'online-payments'");
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
    @DisplayName("RN-ORG-04/16: deterministic pilot, complete tenants and preserved settings on seed reapplication")
    void organizationSettingsAreCompleteAndPreserved() throws IOException {
        apply(jdbc, SEED_DIR);
        for (String table : new String[]{"organization", "unit", "organization_settings", "unit_settings", "organization_limits"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Integer.class)).as(table).isEqualTo(3);
        }
        assertThat(jdbc.queryForObject("select name from organization where id = md5('ctcore-seed-org-1')::uuid", String.class))
                .isEqualTo("Academia Piloto Fictícia");
        assertThat(jdbc.queryForObject("select count(*) from unit u join unit_settings s on (s.organization_id, s.unit_id) = (u.organization_id, u.id) join organization_settings o on o.organization_id = u.organization_id join organization_limits l on l.organization_id = u.organization_id", Integer.class))
                .isEqualTo(3);
        jdbc.update("update organization_settings set deactivation_days = 60, trial_limit = 3 where organization_id = md5('ctcore-seed-org-1')::uuid");
        jdbc.update("update unit_settings set due_days = '{2,28}', pix_key = 'synthetic-key' where organization_id = md5('ctcore-seed-org-1')::uuid");
        jdbc.update("update organization_limits set students = 1 where organization_id = md5('ctcore-seed-org-1')::uuid");
        Map<String, List<String>> before = new java.util.HashMap<>();
        for (String table : new String[]{"organization", "unit", "organization_settings", "unit_settings", "organization_limits"}) {
            before.put(table, jdbc.queryForList("select row_to_json(t)::text from " + table + " t order by 1", String.class));
        }
        apply(jdbc, SEED_DIR);
        for (var entry : before.entrySet()) {
            assertThat(jdbc.queryForList("select row_to_json(t)::text from " + entry.getKey() + " t order by 1", String.class))
                    .as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("RN-ACE-01: contas fictícias com vínculos, senha Argon2 que confere e reaplicação sem duplicar")
    void accountsSeed() throws IOException {
        apply(jdbc, SEED_DIR);
        apply(jdbc, SEED_DIR);
        assertThat(jdbc.queryForObject("select count(*) from account", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from account_link", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from account_link_unit", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from account where password_hash is null", Integer.class)).isEqualTo(1);
        String hash = jdbc.queryForObject("select password_hash from account where email = 'coordenador@ficticia-1.example'", String.class);
        assertThat(new br.com.ctcore.config.SecurityConfig().passwordEncoder().matches("SenhaFicticia1", hash)).isTrue();
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
