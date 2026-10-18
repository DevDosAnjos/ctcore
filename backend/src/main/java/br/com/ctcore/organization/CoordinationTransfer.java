package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.CriticalAction;
import br.com.ctcore.identity.CriticalActionGuard;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-06 (D1..D5): o Coordenador passa a coordenação a um Administrador ativo; tudo ou nada, e-mail só depois do commit. */
@Service
public class CoordinationTransfer {
    static final String WORD = "TRANSFERIR";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final CriticalActionGuard guard;
    private final AuditService audit;
    private final EmailService email;

    public CoordinationTransfer(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions,
            CriticalActionGuard guard, AuditService audit, EmailService email) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.guard = guard;
        this.audit = audit;
        this.email = email;
    }

    /** D3: a palavra, sem diferenciar maiúsculas nem espaços nas pontas. */
    static void requireConfirmation(String confirmation) {
        if (confirmation == null || !WORD.equals(confirmation.strip().toUpperCase(Locale.ROOT))) {
            throw new ApiValidationException("confirmation", "digite " + WORD);
        }
    }

    private record Target(UUID linkId, UUID accountId, String email) {}

    public void transfer(AccountPrincipal who, String targetEmail, String confirmation, String reauthToken, String ip) {
        if (permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null) != Access.FULL) throw new AccessDeniedException("Permission denied");
        requireConfirmation(confirmation);
        String wanted = targetEmail == null ? "" : targetEmail.strip().toLowerCase(Locale.ROOT);
        Target target = transactions.execute(() -> find(who, wanted));
        if (target == null) throw new ApiValidationException("email", "informe um Administrador ativo da organização");
        guard.consume(who, CriticalAction.TRANSFER_COORDINATION, reauthToken);
        String organizationName = transactions.execute(() -> swap(who, target, ip));
        email.send(target.email(), EmailTemplate.COORDINATION_TRANSFERRED, organizationName);
    }

    private Target find(AccountPrincipal who, String wanted) {
        List<Target> rows = jdbc.sql("""
                select l.id, a.id as account_id, a.email from account_link l join account a on a.id = l.account_id
                where l.organization_id = :org and l.role = 'ADMINISTRATOR' and l.status = 'ACTIVE' and a.status = 'ACTIVE'
                and lower(btrim(a.email)) = :email and a.id <> :self""")
                .param("org", who.organizationId()).param("email", wanted).param("self", who.accountId())
                .query((rs, n) -> new Target(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("email"))).list();
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String swap(AccountPrincipal who, Target target, String ip) {
        UUID org = who.organizationId();
        String name = jdbc.sql("select name from organization where id = :org for update").param("org", org).query(String.class).single();
        int demoted = jdbc.sql("update account_link set role = 'ADMINISTRATOR' where organization_id = :org and account_id = :self "
                + "and role = 'COORDINATOR' and status = 'ACTIVE'").param("org", org).param("self", who.accountId()).update();
        if (demoted != 1) throw new AccessDeniedException("Not the active coordinator");
        int promoted = jdbc.sql("update account_link set role = 'COORDINATOR' where id = :id and organization_id = :org "
                + "and role = 'ADMINISTRATOR' and status = 'ACTIVE'").param("id", target.linkId()).param("org", org).update();
        if (promoted != 1) throw new ApiValidationException("email", "informe um Administrador ativo da organização");
        jdbc.sql("delete from account_link_unit where link_id = :id").param("id", target.linkId()).update();
        jdbc.sql("insert into account_link_unit (link_id, organization_id, unit_id) "
                + "select l.id, :org, u.id from account_link l cross join unit u where u.organization_id = :org "
                + "and l.organization_id = :org and l.account_id = :self and l.role = 'ADMINISTRATOR' on conflict do nothing")
                .param("org", org).param("self", who.accountId()).update();
        audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()),
                "organization.coordination_transferred", "organization", org.toString(), null,
                Map.of("coordinatorAccount", who.accountId().toString(), "administratorAccount", target.accountId().toString()),
                Map.of("coordinatorAccount", target.accountId().toString(), "administratorAccount", who.accountId().toString()), null, ip));
        return name;
    }
}
