package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class LinkService {
    private record Before(UUID accountId, Role role, String status) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions orgTx;
    private final ScopedAudit audit;
    private final SessionService sessions;
    private final AccountAudit accountAudit;
    private final Clock clock;

    public LinkService(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions orgTx, ScopedAudit audit,
            SessionService sessions, AccountAudit accountAudit, Clock clock) {
        this.jdbc = jdbc;
        this.orgTx = orgTx;
        this.audit = audit;
        this.sessions = sessions;
        this.accountAudit = accountAudit;
        this.clock = clock;
    }

    /**
     * Ends the link of the organization (RN-ACE-03); the account and its other links stay.
     * RN-ACE-20: ending the last management link of the account ends all its sessions.
     */
    public void end(AuditActor actor, UUID organizationId, UUID linkId) {
        Before before = orgTx.provision(organizationId, () -> {
            Before b = jdbc.sql("select account_id, role, status from account_link where id = :id and organization_id = :o and status <> 'ENDED'")
                    .param("id", linkId).param("o", organizationId)
                    .query((rs, n) -> new Before(rs.getObject(1, UUID.class), Role.valueOf(rs.getString(2)), rs.getString(3)))
                    .optional().orElseThrow(() -> new AccessDeniedException("Link not found or already ended"));
            jdbc.sql("update account_link set status = 'ENDED', ends_on = :d where id = :id")
                    .param("d", LocalDate.now(clock.withZone(ZoneOffset.UTC))).param("id", linkId).update();
            jdbc.sql("update account_session set active_link_id = null where active_link_id = :id").param("id", linkId).update();
            audit.record(organizationId, new AuditEntry(actor, "link.ended", "account_link", linkId.toString(), null,
                    Map.of("status", b.status()), Map.of("status", "ENDED"), null, null));
            return b;
        });
        if (before.status().equals("ACTIVE") && before.role().management() && sessions.endAllIfNoManagement(before.accountId())) {
            accountAudit.record(before.accountId(), actor, null, "account.sessions_ended");
        }
    }
}
