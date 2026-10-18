package br.com.ctcore.organization;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-05: unidades ativas ao alcance do vínculo (RN-ORG-08/09). Somente leitura; a organização é a do vínculo ativo. */
@Service
public class UnitScope {
    public record Reachable(UUID id, String name, String shortName) {}

    public record Result(boolean allUnits, List<Reachable> units) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;

    public UnitScope(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    public Result reachable(AccountPrincipal who) {
        if (who == null || who.organizationId() == null || who.role() == null || !who.role().management()) {
            throw new AccessDeniedException("Permission denied");
        }
        boolean all = permissions.grant(who, Permission.ALL_UNITS_CONSOLIDATED, null) != Access.NONE;
        List<Reachable> units = transactions.execute(() -> jdbc
                .sql("select id, name, short_name from unit where organization_id = :org and active order by name, id")
                .param("org", who.organizationId())
                .query((rs, i) -> new Reachable(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .list());
        // Coordenador alcança todas; os demais, só as do vínculo (a RLS já limitou à organização).
        return new Result(all, who.role() == Role.COORDINATOR ? units : units.stream().filter(u -> who.units().contains(u.id())).toList());
    }
}
