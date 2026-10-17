package br.com.ctcore.audit;

import java.util.Objects;
import java.util.UUID;

/** Who acted (RN-AUD-02): the account and profile are optional (system, portal, site). */
public record AuditActor(Type type, UUID accountId, String profile) {
    public enum Type { ACCOUNT, SYSTEM, PORTAL, SITE, PLATFORM_SUPPORT, PLATFORM_OWNER }

    public AuditActor {
        Objects.requireNonNull(type);
    }

    public boolean platform() {
        return type == Type.PLATFORM_SUPPORT || type == Type.PLATFORM_OWNER;
    }
}
