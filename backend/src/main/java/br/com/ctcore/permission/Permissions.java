package br.com.ctcore.permission;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.sql.Array;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** "Este vínculo pode isto, nesta unidade?" (RNF-05): decide só pelo principal da sessão, nunca por dado do cliente. */
@Service
public class Permissions {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;

    public Permissions(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    private static boolean management(AccountPrincipal who) {
        return who != null && who.organizationId() != null && who.role() != null && who.role().management();
    }

    /** Nível do vínculo na permissão e na unidade (null: operação sem unidade). Fora do alcance, {@link Access#NONE}. */
    public Access grant(AccountPrincipal who, Permission permission, UUID unitId) {
        if (!management(who)) {
            return Access.NONE;
        }
        Grant grant = PermissionTable.grant(who.role(), permission);
        if (grant.access() == Access.NONE) {
            return Access.NONE;
        }
        if (unitId == null) {
            return grant.scope() == Scope.ALL ? grant.access() : Access.NONE;
        }
        boolean coordinator = who.role() == Role.COORDINATOR;
        if (!coordinator && !who.units().contains(unitId)) {
            return Access.NONE;
        }
        int flag = PermissionTable.teacherFlag(permission);
        boolean needsFlag = who.role() == Role.TEACHER && flag >= 0;
        if (!coordinator && !needsFlag) {
            return grant.access();
        }
        // RLS limita à organização do principal: unidade de outra organização não aparece.
        List<Boolean[]> rows = flagsOf(who, "unit_id = :u", unitId);
        if (rows.isEmpty() || (needsFlag && !rows.get(0)[flag])) {
            return Access.NONE;
        }
        return grant.access();
    }

    /** Recusa com 403 FORBIDDEN (AccessDeniedException) e nenhum efeito. */
    public void require(AccountPrincipal who, Permission permission, UUID unitId) {
        if (grant(who, permission, unitId) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    /** Todas as linhas para o menu; as do Professor com asterisco valem se ligadas em ao menos uma unidade (D4). */
    public Map<Permission, Access> effective(AccountPrincipal who) {
        Map<Permission, Access> result = new EnumMap<>(Permission.class);
        boolean teacher = management(who) && who.role() == Role.TEACHER;
        List<Boolean[]> flags = teacher && !who.units().isEmpty() ? flagsOf(who, "unit_id in (:u)", who.units()) : List.of();
        for (Permission p : Permission.values()) {
            Access access = management(who) ? PermissionTable.grant(who.role(), p).access() : Access.NONE;
            int flag = PermissionTable.teacherFlag(p);
            if (teacher && flag >= 0 && flags.stream().noneMatch(f -> f[flag])) {
                access = Access.NONE;
            }
            result.put(p, access);
        }
        return result;
    }

    private List<Boolean[]> flagsOf(AccountPrincipal who, String where, Object unit) {
        return transactions.provision(who.organizationId(), () -> jdbc
                .sql("select teacher_permissions from unit_settings where " + where).param("u", unit)
                .query((rs, i) -> flags(rs.getArray(1))).list());
    }

    private static Boolean[] flags(Array array) throws SQLException {
        return (Boolean[]) array.getArray();
    }
}
