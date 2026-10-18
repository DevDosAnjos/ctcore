package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.IdentityException;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.identity.Role;
import br.com.ctcore.integration.MapLocator;
import br.com.ctcore.integration.MapLocator.Coordinates;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.UnitList.UnitSummary;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-03: cria a unidade (tudo ou nada, sob trava da organização) e, depois do commit, convida o Administrador. Só o Coordenador (FULL). */
@Service
public class UnitCreation {
    public enum Invite { NONE, SENT, FAILED }

    public record Result(UnitSummary unit, Invite invite) {}

    private static final int NAME_ATTEMPTS = 20;

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final MapLocator locator;
    private final InviteService invites;

    public UnitCreation(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit,
            MapLocator locator, InviteService invites) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.locator = locator;
        this.invites = invites;
    }

    public Result create(AccountPrincipal who, NewUnit request, String ip) {
        if (permissions.grant(who, Permission.UNITS, null) != Access.FULL) throw new AccessDeniedException("Permission denied");
        NewUnit unit = request.validated();
        Optional<Coordinates> found = unit.latitude() == null && unit.postalCode() != null ? locator.locate(unit.postalCode()) : Optional.empty();
        double[] coordinates = unit.latitude() != null ? new double[] {unit.latitude(), unit.longitude()}
                : found.map(c -> new double[] {c.latitude(), c.longitude()}).orElse(null);
        // O nome curto é único na plataforma, mas a RLS esconde os de outras organizações: a colisão volta como chave duplicada e tenta o próximo.
        Set<String> rejected = new HashSet<>();
        UnitSummary created = null;
        for (int attempt = 1; created == null; attempt++) {
            try {
                created = insert(who, unit, coordinates, rejected, ip);
            } catch (DuplicateKeyException e) {
                if (attempt >= NAME_ATTEMPTS) throw e;
            }
        }
        return new Result(created, invite(who, unit, created.id()));
    }

    private UnitSummary insert(AccountPrincipal who, NewUnit unit, double[] coordinates, Set<String> rejected, String ip) {
        UUID org = who.organizationId();
        String[] candidate = new String[1];
        try {
            return transactions.execute(() -> {
                jdbc.sql("select id from organization where id = :org for update").param("org", org).query(UUID.class).single();
                int count = jdbc.sql("select count(*) from unit where organization_id = :org").param("org", org).query(Integer.class).single();
                try {
                    Organizations.requireUnitCapacity(count, false);
                } catch (IllegalStateException e) {
                    throw new ApiStatusException(ErrorCode.UNIT_LIMIT_REACHED);
                }
                String shortName = ShortName.generate(unit.name(), n -> rejected.contains(n) || jdbc.sql(
                        "select exists(select 1 from unit_short_name_history where short_name = :n) or exists(select 1 from unit where short_name = :n)")
                        .param("n", n).query(Boolean.class).single());
                candidate[0] = shortName;
                UUID id = jdbc.sql("""
                        insert into unit (organization_id, name, short_name, postal_code, address, city, latitude, longitude, phone, email, whatsapp)
                        values (:org, :name, :short, :cep, :address, :city, :lat, :lon, :phone, :email, :whatsapp) returning id""")
                        .param("org", org).param("name", unit.name()).param("short", shortName).param("cep", unit.postalCode())
                        .param("address", unit.address()).param("city", unit.city())
                        .param("lat", coordinates == null ? null : BigDecimal.valueOf(coordinates[0]))
                        .param("lon", coordinates == null ? null : BigDecimal.valueOf(coordinates[1]))
                        .param("phone", unit.phone()).param("email", unit.email()).param("whatsapp", unit.whatsapp())
                        .query(UUID.class).single();
                jdbc.sql("insert into unit_settings (organization_id, unit_id) values (:org, :unit)").param("org", org).param("unit", id).update();
                jdbc.sql("insert into unit_short_name_history (short_name, organization_id, unit_id) values (:n, :org, :unit)")
                        .param("n", shortName).param("org", org).param("unit", id).update();
                Map<String, Object> after = new LinkedHashMap<>();
                after.put("name", unit.name());
                after.put("shortName", shortName);
                if (unit.city() != null) after.put("city", unit.city());
                audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "unit.create", "unit",
                        id.toString(), id, Map.of(), after, null, ip));
                return new UnitSummary(id, unit.name(), shortName, false, unit.city(), 0, List.of(), true);
            });
        } catch (DuplicateKeyException e) {
            if (candidate[0] != null) rejected.add(candidate[0]);
            throw e;
        }
    }

    private Invite invite(AccountPrincipal who, NewUnit unit, UUID unitId) {
        if (unit.administrator() == null) return Invite.NONE;
        try {
            invites.invite(new Inviter(who.accountId(), who.role(), Set.of()), who.organizationId(), unit.administrator().email(),
                    unit.administrator().name(), Role.ADMINISTRATOR, Set.of(unitId), null);
            return Invite.SENT;
        } catch (IdentityException | AccessDeniedException e) {
            return Invite.FAILED;
        }
    }
}
