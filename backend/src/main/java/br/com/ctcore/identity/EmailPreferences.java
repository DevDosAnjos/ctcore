package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.IdentityException.Code;
import java.util.Map;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RN-COM-13: the student or guardian turns the academy announcements on or off, per organization (the active link of the session). */
@Service
public class EmailPreferences {
    public record View(boolean announcements) {}

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final ScopedAudit audit;

    public EmailPreferences(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, ScopedAudit audit) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.audit = audit;
    }

    private static void portalOnly(AccountPrincipal who) {
        if (who.role() == null || who.role().management() || who.activeLinkId() == null) {
            throw new AccessDeniedException("Portal link required");
        }
    }

    public View get(AccountPrincipal who) {
        portalOnly(who);
        return new View(read(who));
    }

    private boolean read(AccountPrincipal who) {
        return accountTx.forAccount(who.accountId(), () -> jdbc.sql("select announcement_email from account_link where id = :l and account_id = :a and status = 'ACTIVE'")
                .param("l", who.activeLinkId()).param("a", who.accountId()).query(Boolean.class).optional()
                .orElseThrow(() -> new IdentityException(Code.LINK_NOT_FOUND, null)));
    }

    public View set(AccountPrincipal who, boolean announcements, String ip) {
        portalOnly(who);
        boolean before = read(who);
        accountTx.forAccount(who.accountId(), () -> jdbc.sql("update account_link set announcement_email = :v where id = :l and account_id = :a")
                .param("v", announcements).param("l", who.activeLinkId()).param("a", who.accountId()).update());
        audit.record(who.organizationId(), new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), null),
                "account.email_preferences_changed", "account_link", who.activeLinkId().toString(), null,
                Map.of("announcements", before), Map.of("announcements", announcements), null, ip));
        return new View(announcements);
    }
}
