package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Internal account services (D7): no route creates an account (RN-ACE-04). */
@Service
public class AccountService {
    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final ScopedAudit audit;
    private final SessionService sessions;
    private final AccountAudit accountAudit;

    public AccountService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, PasswordPolicy policy,
            PasswordEncoder encoder, ScopedAudit audit, SessionService sessions, AccountAudit accountAudit) {
        this.sessions = sessions;
        this.accountAudit = accountAudit;
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.policy = policy;
        this.encoder = encoder;
        this.audit = audit;
    }

    /** Creates the account for an e-mail, or returns the existing one untouched (RN-ACE-01); never resets a password (RN-ACE-09). */
    public UUID provision(String email, String name, String phone, String password) {
        var violation = password == null ? null : policy.check(password);
        if (violation != null) {
            throw new IdentityException(violation == PasswordPolicy.Violation.LEAKED
                    ? IdentityException.Code.PASSWORD_LEAKED : IdentityException.Code.PASSWORD_WEAK, null);
        }
        jdbc.sql("insert into account (email, name, phone, password_hash, status) values (:e, :n, :p, :h, :s) on conflict do nothing")
                .param("e", email.strip()).param("n", name).param("p", phone)
                .param("h", password == null ? null : encoder.encode(password))
                .param("s", password == null ? "INVITED" : "ACTIVE").update();
        return find(email).orElseThrow();
    }

    /** Account for the e-mail, case-insensitive. */
    public java.util.Optional<UUID> find(String email) {
        return jdbc.sql("select id from account where lower(email) = lower(:e)").param("e", email.strip()).query(UUID.class).optional();
    }

    /** RN-ACE-20: the account stops entering. One audit line per organization where it had an active link (D5). */
    public void deactivate(UUID accountId, AuditActor actor) {
        List<UUID> organizations = accountTx.forAccount(accountId, () -> {
            if (jdbc.sql("update account set status = 'DEACTIVATED' where id = :id and status <> 'DEACTIVATED'")
                    .param("id", accountId).update() == 0) {
                throw new AccessDeniedException("Account not found or already deactivated");
            }
            sessions.endAll(accountId, null);
            return jdbc.sql("select distinct organization_id from account_link where account_id = :id and status = 'ACTIVE'")
                    .param("id", accountId).query(UUID.class).list();
        });
        for (UUID org : organizations) {
            audit.record(org, new AuditEntry(actor, "account.deactivated", "account", accountId.toString(), null,
                    Map.of("status", "ACTIVE"), Map.of("status", "DEACTIVATED"), null, null));
        }
        accountAudit.record(accountId, actor, null, "account.sessions_ended");
    }
}
