package br.com.ctcore.features;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Recursos ligados por organização (RN-PNL-07). Sem linha = desligado. Sem cache: a mudança vale na próxima consulta. */
@Service
public class FeatureFlags {

    private static final Pattern KEY = Pattern.compile("[a-z][a-z0-9-]{0,63}");

    private final JdbcClient jdbc;
    private final Clock clock;

    public FeatureFlags(@Lazy JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean isEnabled(UUID organizationId, String key) {
        validate(organizationId, key);
        return jdbc.sql("select enabled from organization_feature where organization_id = :o and feature_key = :k")
                .param("o", organizationId).param("k", key)
                .query(Boolean.class).optional().orElse(false);
    }

    public void setEnabled(UUID organizationId, String key, boolean enabled) {
        validate(organizationId, key);
        jdbc.sql("""
                insert into organization_feature (organization_id, feature_key, enabled, updated_at)
                values (:o, :k, :e, :t)
                on conflict (organization_id, feature_key)
                do update set enabled = excluded.enabled, updated_at = excluded.updated_at""")
                .param("o", organizationId).param("k", key).param("e", enabled)
                .param("t", OffsetDateTime.now(clock))
                .update();
    }

    static void validate(UUID organizationId, String key) {
        if (organizationId == null) throw new IllegalArgumentException("organizationId is required");
        if (key == null || !KEY.matcher(key).matches()) throw new IllegalArgumentException("invalid feature key");
    }
}
