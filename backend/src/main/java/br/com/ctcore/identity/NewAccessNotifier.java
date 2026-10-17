package br.com.ctcore.identity;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-ACE-22: a login from a device the account has not used before sends "Novo acesso"; the first device of the account does not (D4). */
@Component
public class NewAccessNotifier {
    private static final Logger LOG = LoggerFactory.getLogger(NewAccessNotifier.class);

    private final JdbcClient jdbc;
    private final AccountMailer mailer;
    private final IpLocator locator;
    private final Clock clock;

    public NewAccessNotifier(@Lazy JdbcClient jdbc, AccountMailer mailer, IpLocator locator, Clock clock) {
        this.jdbc = jdbc;
        this.mailer = mailer;
        this.locator = locator;
        this.clock = clock;
    }

    /** Never throws: a failure here must not stop the login. */
    public void loggedIn(UUID accountId, String ip, String userAgent) {
        try {
            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            boolean hadOthers = jdbc.sql("select exists (select 1 from account_device where account_id = :a)")
                    .param("a", accountId).query(Boolean.class).single();
            boolean added = jdbc.sql("insert into account_device (account_id, device_hash, first_seen_at, last_seen_at) values (:a, :h, :now, :now) "
                    + "on conflict (account_id, device_hash) do update set last_seen_at = :now returning (xmax = 0)")
                    .param("a", accountId).param("h", Codes.hash(userAgent)).param("now", now).query(Boolean.class).single();
            if (added && hadOthers) {
                String to = jdbc.sql("select email from account where id = :a").param("a", accountId).query(String.class).single();
                mailer.newAccess(to, DeviceLabel.of(userAgent), ip == null ? null : locator.city(ip).orElse(null), now.toInstant());
            }
        } catch (RuntimeException e) {
            LOG.warn("new access notice failed account={} cause={}", accountId, e.getClass().getSimpleName());
        }
    }
}
