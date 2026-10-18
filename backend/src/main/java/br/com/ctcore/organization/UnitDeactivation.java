package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.CriticalAction;
import br.com.ctcore.identity.CriticalActionGuard;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-07 (RN-ORG-11, D1..D5): o Coordenador desativa uma unidade sem impedimentos; nada é apagado. */
@Service
public class UnitDeactivation {
    public record Check(int activeStudents, int activeClasses, boolean lastActiveUnit, boolean canDeactivate) {}

    private record Row(String name, boolean active) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final CriticalActionGuard guard;
    private final AuditService audit;
    private final UnitDeactivationBlockers blockers;

    public UnitDeactivation(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions,
            CriticalActionGuard guard, AuditService audit, UnitDeactivationBlockers blockers) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.guard = guard;
        this.audit = audit;
        this.blockers = blockers;
    }

    /** D2: o nome da unidade, sem diferenciar maiúsculas nem espaços nas pontas. */
    static boolean matches(String confirmation, String name) {
        return confirmation != null && !confirmation.isBlank()
                && name.strip().toLowerCase(Locale.ROOT).equals(confirmation.strip().toLowerCase(Locale.ROOT));
    }

    public Check check(AccountPrincipal who, UUID unitId) {
        requireFull(who);
        return transactions.execute(() -> {
            find(who, unitId);
            return state(who, unitId);
        });
    }

    public void deactivate(AccountPrincipal who, UUID unitId, String confirmation, String reauthToken, String ip) {
        requireFull(who);
        transactions.execute(() -> {
            Row row = find(who, unitId);
            if (!row.active()) throw new ApiStatusException(ErrorCode.CONFLICT);
            if (!matches(confirmation, row.name())) throw new ApiValidationException("confirmation", "digite o nome da unidade");
            refuseIfBlocked(state(who, unitId));
            return null;
        });
        guard.consume(who, CriticalAction.DEACTIVATE_UNIT, reauthToken);
        transactions.execute(() -> {
            // Trava a organização e reconfere: duas desativações simultâneas não deixam a organização sem unidade ativa.
            jdbc.sql("select id from organization where id = :org for update").param("org", who.organizationId()).query(UUID.class).single();
            Row row = find(who, unitId);
            if (!row.active()) throw new ApiStatusException(ErrorCode.CONFLICT);
            refuseIfBlocked(state(who, unitId));
            jdbc.sql("update unit set active = false where id = :id and organization_id = :org")
                    .param("id", unitId).param("org", who.organizationId()).update();
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "unit.deactivated",
                    "unit", unitId.toString(), unitId, Map.of("active", true), Map.of("active", false), null, ip));
            return null;
        });
    }

    private void refuseIfBlocked(Check check) {
        if (check.canDeactivate()) return;
        throw new ApiStatusException(ErrorCode.UNIT_HAS_BLOCKERS, List.of(
                new ApiError.Field("activeStudents", String.valueOf(check.activeStudents())),
                new ApiError.Field("activeClasses", String.valueOf(check.activeClasses())),
                new ApiError.Field("lastActiveUnit", String.valueOf(check.lastActiveUnit()))));
    }

    private Check state(AccountPrincipal who, UUID unitId) {
        int students = blockers.activeStudents(unitId);
        int classes = blockers.activeClasses(unitId);
        int others = jdbc.sql("select count(*) from unit where organization_id = :org and active and id <> :id")
                .param("org", who.organizationId()).param("id", unitId).query(Integer.class).single();
        boolean last = others == 0;
        return new Check(students, classes, last, students == 0 && classes == 0 && !last);
    }

    private Row find(AccountPrincipal who, UUID unitId) {
        return jdbc.sql("select name, active from unit where id = :id and organization_id = :org")
                .param("id", unitId).param("org", who.organizationId()).query((rs, i) -> new Row(rs.getString(1), rs.getBoolean(2)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private void requireFull(AccountPrincipal who) {
        if (permissions.grant(who, Permission.UNITS, null) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }
}
