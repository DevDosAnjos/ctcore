package br.com.ctcore.isolation;

import java.util.Set;
import java.util.UUID;

/** Implemented only by server-authenticated identities, never from request parameters. */
public interface OrganizationPrincipal {
    UUID organizationId();
    UUID unitId();
    Set<UUID> authorizedUnitIds();
}
