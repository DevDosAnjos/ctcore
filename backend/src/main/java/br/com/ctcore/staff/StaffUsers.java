package br.com.ctcore.staff;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.IdentityException;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.identity.LinkService;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.Organizations;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-ACE-06: the management staff of the organization. The Coordinator manages Administrators, the Administrator manages
 * Teachers of his units (D2). Profile, organization and reach always come from the session (RNF-05); writes that count or
 * remove Administrators hold the organization row so two of them cannot leave a unit without one (RN-ORG-07).
 */
@Service
public class StaffUsers {
    public record UnitRef(UUID id, String name) {}

    public record Row(UUID linkId, String name, String email, String role, List<UnitRef> units, OffsetDateTime lastAccessAt,
            String status, boolean canManage) {}

    private record Found(Row row, UUID accountId, Set<UUID> unitIds, Role role) {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> OPEN = List.of("PENDING", "ACTIVE");
    private static final List<String> ALL = List.of("PENDING", "ACTIVE", "ENDED");

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final InviteService invites;
    private final LinkService links;
    private final AuditService audit;

    public StaffUsers(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, InviteService invites,
            LinkService links, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.invites = invites;
        this.links = links;
        this.audit = audit;
    }

    /** STAFF_USERS on the server (RF-ACE-06); returns the profile the caller manages. */
    private Role manager(AccountPrincipal who) {
        Role managed = who.role() == null ? null : StaffRules.managedRole(who.role());
        if (managed == null || permissions.effective(who).get(Permission.STAFF_USERS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        return managed;
    }

    private static ApiStatusException notFound() {
        return new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private void lock(AccountPrincipal who) {
        jdbc.sql("select id from organization where id = :org for update").param("org", who.organizationId()).query(UUID.class).single();
    }

    // ---- reading

    public List<Row> list(AccountPrincipal who, String status) {
        manager(who);
        List<String> statuses = status == null || status.isBlank() ? OPEN : List.of(status.strip().toUpperCase(Locale.ROOT));
        if (!ALL.containsAll(statuses)) throw new ApiValidationException("status", "valor inválido");
        return transactions.execute(() -> load(who, null, statuses).stream().map(Found::row).toList());
    }

    private record Head(UUID id, UUID account, String name, String email, String status, OffsetDateTime lastAccess) {}

    private List<Found> load(AccountPrincipal who, UUID only, List<String> statuses) {
        Role role = StaffRules.managedRole(who.role());
        var query = jdbc.sql("select l.id, a.id as account_id, a.name, a.email, l.status, a.last_access_at from account_link l "
                + "join account a on a.id = l.account_id where l.organization_id = :org and l.role = :role and l.status in (:st)"
                + (only == null ? "" : " and l.id = :id") + " order by lower(a.name), l.id")
                .param("org", who.organizationId()).param("role", role.name()).param("st", statuses);
        if (only != null) query = query.param("id", only);
        List<Head> heads = query.query((rs, n) -> new Head(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("name"), rs.getString("email"), rs.getString("status"), rs.getObject("last_access_at", OffsetDateTime.class))).list();
        Map<UUID, List<UnitRef>> units = new HashMap<>();
        if (!heads.isEmpty()) {
            jdbc.sql("select lu.link_id, u.id, u.name from account_link_unit lu join unit u on u.id = lu.unit_id "
                    + "where lu.organization_id = :org and lu.link_id in (:ids) order by u.name, u.id")
                    .param("org", who.organizationId()).param("ids", heads.stream().map(Head::id).toList())
                    .query((rs, n) -> units.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new UnitRef(rs.getObject(2, UUID.class), rs.getString(3)))).list();
        }
        List<Found> found = new ArrayList<>();
        for (Head h : heads) {
            List<UnitRef> refs = units.getOrDefault(h.id(), List.of());
            Set<UUID> ids = new LinkedHashSet<>(refs.stream().map(UnitRef::id).toList());
            if (!StaffRules.reaches(who.role(), who.units(), role, ids)) continue;
            boolean manage = StaffRules.canManage(who.role(), who.units(), role, ids);
            found.add(new Found(new Row(h.id(), h.name(), h.email(), role.name(), refs, h.lastAccess(), h.status(), manage), h.account(), ids, role));
        }
        return found;
    }

    /** R11: a link outside the organization, the profile or the reach is the same 404 as one that does not exist. */
    private Found find(AccountPrincipal who, UUID linkId, List<String> statuses) {
        List<Found> found = load(who, linkId, statuses);
        if (found.isEmpty()) throw notFound();
        return found.get(0);
    }

    // ---- inviting

    public Row invite(AccountPrincipal who, String email, String name, List<UUID> unitIds) {
        Role target = manager(who);
        if (email == null || !EMAIL.matcher(email.strip()).matches()) throw new ApiValidationException("email", "informe um e-mail válido");
        if (name == null || name.isBlank()) throw new ApiValidationException("name", "informe o nome");
        Set<UUID> units = units(unitIds);
        if (who.role() == Role.ADMINISTRATOR && !who.units().containsAll(units)) throw new AccessDeniedException("Unit out of reach");
        return transactions.execute(() -> {
            lock(who);
            requireActiveUnits(who, units);
            int staff = jdbc.sql("select count(*) from account_link where organization_id = :org and status in ('PENDING', 'ACTIVE') "
                    + "and role in ('COORDINATOR', 'ADMINISTRATOR', 'TEACHER')").param("org", who.organizationId()).query(Integer.class).single();
            try {
                Organizations.requireManagementUserCapacity(staff, false);
            } catch (IllegalStateException e) {
                throw new ApiStatusException(ErrorCode.STAFF_LIMIT_REACHED);
            }
            int linked = jdbc.sql("select count(*) from account_link l join account a on a.id = l.account_id where l.organization_id = :org "
                    + "and l.role = :role and l.status in ('PENDING', 'ACTIVE') and lower(btrim(a.email)) = lower(btrim(:e))")
                    .param("org", who.organizationId()).param("role", target.name()).param("e", email).query(Integer.class).single();
            if (linked > 0) throw new IdentityException(Code.ALREADY_LINKED, null);
            var issued = invites.invite(new Inviter(who.accountId(), who.role(), who.units()), who.organizationId(), email, name.strip(),
                    target, units, null);
            return find(who, issued.linkId(), ALL).row();
        });
    }

    private static Set<UUID> units(List<UUID> unitIds) {
        Set<UUID> units = unitIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(unitIds);
        units.remove(null);
        if (units.isEmpty()) throw new ApiValidationException("unitIds", "escolha ao menos uma unidade");
        return units;
    }

    private void requireActiveUnits(AccountPrincipal who, Set<UUID> units) {
        int active = jdbc.sql("select count(*) from unit where organization_id = :org and active and id in (:u)")
                .param("org", who.organizationId()).param("u", units).query(Integer.class).single();
        if (active != units.size()) throw new ApiValidationException("unitIds", "escolha unidades ativas da organização");
    }

    public void resend(AccountPrincipal who, UUID linkId) {
        manager(who);
        transactions.execute(() -> {
            Found f = find(who, linkId, List.of("PENDING"));
            if (!f.row().canManage()) throw notFound();
            return null;
        });
        invites.resend(new Inviter(who.accountId(), who.role(), who.units()), who.organizationId(), linkId);
    }

    // ---- units

    public Row changeUnits(AccountPrincipal who, UUID linkId, List<UUID> unitIds, String ip) {
        manager(who);
        Set<UUID> desired = units(unitIds);
        if (who.role() == Role.ADMINISTRATOR && !who.units().containsAll(desired)) throw new AccessDeniedException("Unit out of reach");
        return transactions.execute(() -> {
            lock(who);
            Found f = find(who, linkId, OPEN);
            requireActiveUnits(who, desired);
            Set<UUID> result = StaffRules.mergeUnits(who.role(), who.units(), f.unitIds(), desired);
            if (result.isEmpty()) throw new ApiValidationException("unitIds", "o vínculo não pode ficar sem unidade; para sair, desative");
            Set<UUID> removed = StaffRules.removed(f.unitIds(), result);
            if (f.role() == Role.ADMINISTRATOR && f.row().status().equals("ACTIVE")) requireOtherAdministrators(who, linkId, removed);
            if (!removed.isEmpty()) {
                jdbc.sql("delete from account_link_unit where link_id = :l and unit_id in (:u)").param("l", linkId).param("u", removed).update();
            }
            for (UUID unit : StaffRules.removed(result, f.unitIds())) {
                jdbc.sql("insert into account_link_unit (link_id, organization_id, unit_id) values (:l, :o, :u)")
                        .param("l", linkId).param("o", who.organizationId()).param("u", unit).update();
            }
            audit.record(new AuditEntry(actor(who), "link.units_changed", "account_link", linkId.toString(), null,
                    Map.of("unitIds", sorted(f.unitIds())), Map.of("unitIds", sorted(result)), null, ip));
            return find(who, linkId, OPEN).row();
        });
    }

    private static List<String> sorted(Set<UUID> ids) {
        return new TreeSet<>(ids).stream().map(UUID::toString).toList();
    }

    /** RN-ORG-07: losing these units may not leave an active unit without an active Administrator. */
    private void requireOtherAdministrators(AccountPrincipal who, UUID linkId, Set<UUID> losing) {
        if (losing.isEmpty()) return;
        Set<UUID> active = new LinkedHashSet<>(jdbc.sql("select id from unit where organization_id = :org and active and id in (:u)")
                .param("org", who.organizationId()).param("u", losing).query(UUID.class).list());
        Map<UUID, Integer> others = new HashMap<>();
        if (!active.isEmpty()) {
            jdbc.sql("select lu.unit_id, count(*) from account_link_unit lu join account_link l on l.id = lu.link_id "
                    + "where l.organization_id = :org and l.role = 'ADMINISTRATOR' and l.status = 'ACTIVE' and l.id <> :id and lu.unit_id in (:u) "
                    + "group by lu.unit_id").param("org", who.organizationId()).param("id", linkId).param("u", active)
                    .query((rs, n) -> others.put(rs.getObject(1, UUID.class), rs.getInt(2))).list();
        }
        Set<UUID> left = StaffRules.leftWithoutAdministrator(active, others);
        if (!left.isEmpty()) {
            throw new ApiStatusException(ErrorCode.LAST_UNIT_ADMINISTRATOR,
                    List.of(new ApiError.Field("unitIds", String.join(",", sorted(left)))));
        }
    }

    // ---- deactivating

    public void deactivate(AccountPrincipal who, UUID linkId) {
        manager(who);
        // O fim das sessões (RN-ACE-20) audita em outras organizações e por isso roda depois do commit desta.
        LinkService.Before ended = transactions.execute(() -> {
            lock(who);
            var target = jdbc.sql("select account_id, role from account_link where id = :id and organization_id = :org")
                    .param("id", linkId).param("org", who.organizationId())
                    .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2))).optional().orElseThrow(StaffUsers::notFound);
            if (target.getKey().equals(who.accountId())) throw new ApiStatusException(ErrorCode.CONFLICT);
            if (target.getValue().equals(Role.COORDINATOR.name())) throw new AccessDeniedException("The coordinator cannot be deactivated here");
            Found f = find(who, linkId, OPEN);
            if (!f.row().canManage()) throw new AccessDeniedException("Units out of reach");
            if (f.row().status().equals("PENDING")) {
                invites.cancel(new Inviter(who.accountId(), who.role(), who.units()), who.organizationId(), linkId);
                return null;
            }
            if (f.role() == Role.ADMINISTRATOR) requireOtherAdministrators(who, linkId, f.unitIds());
            return links.endInScope(actor(who), who.organizationId(), linkId);
        });
        if (ended != null) links.sessionsAfter(actor(who), ended);
    }

