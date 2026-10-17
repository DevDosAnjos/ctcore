package br.com.ctcore.identity;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-ACE-21: password (and code, when two-step is on) for a critical action; answers a 5-minute single-use token. */
@Service
public class ReauthService {
    static final Duration VALIDITY = Duration.ofMinutes(5);

    public record Token(String reauthToken, OffsetDateTime expiresAt) {}

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final TwoFactorService twoFactor;
    private final AccountAudit audit;
    private final Clock clock;

    public ReauthService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, TwoFactorService twoFactor,
            AccountAudit audit, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.twoFactor = twoFactor;
        this.audit = audit;
        this.clock = clock;
    }

    public Token confirm(AccountPrincipal principal, String action, String password, String code, String ip) {
        CriticalAction critical;
        try {
            critical = CriticalAction.valueOf(action == null ? "" : action);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("action", "ação inválida");
        }
        AuditActor actor = new AuditActor(AuditActor.Type.ACCOUNT, principal.accountId(), null);
        try {
            twoFactor.confirm(principal.accountId(), password, code, ip);
        } catch (IdentityException e) {
            audit.record(principal.accountId(), actor, ip, "account.reauth_failed");
            throw e;
        }
        String token = Codes.random();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        OffsetDateTime expires = now.plus(VALIDITY);
        accountTx.forAccount(principal.accountId(), () -> jdbc.sql(
                "insert into reauth_token (account_id, session_id, action, token_hash, expires_at, created_at) "
                + "values (:a, :s, :act, :h, :exp, :now)")
                .param("a", principal.accountId()).param("s", principal.sessionId()).param("act", critical.name())
                .param("h", Codes.hash(token)).param("exp", expires).param("now", now).update());
        audit.record(principal.accountId(), actor, ip, "account.reauth_confirmed");
        return new Token(token, expires);
    }
}
