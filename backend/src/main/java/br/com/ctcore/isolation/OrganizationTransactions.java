package br.com.ctcore.isolation;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Lazy
public class OrganizationTransactions {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    public OrganizationTransactions(@Lazy JdbcClient jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public <T> T execute(Supplier<T> work) {
        return scoped(OrganizationContext.organizationId(), work);
    }

    /** Internal provisioning only: never accepts another organization under an existing scope. */
    public <T> T provision(UUID organizationId, Supplier<T> work) {
        if (OrganizationContext.organizationId() != null && !OrganizationContext.permits(organizationId)) {
            throw new AccessDeniedException("Organization scope mismatch");
        }
        return scoped(organizationId, work);
    }

    private <T> T scoped(UUID organizationId, Supplier<T> work) {
        return transactions.execute(status -> {
            String requested = organizationId == null ? "" : organizationId.toString();
            var previous = jdbc.sql("select current_setting('app.scope_initialized', true) as initialized, "
                    + "coalesce(current_setting('app.organization_id', true), '') as organization").query().singleRow();
            if ("true".equals(previous.get("initialized")) && !requested.equals(previous.get("organization"))) {
                throw new AccessDeniedException("Cannot change organization inside a transaction");
            }
            jdbc.sql("select set_config('app.organization_id', :org, true), "
                    + "set_config('app.scope_initialized', 'true', true)")
                    .param("org", requested).query().singleRow();
            return work.get();
        });
    }
}
