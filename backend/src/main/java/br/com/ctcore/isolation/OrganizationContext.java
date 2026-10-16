package br.com.ctcore.isolation;

import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

public final class OrganizationContext implements AutoCloseable {
    private record Scope(UUID organizationId, UUID unitId) {}
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private OrganizationContext() {}

    public static OrganizationContext open(OrganizationPrincipal principal) {
        Objects.requireNonNull(principal);
        if (CURRENT.get() != null || principal.organizationId() == null
                || principal.authorizedUnitIds() == null
                || (principal.unitId() != null && !principal.authorizedUnitIds().contains(principal.unitId()))) {
            throw new AccessDeniedException("Invalid organization scope");
        }
        // Snapshot trusted claims so a mutable principal cannot change scope mid-request.
        CURRENT.set(new Scope(principal.organizationId(), principal.unitId()));
        return new OrganizationContext();
    }

    public static UUID organizationId() {
        var scope = CURRENT.get();
        return scope == null ? null : scope.organizationId();
    }

    public static boolean permits(UUID organizationId) {
        return Objects.requireNonNull(organizationId).equals(organizationId());
    }

    public static boolean permitsUnit(UUID organizationId, UUID unitId) {
        var scope = CURRENT.get();
        return permits(organizationId) && Objects.requireNonNull(unitId).equals(scope.unitId());
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
