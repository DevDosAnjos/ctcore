package br.com.ctcore.audit;

import br.com.ctcore.isolation.OrganizationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The only way to write {@code audit_log} (RN-AUD-02). Joins the caller's transaction; never opens one. */
@Service
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public AuditService(@Lazy JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry e) {
        Objects.requireNonNull(e);
        var org = OrganizationContext.organizationId();
        if (org == null && !e.actor().platform()) {
            throw new AccessDeniedException("Audit requires an organization scope");
        }
        if (e.unitId() != null && (org == null || jdbc.sql("select count(*) from unit where id = :u and organization_id = :o")
                .param("u", e.unitId()).param("o", org).query(Integer.class).single() == 0)) {
            throw new AccessDeniedException("Unit does not belong to the organization");
        }
        var values = AuditSanitizer.diff(e.before(), e.after());
        jdbc.sql("""
                insert into audit_log (organization_id, unit_id, actor_type, actor_id, actor_profile, action,
                    entity_type, entity_id, before_data, after_data, reason, ip, occurred_at)
                values (:org, :unit, :type, :actor, :profile, :action, :entity, :id,
                    cast(:before as jsonb), cast(:after as jsonb), :reason, :ip, :at)""")
                .param("org", org).param("unit", e.unitId()).param("type", e.actor().type().name())
                .param("actor", e.actor().accountId()).param("profile", e.actor().profile())
                .param("action", e.action()).param("entity", e.entityType()).param("id", e.entityId())
                .param("before", write(values.get(0))).param("after", write(values.get(1)))
                .param("reason", e.reason()).param("ip", e.ip())
                .param("at", OffsetDateTime.now(clock)).update();
        LOG.info("audit action={} entity={} id={}", e.action(), e.entityType(), e.entityId());
    }

    private String write(Map<String, Object> m) {
        try {
            return m == null ? null : json.writeValueAsString(m);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit values are not serializable", ex);
        }
    }
}
