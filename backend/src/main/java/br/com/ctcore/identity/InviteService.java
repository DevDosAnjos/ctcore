package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** RN-ACE-05 to RN-ACE-07 and RN-ACE-13: cascade invites, adulthood, existing accounts, 7 days, single use, resend limit. */
@Service
public class InviteService {
    static final Duration VALIDITY = Duration.ofDays(7);
    static final int MAX_RESENDS_PER_DAY = 3;

    public record Issued(UUID inviteId, UUID linkId, UUID accountId, String code) {}
    public record Accepted(UUID accountId, UUID linkId, UUID organizationId) {}

    private record Target(UUID accountId, String email, Role role, Set<UUID> units) {}
    private record Found(UUID inviteId, UUID accountId, UUID linkId, UUID organizationId, String email, String name, String status,
            String passwordHash, OffsetDateTime expiresAt, OffsetDateTime usedAt, OffsetDateTime canceledAt,
            String role) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions orgTx;
    private final AccountTransactions accountTx;
    private final AccountMailer mailer;
    private final ScopedAudit audit;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final LoginAttempts attempts;
    private final Clock clock;

    public InviteService(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions orgTx, @Lazy AccountTransactions accountTx,
            AccountMailer mailer, ScopedAudit audit, PasswordPolicy policy,
            PasswordEncoder encoder, LoginAttempts attempts, Clock clock) {
        this.jdbc = jdbc;
        this.orgTx = orgTx;
        this.accountTx = accountTx;
        this.mailer = mailer;
        this.audit = audit;
        this.policy = policy;
        this.encoder = encoder;
        this.attempts = attempts;
        this.clock = clock;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /** Creates the pending link (and the account, if the e-mail is new) and sends the invite. */
    public Issued invite(Inviter by, UUID organizationId, String email, String name, Role role, Set<UUID> units, LocalDate birthDate) {
        return invite(by, organizationId, email, name, role, units, birthDate, false);
    }

    /** RN-PRO-02: dar acesso a um professor cadastrado; o Coordenador também pode (fora da cascata de RN-ACE-05). */
    public Issued inviteTeacher(Inviter by, UUID organizationId, String email, String name, Set<UUID> units) {
        return invite(by, organizationId, email, name, Role.TEACHER, units, null, true);
    }

    private Issued invite(Inviter by, UUID organizationId, String email, String name, Role role, Set<UUID> units, LocalDate birthDate,
            boolean teacherAccess) {
        Set<UUID> unitIds = units == null ? Set.of() : units;
        if (teacherAccess ? !InviteRules.teacherAccess(by, unitIds) : !InviteRules.allowed(by, role, unitIds)) {
            throw new AccessDeniedException("Invite not allowed for this role");
        }
        if (role == Role.STUDENT && !InviteRules.adult(birthDate, now().toLocalDate())) {
            throw new IdentityException(Code.MINOR_HAS_NO_ACCOUNT, null);
        }
        Issued issued = orgTx.provision(organizationId, () -> {
            jdbc.sql("insert into account (email, name, status) values (:e, :n, 'INVITED') on conflict do nothing")
                    .param("e", email.strip()).param("n", name).update();
            var account = jdbc.sql("select id, status from account where lower(email) = lower(:e)").param("e", email.strip())
                    .query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("status"))).single();
            if (account.getValue().equals("DEACTIVATED")) {
                throw new AccessDeniedException("Account is deactivated");
            }
            UUID linkId = linkFor(organizationId, account.getKey(), role, unitIds);
            return issue(by, organizationId, account.getKey(), linkId, false, "invite.created");
        });
        mailer.invite(email.strip(), issued.code());
        return issued;
    }

    private UUID linkFor(UUID organizationId, UUID accountId, Role role, Set<UUID> units) {
        Optional<Map.Entry<UUID, String>> existing = jdbc.sql("select id, status from account_link where account_id = :a and organization_id = :o and role = :r")
                .param("a", accountId).param("o", organizationId).param("r", role.name())
                .query((rs, n) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("status"))).optional();
        if (existing.isPresent()) {
            if (existing.get().getValue().equals("ACTIVE")) {
                throw new IdentityException(Code.ALREADY_LINKED, null);
            }
            jdbc.sql("update account_link set status = 'PENDING', ends_on = null where id = :id").param("id", existing.get().getKey()).update();
            jdbc.sql("delete from account_link_unit where link_id = :id").param("id", existing.get().getKey()).update();
            addUnits(organizationId, existing.get().getKey(), units);
            return existing.get().getKey();
        }
        UUID id = jdbc.sql("insert into account_link (account_id, organization_id, role, status) values (:a, :o, :r, 'PENDING') returning id")
                .param("a", accountId).param("o", organizationId).param("r", role.name()).query(UUID.class).single();
        addUnits(organizationId, id, units);
        return id;
    }

    private void addUnits(UUID organizationId, UUID linkId, Set<UUID> units) {
        for (UUID unit : units) {
            jdbc.sql("insert into account_link_unit (link_id, organization_id, unit_id) values (:l, :o, :u)")
                    .param("l", linkId).param("o", organizationId).param("u", unit).update();
        }
    }

    /** Cancels the open invites of the link, creates a new one and audits it. Runs inside the organization scope. */
    private Issued issue(Inviter by, UUID organizationId, UUID accountId, UUID linkId, boolean resend, String action) {
        jdbc.sql("update account_invite set canceled_at = :now where link_id = :l and used_at is null and canceled_at is null")
                .param("now", now()).param("l", linkId).update();
        String code = Codes.random();
        UUID id = jdbc.sql("insert into account_invite (account_id, link_id, organization_id, code_hash, expires_at, created_at, sent_by, resend) "
                + "values (:a, :l, :o, :h, :exp, :now, :by, :re) returning id")
                .param("a", accountId).param("l", linkId).param("o", organizationId).param("h", Codes.hash(code))
                .param("exp", now().plus(VALIDITY)).param("now", now()).param("by", by.accountId()).param("re", resend)
                .query(UUID.class).single();
        audit.record(organizationId, new AuditEntry(by.actor(), action, "account_invite", id.toString(), null,
                null, Map.of("linkId", linkId.toString(), "accountId", accountId.toString()), null, null));
        return new Issued(id, linkId, accountId, code);
    }

    /** Loads a pending link of the organization, checking that the inviter may manage its role and units. */
    private Target authorize(Inviter by, UUID organizationId, UUID linkId, boolean teacherAccess) {
        var row = jdbc.sql("select l.account_id, l.role, l.status, a.email from account_link l join account a on a.id = l.account_id "
                + "where l.id = :l and l.organization_id = :o").param("l", linkId).param("o", organizationId)
                .query((rs, n) -> new String[]{rs.getString("account_id"), rs.getString("role"), rs.getString("status"), rs.getString("email")})
                .optional().orElseThrow(() -> new AccessDeniedException("Link not found"));
        Set<UUID> units = new HashSet<>(jdbc.sql("select unit_id from account_link_unit where link_id = :l").param("l", linkId).query(UUID.class).list());
        Role role = Role.valueOf(row[1]);
        if (!row[2].equals("PENDING") || !(teacherAccess ? role == Role.TEACHER && InviteRules.teacherAccess(by, units) : InviteRules.allowed(by, role, units))) {
            throw new AccessDeniedException("Link is not a pending invite you may manage");
        }
        return new Target(UUID.fromString(row[0]), row[3], role, units);
    }

    /** RN-ACE-13: cancels the previous invite and sends a new one; at most 3 resends a day per person. */
    public Issued resend(Inviter by, UUID organizationId, UUID linkId) {
        return resend(by, organizationId, linkId, false);
    }

    /** RN-PRO-02: reenvio do convite de acesso de professor, também pelo Coordenador. */
    public Issued resendTeacher(Inviter by, UUID organizationId, UUID linkId) {
        return resend(by, organizationId, linkId, true);
    }

    private Issued resend(Inviter by, UUID organizationId, UUID linkId, boolean teacherAccess) {
        Object[] sent = new Object[1];
        Issued issued = orgTx.provision(organizationId, () -> {
            Target t = authorize(by, organizationId, linkId, teacherAccess);
            checkResendLimit(t.accountId());
            sent[0] = t.email();
            return issue(by, organizationId, t.accountId(), linkId, true, "invite.resent");
        });
        mailer.invite((String) sent[0], issued.code());
        return issued;
    }

    private void checkResendLimit(UUID accountId) {
        int today = jdbc.sql("select count(*) from account_invite where account_id = :a and resend and created_at > :since")
                .param("a", accountId).param("since", now().minusDays(1)).query(Integer.class).single();
        if (today >= MAX_RESENDS_PER_DAY) {
            throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
        }
    }

    public void cancel(Inviter by, UUID organizationId, UUID linkId) {
        cancel(by, organizationId, linkId, false);
    }

    /** RN-PRO-05: inativar o professor cancela o convite pendente de acesso, também pelo Coordenador. */
    public void cancelTeacher(Inviter by, UUID organizationId, UUID linkId) {
        cancel(by, organizationId, linkId, true);
    }

    private void cancel(Inviter by, UUID organizationId, UUID linkId, boolean teacherAccess) {
        orgTx.provision(organizationId, () -> {
            Target t = authorize(by, organizationId, linkId, teacherAccess);
            jdbc.sql("update account_invite set canceled_at = :now where link_id = :l and used_at is null and canceled_at is null")
                    .param("now", now()).param("l", linkId).update();
            jdbc.sql("update account_link set status = 'ENDED', ends_on = :d where id = :l").param("d", now().toLocalDate()).param("l", linkId).update();
            audit.record(organizationId, new AuditEntry(by.actor(), "invite.canceled", "account_link", linkId.toString(), null,
                    null, Map.of("accountId", t.accountId().toString()), null, null));
            return null;
        });
    }

    /** What the invite screen needs, and nothing about other accounts. */
    public record Info(String role, boolean existingAccount, UUID organizationId, String email, String name, OffsetDateTime expiresAt) {}

    /** State of a usable invite for the screen; any problem is INVITE_INVALID with the reason. */
    public Info describe(String code) {
        Found f = usable(code);
        return new Info(f.role(), f.passwordHash() != null, f.organizationId(), f.email(), f.name(), f.expiresAt());
    }

    private Found usable(String code) {
        Found f = accountTx.forInvite(Codes.hash(code), () -> jdbc.sql("select i.id, i.account_id, i.link_id, i.organization_id, "
                + "a.email, a.name, a.status, a.password_hash, i.expires_at, i.used_at, i.canceled_at, l.role "
                + "from account_invite i join account a on a.id = i.account_id join account_link l on l.id = i.link_id "
                + "where i.code_hash = :h")
                .param("h", Codes.hash(code))
                .query((rs, n) -> new Found(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        rs.getObject(9, OffsetDateTime.class), rs.getObject(10, OffsetDateTime.class), rs.getObject(11, OffsetDateTime.class),
                        rs.getString(12)))
                .optional().orElse(null));
        if (f == null) {
            throw new IdentityException(Code.INVITE_INVALID, "UNKNOWN");
        }
        if (f.usedAt() != null) throw new IdentityException(Code.INVITE_INVALID, "USED");
        if (f.canceledAt() != null) throw new IdentityException(Code.INVITE_INVALID, "CANCELED");
        if (!f.expiresAt().isAfter(now())) throw new IdentityException(Code.INVITE_INVALID, "EXPIRED");
        if (f.status().equals("DEACTIVATED")) throw new IdentityException(Code.INVITE_INVALID, "DEACTIVATED");
        return f;
    }

    /** RN-ACE-14: an account with a pending invite asks for a new password and gets the invite again (inside the daily limit). */
    public void resendToAccount(UUID accountId, String email) {
        UUID[] pending = accountTx.forAccount(accountId, () -> jdbc.sql("select l.id, l.organization_id from account_link l "
                + "join account_invite i on i.link_id = l.id where l.account_id = :a and l.status = 'PENDING' "
                + "order by i.created_at desc limit 1").param("a", accountId)
                .query((rs, n) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}).optional().orElse(null));
        if (pending == null) {
            return;
        }
        try {
            Issued issued = orgTx.provision(pending[1], () -> {
                checkResendLimit(accountId);
                return issue(Inviter.system(), pending[1], accountId, pending[0], true, "invite.resent");
            });
            mailer.invite(email, issued.code());
        } catch (IdentityException e) {
            if (e.code() != Code.TOO_MANY_REQUESTS) {
                throw e;
            }
        }
    }

    /**
     * Accepts with the invite code (RN-ACE-07): a new account sets its password (RN-ACE-08), an existing one proves it.
     * Single use is enforced by the conditional update; wrong password counts as a failed login (CA5).
     */
    public Accepted accept(String code, String password, String ip) {
        Found f = usable(code);
        String newHash = null;
        if (f.passwordHash() == null) {
            var violation = policy.check(password);
            if (violation != null) {
                throw new IdentityException(violation == PasswordPolicy.Violation.LEAKED ? Code.PASSWORD_LEAKED : Code.PASSWORD_WEAK, null);
            }
            newHash = encoder.encode(password);
        } else {
            if (attempts.check(f.email(), ip) != LoginAttempts.Status.OPEN) {
                throw new IdentityException(Code.TOO_MANY_REQUESTS, null);
            }
            if (password == null || !encoder.matches(password, f.passwordHash())) {
                attempts.record(f.email(), ip, LoginAttempts.Result.WRONG_PASSWORD);
                throw new IdentityException(Code.INVALID_CREDENTIALS, null);
            }
            attempts.record(f.email(), ip, LoginAttempts.Result.SUCCESS);
        }
        String hashToSet = newHash;
        accountTx.forAccount(f.accountId(), () -> {
            if (jdbc.sql("update account_invite set used_at = :now where id = :i and used_at is null and canceled_at is null and expires_at > :now")
                    .param("now", now()).param("i", f.inviteId()).update() == 0) {
                throw new IdentityException(Code.INVITE_INVALID, "USED");
            }
            jdbc.sql("update account_link set status = 'ACTIVE', starts_on = :d, ends_on = null where id = :l")
                    .param("d", now().toLocalDate()).param("l", f.linkId()).update();
            if (hashToSet != null && jdbc.sql("update account set password_hash = :h, status = 'ACTIVE' where id = :a and password_hash is null")
                    .param("h", hashToSet).param("a", f.accountId()).update() == 0) {
                throw new IdentityException(Code.INVITE_INVALID, "USED");
            }
            var actor = new AuditActor(AuditActor.Type.ACCOUNT, f.accountId(), null);
            audit.record(f.organizationId(), new AuditEntry(actor, "invite.accepted", "account_invite", f.inviteId().toString(),
                    null, null, Map.of("linkId", f.linkId().toString()), null, ip));
            audit.record(f.organizationId(), new AuditEntry(actor, "link.created", "account_link", f.linkId().toString(),
                    null, Map.of("status", "PENDING"), Map.of("status", "ACTIVE"), null, ip));
            return null;
        });
        return new Accepted(f.accountId(), f.linkId(), f.organizationId());
    }
}
