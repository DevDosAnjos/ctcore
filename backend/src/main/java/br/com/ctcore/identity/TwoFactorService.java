package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.LoginAttempts.Result;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.OptionalLong;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** RN-ACE-17 and RN-ACE-18: TOTP two-step and backup codes. No code is ever sent by e-mail or SMS. */
@Service
public class TwoFactorService {
    static final int BACKUP_CODES = 10;
    static final int BACKUP_LENGTH = 10;
    /** No 0/O, 1/I/L: easy to read aloud and to type. */
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record Setup(String secret, String otpauthUri) {}

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final LoginAttempts attempts;
    private final PasswordEncoder encoder;
    private final SecretCipher cipher;
    private final AccountAudit audit;
    private final Clock clock;

    public TwoFactorService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, LoginAttempts attempts,
            PasswordEncoder encoder, SecretCipher cipher, AccountAudit audit, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.attempts = attempts;
        this.encoder = encoder;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static AuditActor actor(UUID accountId) {
        return new AuditActor(AuditActor.Type.ACCOUNT, accountId, null);
    }

    public boolean enabled(UUID accountId) {
        return accountTx.forAccount(accountId, () -> jdbc.sql(
                "select exists (select 1 from account_two_factor where account_id = :a and enabled_at is not null)")
                .param("a", accountId).query(Boolean.class).single());
    }

    /** The one place that says who must have two-step: an active Coordinator link (the platform staff joins here, D2). */
    public boolean requiredFor(UUID accountId) {
        return accountTx.forAccount(accountId, () -> jdbc.sql(
                "select exists (select 1 from account_link where account_id = :a and status = 'ACTIVE' and role = 'COORDINATOR')")
                .param("a", accountId).query(Boolean.class).single());
    }

    /** Required and not yet on: the session reaches only the setup, the session read and the exit. */
    public boolean setupRequired(UUID accountId) {
        return requiredFor(accountId) && !enabled(accountId);
    }

