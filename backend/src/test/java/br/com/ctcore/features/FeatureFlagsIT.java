package br.com.ctcore.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FeatureFlagsIT extends PostgresIT {

    @Autowired FeatureFlags flags;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("CA5: V2 aplicada com sucesso e tabelas existem")
    void v2Applied() {
        assertThat(jdbc.queryForObject(
                "select success from flyway_schema_history where version = '2'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name in ('shedlock','organization_feature') and table_schema = current_schema()",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA6 / RN-PNL-07: sem linha, o recurso está desligado")
    void defaultOff() {
        assertThat(flags.isEnabled(UUID.randomUUID(), "new-feature")).isFalse();
    }

    @Test
    @DisplayName("CA7 / RN-PNL-07: liga e desliga valendo na próxima consulta, sem duplicar linha e atualizando a data")
    void toggle() throws Exception {
        UUID org = UUID.randomUUID();
        flags.setEnabled(org, "toggle", true);
        assertThat(flags.isEnabled(org, "toggle")).isTrue();
        OffsetDateTime first = updatedAt(org);

        Thread.sleep(10);
        flags.setEnabled(org, "toggle", false);
        flags.setEnabled(org, "toggle", false);

        assertThat(flags.isEnabled(org, "toggle")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from organization_feature where organization_id = ?",
                Integer.class, org)).isEqualTo(1);
        assertThat(updatedAt(org)).isAfter(first);
    }

    @Test
    @DisplayName("CA8: ligar para a organização A não afeta B nem outra chave")
    void isolation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        flags.setEnabled(a, "iso", true);
        assertThat(flags.isEnabled(a, "iso")).isTrue();
        assertThat(flags.isEnabled(b, "iso")).isFalse();
        assertThat(flags.isEnabled(a, "other")).isFalse();
    }

    @Test
    @DisplayName("CA9: chave inválida ou organização nula é recusada e nada é gravado")
    void invalid() {
        UUID org = UUID.randomUUID();
        for (String key : new String[] {"Invalid Key", "", null, "a".repeat(65), "1abc"}) {
            assertThatThrownBy(() -> flags.setEnabled(org, key, true)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> flags.isEnabled(org, key)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> flags.setEnabled(null, "ok", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> flags.isEnabled(null, "ok")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from organization_feature where organization_id = ?",
                Integer.class, org)).isZero();
        flags.setEnabled(org, "a".repeat(64), true); // 64 ainda vale
    }

    private OffsetDateTime updatedAt(UUID org) {
        return jdbc.queryForObject("select updated_at from organization_feature where organization_id = ?",
                OffsetDateTime.class, org);
    }
}
