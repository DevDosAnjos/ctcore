package br.com.ctcore.identity;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.LoginAttempts.Result;
import br.com.ctcore.integration.ChallengeVerifier;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Login (RN-ACE-10 to RN-ACE-12), password recovery (RN-ACE-14) and password change (RN-ACE-20). */
@Service
public class AuthService {
    static final Duration RESET_VALIDITY = Duration.ofHours(1);

    private record Found(UUID id, String email, String hash, String status) {}

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final LoginAttempts attempts;
    private final ChallengeVerifier challenge;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final SessionService sessions;
    private final InviteService invites;
    private final AccountMailer mailer;
    private final AccountAudit audit;
    private final MessageSource messages;
    private final Clock clock;
    /** Hash of nothing: the login runs Argon2 against it when the account does not exist, so time does not tell. */
    private final String fakeHash;

    public AuthService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, LoginAttempts attempts,
            ChallengeVerifier challenge, PasswordEncoder encoder, PasswordPolicy policy, SessionService sessions,
            InviteService invites, AccountMailer mailer, AccountAudit audit, MessageSource messages, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.attempts = attempts;
        this.challenge = challenge;
        this.encoder = encoder;
        this.policy = policy;
        this.sessions = sessions;
        this.invites = invites;
        this.mailer = mailer;
        this.audit = audit;
        this.messages = messages;
        this.clock = clock;
        this.fakeHash = encoder.encode(Codes.random());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private Found find(String email) {
        return jdbc.sql("select id, email, password_hash, status from account where lower(email) = lower(:e)")
                .param("e", email.strip())
                .query((rs, n) -> new Found(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
                .optional().orElse(null);
    }

    private void requireChallenge(String email, String ip, String token) {
        if (!challenge.verify(token)) {
            attempts.record(email, ip, Result.CHALLENGE_FAILED);
            throw new IdentityException(Code.CHALLENGE_FAILED, null);
        }
    }

    private static void requireStrong(PasswordPolicy policy, String password) {
        var violation = policy.check(password);
        if (violation != null) {
            throw new IdentityException(violation == PasswordPolicy.Violation.LEAKED ? Code.PASSWORD_LEAKED : Code.PASSWORD_WEAK, null);
        }
    }

    /** One answer for every failure: wrong password, unknown e-mail, pending invite, deactivated account. */
    public SessionService.Created login(String email, String password, String challengeToken, String ip, String device) {
        requireChallenge(email, ip, challengeToken);
        switch (attempts.check(email, ip)) {
            case EMAIL_LOCKED -> {
                attempts.record(email, ip, Result.LOCKED);
                throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
            }
            case IP_LIMITED -> {
                attempts.record(email, ip, Result.IP_LIMITED);
                throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
            }
            default -> { }
        }
        Found account = find(email);
        boolean usable = account != null && account.hash() != null && account.status().equals("ACTIVE");
        boolean matches = encoder.matches(password, usable ? account.hash() : fakeHash);
        if (!usable || !matches) {
            attempts.record(email, ip, Result.WRONG_PASSWORD);
            throw new IdentityException(Code.INVALID_CREDENTIALS, null);
        }
        attempts.record(email, ip, Result.SUCCESS);
        return sessions.create(account.id(), ip, device);
    }

    /** Same silent outcome for any e-mail (RN-ACE-11); only the work behind differs. */
    public void requestReset(String email, String challengeToken, String ip) {
        requireChallenge(email, ip, challengeToken);
        if (attempts.resetLimited(email, ip)) {
            throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
        }
        attempts.record(email, ip, Result.RESET_REQUEST);
        Found account = find(email);
        if (account == null || account.status().equals("DEACTIVATED")) {
            return;
        }
        if (account.status().equals("INVITED")) {
            invites.resendToAccount(account.id(), account.email());
            return;
        }
        String code = Codes.random();
        accountTx.forAccount(account.id(), () -> {
            jdbc.sql("update password_reset set canceled_at = :now where account_id = :a and used_at is null and canceled_at is null")
                    .param("now", now()).param("a", account.id()).update();
            jdbc.sql("insert into password_reset (account_id, code_hash, expires_at, created_at) values (:a, :h, :exp, :now)")
                    .param("a", account.id()).param("h", Codes.hash(code)).param("exp", now().plus(RESET_VALIDITY))
                    .param("now", now()).update();
            return null;
        });
        mailer.passwordReset(account.email(), code);
    }

    /** Uses the link: weak password does not consume it; success ends every session and starts a new one. */
    public SessionService.Created reset(String code, String password, String ip, String device) {
        var row = jdbc.sql("select r.id, r.account_id, a.email, a.status, r.expires_at, r.used_at, r.canceled_at "
                + "from password_reset r join account a on a.id = r.account_id where r.code_hash = :h").param("h", Codes.hash(code))
                .query((rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getObject(5, OffsetDateTime.class), rs.getObject(6, OffsetDateTime.class),
                        rs.getObject(7, OffsetDateTime.class)})
                .optional().orElseThrow(() -> new IdentityException(Code.RESET_INVALID, null));
        UUID id = (UUID) row[0], accountId = (UUID) row[1];
        if (row[5] != null || row[6] != null || !((OffsetDateTime) row[4]).isAfter(now()) || !row[3].equals("ACTIVE")) {
            throw new IdentityException(Code.RESET_INVALID, null);
        }
        requireStrong(policy, password);
        String hash = encoder.encode(password);
        accountTx.forAccount(accountId, () -> {
            if (jdbc.sql("update password_reset set used_at = :now where id = :id and used_at is null and canceled_at is null and expires_at > :now")
                    .param("now", now()).param("id", id).update() == 0) {
                throw new IdentityException(Code.RESET_INVALID, null);
            }
            jdbc.sql("update password_reset set canceled_at = :now where account_id = :a and used_at is null and canceled_at is null")
                    .param("now", now()).param("a", accountId).update();
            jdbc.sql("update account set password_hash = :h where id = :a").param("h", hash).param("a", accountId).update();
            sessions.endAll(accountId, null);
            return null;
        });
        audit.record(accountId, new AuditActor(AuditActor.Type.ACCOUNT, accountId, null), ip,
                "account.password_reset", "account.sessions_ended");
        mailer.passwordChanged((String) row[2]);
        return sessions.create(accountId, ip, device);
    }

    /** Logged-in change: needs the current password, keeps this session and ends the others. */
    public void change(UUID accountId, UUID keepSession, String current, String next, String ip) {
        Found account = jdbc.sql("select id, email, password_hash, status from account where id = :a").param("a", accountId)
                .query((rs, n) -> new Found(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
                .single();
        if (account.hash() == null || !encoder.matches(current, account.hash())) {
            throw new ApiValidationException("currentPassword",
                    messages.getMessage("auth.password.current-wrong", null, Locale.forLanguageTag("pt-BR")));
        }
        requireStrong(policy, next);
        String hash = encoder.encode(next);
        accountTx.forAccount(accountId, () -> {
            jdbc.sql("update account set password_hash = :h where id = :a").param("h", hash).param("a", accountId).update();
            sessions.endAll(accountId, keepSession);
            return null;
        });
        audit.record(accountId, new AuditActor(AuditActor.Type.ACCOUNT, accountId, null), ip,
                "account.password_changed", "account.sessions_ended");
        mailer.passwordChanged(account.email());
    }
}