    /** New secret, pending until {@link #enable}; a later call replaces a pending one. */
    public Setup setup(UUID accountId) {
        byte[] secret = Totp.newSecret();
        String email = accountTx.forAccount(accountId, () -> {
            int saved = jdbc.sql("insert into account_two_factor (account_id, secret_encrypted, created_at) values (:a, :s, :now) "
                    + "on conflict (account_id) do update set secret_encrypted = excluded.secret_encrypted, "
                    + "created_at = excluded.created_at where account_two_factor.enabled_at is null")
                    .param("a", accountId).param("s", cipher.encrypt(secret)).param("now", now()).update();
            if (saved == 0) {
                throw new IdentityException(Code.TWO_FACTOR_STATE, "ENABLED");
            }
            return jdbc.sql("select email from account where id = :a").param("a", accountId).query(String.class).single();
        });
        String base32 = Base32.encode(secret);
        String label = URLEncoder.encode("CTCore:" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return new Setup(base32, "otpauth://totp/" + label + "?secret=" + base32
                + "&issuer=CTCore&algorithm=SHA1&digits=6&period=30");
    }

    /** Proves the pending secret with a valid code, turns two-step on and answers the 10 backup codes (shown once). */
    public List<String> enable(UUID accountId, String code, String ip) {
        List<String> codes = accountTx.forAccount(accountId, () -> {
            var row = jdbc.sql("select secret_encrypted, enabled_at is not null from account_two_factor where account_id = :a")
                    .param("a", accountId).query((rs, n) -> new Object[]{rs.getString(1), rs.getBoolean(2)}).optional().orElse(null);
            if (row == null || (Boolean) row[1]) {
                throw new IdentityException(Code.TWO_FACTOR_STATE, row == null ? "NO_SETUP" : "ENABLED");
            }
            if (!isTotpShape(clean(code)) || Totp.match(cipher.decrypt((String) row[0]), clean(code), clock.instant()).isEmpty()) {
                throw new IdentityException(Code.INVALID_TWO_FACTOR_CODE, null);
            }
            jdbc.sql("update account_two_factor set enabled_at = :now where account_id = :a")
                    .param("now", now()).param("a", accountId).update();
            return newBackupCodes(accountId);
        });
        audit.record(accountId, actor(accountId), ip, "account.two_factor_enabled");
        return codes;
    }

    /** Password and code; whoever is required to have two-step cannot turn it off (RN-ACE-17). */
    public void disable(UUID accountId, String password, String code, String ip) {
        if (requiredFor(accountId)) {
            throw new AccessDeniedException("Two-step is mandatory for this account");
        }
        requireEnabled(accountId);
        confirm(accountId, password, code, ip);
        accountTx.forAccount(accountId, () -> {
            jdbc.sql("delete from account_backup_code where account_id = :a").param("a", accountId).update();
            return jdbc.sql("delete from account_two_factor where account_id = :a").param("a", accountId).update();
        });
        audit.record(accountId, actor(accountId), ip, "account.two_factor_disabled");
    }

    /** Session only, no password or code (not a critical action); the previous codes stop working and 10 new ones come back (RN-ACE-18). */
    public List<String> regenerate(UUID accountId, String ip) {
        requireEnabled(accountId);
        List<String> codes = accountTx.forAccount(accountId, () -> newBackupCodes(accountId));
        audit.record(accountId, actor(accountId), ip, "account.backup_codes_regenerated");
        return codes;
    }

    private void requireEnabled(UUID accountId) {
        if (!enabled(accountId)) {
            throw new IdentityException(Code.TWO_FACTOR_STATE, "NOT_ENABLED");
        }
    }

    /**
     * Password, then the code if two-step is on, for a sensitive action. Honors and feeds the login lock (RN-ACE-10):
     * a wrong password or code counts as a failure of the account's e-mail and of the IP.
     */
    void confirm(UUID accountId, String password, String code, String ip) {
        var account = jdbc.sql("select email, password_hash from account where id = :a").param("a", accountId)
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2)}).single();
        String email = account[0];
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
        if (password == null || account[1] == null || !encoder.matches(password, account[1])) {
            attempts.record(email, ip, Result.WRONG_PASSWORD);
            throw new IdentityException(Code.INVALID_CREDENTIALS, null);
        }
        if (enabled(accountId) && !verify(accountId, code, ip)) {
            attempts.record(email, ip, Result.WRONG_CODE);
            throw new IdentityException(Code.INVALID_TWO_FACTOR_CODE, null);
        }
        attempts.record(email, ip, Result.SUCCESS);
    }

    /** A TOTP code (once per 30 s step, D4) or an unused backup code, which is spent. False for anything else. */
    boolean verify(UUID accountId, String code, String ip) {
        String c = clean(code);
        if (isTotpShape(c)) {
            return accountTx.forAccount(accountId, () -> {
                var row = jdbc.sql("select secret_encrypted, last_used_step from account_two_factor "
                        + "where account_id = :a and enabled_at is not null").param("a", accountId)
                        .query((rs, n) -> new Object[]{rs.getString(1), (Long) rs.getObject(2)}).optional().orElse(null);
                if (row == null) {
                    return false;
                }
                OptionalLong step = Totp.match(cipher.decrypt((String) row[0]), c, clock.instant());
                if (step.isEmpty()) {
                    return false;
                }
                // The conditional update is what makes a code usable once, even with two requests at the same time.
                return jdbc.sql("update account_two_factor set last_used_step = :s where account_id = :a "
                        + "and (last_used_step is null or last_used_step < :s)")
                        .param("s", step.getAsLong()).param("a", accountId).update() == 1;
            });
        }
        if (c.length() != BACKUP_LENGTH) {
            return false;
        }
        boolean spent = accountTx.forAccount(accountId, () -> jdbc.sql(
                "update account_backup_code set used_at = :now where account_id = :a and code_hash = :h and used_at is null")
                .param("now", now()).param("a", accountId).param("h", Codes.hash(c)).update() == 1);
        if (spent) {
            audit.record(accountId, actor(accountId), ip, "account.backup_code_used");
        }
        return spent;
    }

    private static String clean(String code) {
        return code == null ? "" : code.strip().replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
    }

    private static boolean isTotpShape(String c) {
        return c.length() == 6 && c.chars().allMatch(Character::isDigit);
    }

    /** Inside the account scope: drops the old codes (used or not) and stores the hashes of 10 new ones. */
    private List<String> newBackupCodes(UUID accountId) {
        Set<String> codes = new LinkedHashSet<>();
        while (codes.size() < BACKUP_CODES) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < BACKUP_LENGTH; i++) {
                code.append(BACKUP_ALPHABET.charAt(RANDOM.nextInt(BACKUP_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        jdbc.sql("delete from account_backup_code where account_id = :a").param("a", accountId).update();
        for (String code : codes) {
            jdbc.sql("insert into account_backup_code (account_id, code_hash, created_at) values (:a, :h, :now)")
                    .param("a", accountId).param("h", Codes.hash(code)).param("now", now()).update();
        }
        return new ArrayList<>(codes);
    }
}
