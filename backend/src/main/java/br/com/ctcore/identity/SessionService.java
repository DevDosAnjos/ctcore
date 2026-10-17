package br.com.ctcore.identity;

import br.com.ctcore.identity.IdentityException.Code;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** RN-ACE-19 and RN-ACE-20: server-side sessions. Only the SHA-256 of the cookie value is stored. */
@Service
public class SessionService {
    static final Duration PORTAL_IDLE = Duration.ofDays(30);
    static final Duration MANAGEMENT_IDLE = Duration.ofHours(12);
    static final Duration WRITE_EVERY = Duration.ofMinutes(1);

    public record Created(UUID accountId, String token, UUID activeLinkId) {}
    public record AccountView(UUID id, String email, String name) {}
    public record LinkView(UUID id, UUID organizationId, String role, Set<UUID> unitIds) {}
    public record SessionView(AccountView account, List<LinkView> links, UUID activeLinkId) {}

    record Link(UUID id, UUID organizationId, Role role) {}

    private record Row(UUID id, UUID accountId, UUID activeLinkId, OffsetDateTime lastUsedAt, OffsetDateTime expiresAt, String status) {}

    private final JdbcClient jdbc;
    private final AccountTransactions accountTx;
    private final Clock clock;

    public SessionService(@Lazy JdbcClient jdbc, @Lazy AccountTransactions accountTx, Clock clock) {
        this.jdbc = jdbc;
        this.accountTx = accountTx;
        this.clock = clock;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private List<Link> activeLinks(UUID accountId) {
        return jdbc.sql("select id, organization_id, role from account_link where account_id = :a and status = 'ACTIVE' order by id")
                .param("a", accountId)
                .query((rs, n) -> new Link(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), Role.valueOf(rs.getString(3))))
                .list();
    }

    /** D3: 30 days idle only when every active link is portal (Student/Guardian); any management link means 12 hours. */
    static Duration idle(List<Link> links) {
        return !links.isEmpty() && links.stream().noneMatch(l -> l.role().management()) ? PORTAL_IDLE : MANAGEMENT_IDLE;
    }

    /** Starts a session; a single active link becomes the active one (RN-ACE-03), several leave it empty. */
    public Created create(UUID accountId, String ip, String device) {
        return accountTx.forAccount(accountId, () -> {
            List<Link> links = activeLinks(accountId);
            String token = Codes.random();
            UUID active = links.size() == 1 ? links.get(0).id() : null;
            OffsetDateTime now = now();
            jdbc.sql("insert into account_session (account_id, token_hash, active_link_id, device, ip, created_at, last_used_at, expires_at) "
                    + "values (:a, :h, :l, :d, :ip, :now, :now, :exp)")
                    .param("a", accountId).param("h", Codes.hash(token)).param("l", active).param("d", device)
                    .param("ip", ip).param("now", now).param("exp", now.plus(idle(links))).update();
            return new Created(accountId, token, active);
        });
    }

    /** The principal of a valid session, renewing it (write at most once a minute); empty when unknown, expired or inactive. */
    public Optional<AccountPrincipal> authenticate(String token) {
        Row s = jdbc.sql("select s.id, s.account_id, s.active_link_id, s.last_used_at, s.expires_at, a.status "
                + "from account_session s join account a on a.id = s.account_id where s.token_hash = :h")
                .param("h", Codes.hash(token))
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, OffsetDateTime.class), rs.getObject(5, OffsetDateTime.class), rs.getString(6)))
                .optional().orElse(null);
        if (s == null) {
            return Optional.empty();
        }
        OffsetDateTime now = now();
        if (!s.expiresAt().isAfter(now) || !s.status().equals("ACTIVE")) {
            jdbc.sql("delete from account_session where id = :id").param("id", s.id()).update();
            return Optional.empty();
        }
        return Optional.of(accountTx.forAccount(s.accountId(), () -> {
            List<Link> links = activeLinks(s.accountId());
            if (!s.lastUsedAt().plus(WRITE_EVERY).isAfter(now)) {
                jdbc.sql("update account_session set last_used_at = :now, expires_at = :exp where id = :id")
                        .param("now", now).param("exp", now.plus(idle(links))).param("id", s.id()).update();
            }
            Link active = links.stream().filter(l -> l.id().equals(s.activeLinkId())).findFirst().orElse(null);
            Set<UUID> units = active == null ? Set.of() : unitsOf(active.id());
            return new AccountPrincipal(s.accountId(), s.id(), active == null ? null : active.id(),
                    active == null ? null : active.organizationId(), active == null ? null : active.role(), units);
        }));
    }

    private Set<UUID> unitsOf(UUID linkId) {
        return new HashSet<>(jdbc.sql("select unit_id from account_link_unit where link_id = :l").param("l", linkId)
                .query(UUID.class).list());
    }

    /** Account, active links and the active one, for the front to decide where to go (RN-ACE-16). */
    public SessionView view(UUID accountId, UUID activeLinkId) {
        return accountTx.forAccount(accountId, () -> {
            AccountView account = jdbc.sql("select id, email, name from account where id = :a").param("a", accountId)
                    .query((rs, n) -> new AccountView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3))).single();
            List<LinkView> links = activeLinks(accountId).stream()
                    .map(l -> new LinkView(l.id(), l.organizationId(), l.role().name(), unitsOf(l.id()))).toList();
            return new SessionView(account, links, activeLinkId);
        });
    }

    /** PUT /session/link: only an active link of the same account; anything else is a 404 and nothing changes. */
    public void selectLink(UUID accountId, UUID sessionId, UUID linkId) {
        accountTx.forAccount(accountId, () -> {
            if (activeLinks(accountId).stream().noneMatch(l -> l.id().equals(linkId))) {
                throw new IdentityException(Code.LINK_NOT_FOUND, null);
            }
            jdbc.sql("update account_session set active_link_id = :l where id = :s and account_id = :a")
                    .param("l", linkId).param("s", sessionId).param("a", accountId).update();
            return null;
        });
    }

    public void end(UUID sessionId) {
        jdbc.sql("delete from account_session where id = :id").param("id", sessionId).update();
    }

    /** Ends the sessions of the account, all of them or all but {@code except}; the caller audits it. */
    public void endAll(UUID accountId, UUID except) {
        jdbc.sql("delete from account_session where account_id = :a and (cast(:x as uuid) is null or id <> cast(:x as uuid))")
                .param("a", accountId).param("x", except).update();
    }

    /** RN-ACE-20: after a management link ends, the account without any other one loses every session. */
    public boolean endAllIfNoManagement(UUID accountId) {
        return accountTx.forAccount(accountId, () -> {
            if (activeLinks(accountId).stream().anyMatch(l -> l.role().management())) {
                return false;
            }
            endAll(accountId, null);
            return true;
        });
    }
}
