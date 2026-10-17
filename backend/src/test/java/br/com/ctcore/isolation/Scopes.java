package br.com.ctcore.isolation;

import java.util.Set;
import java.util.UUID;

/** Synthetic trusted identity used only in tests. */
public record Scopes(UUID organizationId, UUID unitId, Set<UUID> authorizedUnitIds) implements OrganizationPrincipal {
    public static OrganizationContext open(UUID organization, UUID unit) {
        return OrganizationContext.open(new Scopes(organization, unit, unit == null ? Set.of() : Set.of(unit)));
    }
}
