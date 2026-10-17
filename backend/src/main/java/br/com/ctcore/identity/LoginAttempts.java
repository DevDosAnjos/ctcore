package br.com.ctcore.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-ACE-10: every attempt goes to login_attempt; N wrong passwords in a row lock the e-mail, many failures lock the IP (D4). */
@Component
public class LoginAttempts {
    public enum Result { SUCCESS, WRONG_PASSWORD, LOCKED, IP_LIMITED, CHALLENGE_FAILED, RESET_REQUEST, WRONG_CODE }
    public enum Status { OPEN, EMAIL_LOCKED, IP_LIMITED }

    /** One password verdict (success or wrong password), used to decide the lock. */
    record Verdict(Instant at, boolean failure) {}

    private final JdbcClient jdbc;
    private final Clock clock;
    private final int maxFailures;
    private final Duration window;
    private final int ipMaxFailures;
    private final int resetMaxPerEmail;

    public LoginAttempts(@Lazy JdbcClient jdbc, Clock clock,
            @Value("${ctcore.security.login.max-failures:5}") int maxFailures,
            @Value("${ctcore.security.login.lockout:15m}") Duration window,
            @Value("${ctcore.security.login.ip-max-failures:20}") int ipMaxFailures,
            @Value("${ctcore.security.login.reset-max-per-email:5}") int resetMaxPerEmail) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.window = window;
        this.ipMaxFailures = ipMaxFailures;
        this.resetMaxPerEmail = resetMaxPerEmail;
    }

    public static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public void record(String email, String ip, Result result) {
        jdbc.sql("insert into login_attempt (email, ip, occurred_at, result) values (:email, :ip, :at, :result)")
                .param("email", normalize(email)).param("ip", ip)
                .param("at", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).param("result", result.name()).update();
    }

    /** Same answer for any e-mail, existing or not: the lock only looks at attempts. */
    public Status check(String email, String ip) {
        Instant now = clock.instant();
        List<Verdict> latest = jdbc.sql("select occurred_at, result <> 'SUCCESS' as failure from login_attempt "
                + "where email = :email and result in ('SUCCESS', 'WRONG_PASSWORD', 'WRONG_CODE') "
                + "order by occurred_at desc, id desc limit :max")
                .param("email", normalize(email)).param("max", maxFailures)
                .query((rs, n) -> new Verdict(rs.getObject("occurred_at", OffsetDateTime.class).toInstant(), rs.getBoolean("failure")))
                .list();
        if (emailLocked(latest, maxFailures, window, now)) {
            return Status.EMAIL_LOCKED;
        }
        if (ip != null) {
            Long failures = jdbc.sql("select count(*) from login_attempt where ip = :ip and result in ('WRONG_PASSWORD', 'WRONG_CODE') "
                    + "and occurred_at > :since")
                    .param("ip", ip).param("since", OffsetDateTime.ofInstant(now.minus(window), ZoneOffset.UTC))
                    .query(Long.class).single();
            if (failures >= ipMaxFailures) {
                return Status.IP_LIMITED;
            }
        }
        return Status.OPEN;
    }

    /** Password-reset requests (RN-ACE-10): too many per e-mail or per IP in one window. Same answer for any e-mail. */
    public boolean resetLimited(String email, String ip) {
        OffsetDateTime since = OffsetDateTime.ofInstant(clock.instant().minus(window), ZoneOffset.UTC);
        Long byEmail = jdbc.sql("select count(*) from login_attempt where email = :email and result = 'RESET_REQUEST' and occurred_at > :since")
                .param("email", normalize(email)).param("since", since).query(Long.class).single();
        if (byEmail >= resetMaxPerEmail) {
            return true;
        }
        Long byIp = ip == null ? 0L : jdbc.sql("select count(*) from login_attempt where ip = :ip and result = 'RESET_REQUEST' and occurred_at > :since")
                .param("ip", ip).param("since", since).query(Long.class).single();
        return byIp >= ipMaxFailures;
    }

    /** Newest first: locked when the last {@code max} verdicts are all failures within one window and that window has not passed. */
    static boolean emailLocked(List<Verdict> latestNewestFirst, int max, Duration window, Instant now) {
        if (latestNewestFirst.size() < max || !latestNewestFirst.stream().allMatch(Verdict::failure)) {
            return false;
        }
        Instant newest = latestNewestFirst.get(0).at();
        Instant oldest = latestNewestFirst.get(max - 1).at();
        return Duration.between(oldest, newest).compareTo(window) <= 0 && now.isBefore(newest.plus(window));
    }
}
