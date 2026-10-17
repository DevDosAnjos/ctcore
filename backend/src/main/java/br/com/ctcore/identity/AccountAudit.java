package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** D5: an account event is audited once per organization where the account has an active link; none, no line. */
@Component
public class AccountAudit {
    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final ScopedAudit audit;

    public AccountAudit(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, ScopedAudit audit) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.audit = audit;
    }

    public void record(UUID accountId, AuditActor actor, String ip, String... actions) {
        List<UUID> organizations = accountTx.forAccount(accountId, () -> jdbc.sql(
                "select distinct organization_id from account_link where account_id = :a and status = 'ACTIVE'")
                .param("a", accountId).query(UUID.class).list());
        for (UUID org : organizations) {
            for (String action : actions) {
                audit.record(org, new AuditEntry(actor, action, "account", accountId.toString(), null, null, null, null, ip));
            }
        }
    }
}
