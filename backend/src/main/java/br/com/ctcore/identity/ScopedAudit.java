package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** Audits an identity event in the scope of the organization it belongs to, joining the caller's transaction if any. */
@Component
public class ScopedAudit {
    private final AuditService audit;
    private final OrganizationTransactions transactions;

    public ScopedAudit(AuditService audit, @Lazy OrganizationTransactions transactions) {
        this.audit = audit;
        this.transactions = transactions;
    }

    public void record(UUID organizationId, AuditEntry entry) {
        if (OrganizationContext.permits(organizationId)) {
            write(entry);
            return;
        }
        try (var ignored = OrganizationContext.open(system(organizationId))) {
            write(entry);
        }
    }

    private void write(AuditEntry entry) {
        transactions.execute(() -> {
            audit.record(entry);
            return null;
        });
    }

    private static OrganizationPrincipal system(UUID organizationId) {
        return new OrganizationPrincipal() {
            @Override public UUID organizationId() { return organizationId; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        };
    }
}
