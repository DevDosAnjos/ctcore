package br.com.ctcore.organization;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-ORG-08 (D1): última alteração de configuração, tirada do último registro de auditoria da entidade. */
public record LastChange(OffsetDateTime at, String by) {
    static final List<String> ORGANIZATION_ACTIONS = List.of("organization.update", "organization.logo.update", "organization.logo.remove");
    static final List<String> UNIT_ACTIONS = List.of("unit.update", "unit.short_name.update", "unit.deactivated");

    /** Chamar dentro da transação com escopo da organização (RLS); sem registro devolve nulo. */
    static LastChange find(JdbcClient jdbc, UUID organizationId, String entityType, UUID entityId, List<String> actions) {
        return jdbc.sql("""
                select a.occurred_at, coalesce(nullif(btrim(c.name), ''), c.email)
                from audit_log a left join account c on c.id = a.actor_id
                where a.organization_id = :org and a.entity_type = :type and a.entity_id = :entity and a.action in (:actions)
                order by a.occurred_at desc limit 1""")
                .param("org", organizationId).param("type", entityType).param("entity", entityId.toString()).param("actions", actions)
                .query((rs, i) -> new LastChange(rs.getObject(1, OffsetDateTime.class), rs.getString(2))).optional().orElse(null);
    }
}
