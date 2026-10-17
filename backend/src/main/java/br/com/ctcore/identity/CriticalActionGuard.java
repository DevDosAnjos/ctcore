package br.com.ctcore.identity;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-ACE-21 (D6): the critical routes call {@link #consume}; the token is single use, bound to the session and to the action. */
@Component
public class CriticalActionGuard {
    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final Clock clock;

    public CriticalActionGuard(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.clock = clock;
    }

    /** Passes once; missing, wrong action, other session, expired or used token is REAUTH_REQUIRED (403). */
    public void consume(AccountPrincipal principal, CriticalAction action, String token) {
        if (token == null || token.isBlank()) {
            throw new IdentityException(IdentityException.Code.REAUTH_REQUIRED, null);
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int used = accountTx.forAccount(principal.accountId(), () -> jdbc.sql(
                "update reauth_token set used_at = :now where token_hash = :h and account_id = :a and session_id = :s "
                + "and action = :act and used_at is null and expires_at > :now")
                .param("now", now).param("h", Codes.hash(token)).param("a", principal.accountId())
                .param("s", principal.sessionId()).param("act", action.name()).update());
        if (used == 0) {
            throw new IdentityException(IdentityException.Code.REAUTH_REQUIRED, null);
        }
    }
}
