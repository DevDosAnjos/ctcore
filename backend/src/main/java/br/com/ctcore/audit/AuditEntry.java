package br.com.ctcore.audit;

import java.util.Map;
import java.util.UUID;

/** One audited change. {@code before}/{@code after} are full maps; the service keeps only changed fields. */
public record AuditEntry(AuditActor actor, String action, String entityType, String entityId, UUID unitId,
        Map<String, Object> before, Map<String, Object> after, String reason, String ip) {
}
