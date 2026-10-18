package br.com.ctcore.identity;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.identity.IdentityException.Code;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-ACE-15: e-mail change with a link to the new address; the account e-mail only changes on confirmation. */
@Service
public class EmailChangeService {
    static final Duration VALIDITY = Duration.ofHours(24);
    static final Duration LIMIT_WINDOW = Duration.ofHours(1);
    static final int LIMIT = 3;

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final TwoFactorService twoFactor;
    private final AccountMailer mailer;
    private final AccountAudit audit;
    private final MessageSource messages;
    private final Clock clock;

    public EmailChangeService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, TwoFactorService twoFactor,
            AccountMailer mailer, AccountAudit audit, MessageSource messages, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.twoFactor = twoFactor;
        this.mailer = mailer;
        this.audit = audit;
        this.messages = messages;
        this.clock = clock;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static AuditActor actor(UUID accountId) {
        return new AuditActor(AuditActor.Type.ACCOUNT, accountId, null);
    }

    /** Same silent outcome when the address belongs to another account (RN-ACE-11): nothing saved, nothing sent. */
    public void request(UUID accountId, String newEmail, String password, String code, String ip) {
        String target = newEmail.strip();
        // The password (and code) come first: only the owner learns anything else about the account.
        twoFactor.confirm(accountId, password, code, ip);
        String current = jdbc.sql("select email from account where id = :a").param("a", accountId).query(String.class).single();
        if (target.equalsIgnoreCase(current.strip())) {
            throw new ApiValidationException("newEmail", messages.getMessage("auth.email-change.same", null, Locale.forLanguageTag("pt-BR")));
        }
        String secret = Codes.random();
        boolean saved = accountTx.forAccount(accountId, () -> {
            long recent = jdbc.sql("select count(*) from account_email_change where account_id = :a and created_at > :since")
                    .param("a", accountId).param("since", now().minus(LIMIT_WINDOW)).query(Long.class).single();
            if (recent >= LIMIT) {
                throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
            }
            if (taken(target, accountId)) {
                return false;
            }
            jdbc.sql("update account_email_change set canceled_at = :now where account_id = :a and used_at is null and canceled_at is null")
                    .param("now", now()).param("a", accountId).update();
            jdbc.sql("insert into account_email_change (account_id, new_email, code_hash, expires_at, created_at) "
                    + "values (:a, :e, :h, :exp, :now)")
                    .param("a", accountId).param("e", target).param("h", Codes.hash(secret))
                    .param("exp", now().plus(VALIDITY)).param("now", now()).update();
            return true;
        });
        if (saved) {
            audit.record(accountId, actor(accountId), ip, "account.email_change_requested");
            mailer.emailChange(target, secret);
        }
    }

    private boolean taken(String email, UUID except) {
        return jdbc.sql("select exists (select 1 from account where lower(email) = lower(:e) and id <> :a)")
                .param("e", email).param("a", except).query(Boolean.class).single();
    }

    /** The link of this account, once: any other case is the same 410 and nothing changes. */
    public void confirm(UUID accountId, String code, String ip) {
        String oldEmail;
        try {
            oldEmail = accountTx.forAccount(accountId, () -> {
                var row = jdbc.sql("select id, new_email from account_email_change where code_hash = :h and account_id = :a "
                        + "and used_at is null and canceled_at is null and expires_at > :now")
                        .param("h", Codes.hash(code)).param("a", accountId).param("now", now())
                        .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).optional()
                        .orElseThrow(() -> new IdentityException(Code.EMAIL_CHANGE_INVALID, null));
                if (taken(row[1], accountId)) {
                    throw new IdentityException(Code.EMAIL_CHANGE_INVALID, null);
                }
                String old = jdbc.sql("select email from account where id = :a").param("a", accountId).query(String.class).single();
                if (jdbc.sql("update account_email_change set used_at = :now where id = :id and used_at is null")
                        .param("now", now()).param("id", UUID.fromString(row[0])).update() == 0) {
                    throw new IdentityException(Code.EMAIL_CHANGE_INVALID, null);
                }
                jdbc.sql("update account_email_change set canceled_at = :now where account_id = :a and used_at is null and canceled_at is null")
                        .param("now", now()).param("a", accountId).update();
                jdbc.sql("update account set email = :e where id = :a").param("e", row[1]).param("a", accountId).update();
                return old;
            });
        } catch (DataIntegrityViolationException e) {
            // Another account took the address between the check and the update: the unique index on lower(email) decides.
            throw new IdentityException(Code.EMAIL_CHANGE_INVALID, null);
        }
        audit.record(accountId, actor(accountId), ip, "account.email_changed");
        mailer.emailChanged(oldEmail);
    }
}