    // ---- the Coordinator names himself

    public Row appointCoordinator(AccountPrincipal who, List<UUID> unitIds, String ip) {
        if (who.role() != Role.COORDINATOR || permissions.effective(who).get(Permission.STAFF_USERS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        Set<UUID> units = units(unitIds);
        return transactions.execute(() -> {
            lock(who);
            requireActiveUnits(who, units);
            UUID linkId = jdbc.sql("select id from account_link where account_id = :a and organization_id = :o and role = 'ADMINISTRATOR'")
                    .param("a", who.accountId()).param("o", who.organizationId()).query(UUID.class).optional().orElse(null);
            Set<UUID> before = new LinkedHashSet<>();
            if (linkId == null) {
                linkId = jdbc.sql("insert into account_link (account_id, organization_id, role, status, starts_on) "
                        + "values (:a, :o, 'ADMINISTRATOR', 'ACTIVE', current_date) returning id")
                        .param("a", who.accountId()).param("o", who.organizationId()).query(UUID.class).single();
            } else {
                before.addAll(jdbc.sql("select unit_id from account_link_unit where link_id = :l").param("l", linkId).query(UUID.class).list());
                jdbc.sql("update account_link set status = 'ACTIVE', ends_on = null where id = :l").param("l", linkId).update();
            }
            for (UUID unit : units) {
                jdbc.sql("insert into account_link_unit (link_id, organization_id, unit_id) values (:l, :o, :u) on conflict do nothing")
                        .param("l", linkId).param("o", who.organizationId()).param("u", unit).update();
            }
            Set<UUID> after = new LinkedHashSet<>(before);
            after.addAll(units);
            audit.record(new AuditEntry(actor(who), "link.coordinator_appointed", "account_link", linkId.toString(), null,
                    Map.of("unitIds", sorted(before)), Map.of("unitIds", sorted(after)), null, ip));
            return find(who, linkId, ALL).row();
        });
    }
}
