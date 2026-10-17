package br.com.ctcore.identity;

import br.com.ctcore.isolation.OrganizationPrincipal;
import java.util.Set;
import java.util.UUID;

/** Identity of a session: the organization and units come from the active link, never from the request (RNF-05). */
public record AccountPrincipal(UUID accountId, UUID sessionId, UUID activeLinkId, UUID organizationId, Role role,
        Set<UUID> units) implements OrganizationPrincipal {
    @Override public UUID unitId() { return null; }

    @Override public Set<UUID> authorizedUnitIds() { return units; }
}
