package br.com.ctcore.organization;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-ORG-02: lista de unidades da organização do vínculo ativo e ajuste único do nome curto (RN-ORG-15). Só o Coordenador (FULL). */
@Service
public class UnitList {
    public record Administrator(String name) {}

    public record UnitSummary(UUID id, String name, String shortName, boolean shortNameAdjusted, String city, int activeStudents,
            List<Administrator> administrators, boolean active) {}

    private record Row(UUID id, String name, String shortName, boolean adjusted, String city, boolean active) {}

    private static final String COLUMNS = "select id, name, short_name, short_name_adjusted, city, active from unit ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;
    private final Clock clock;

    public UnitList(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    public List<UnitSummary> list(AccountPrincipal who) {
        requireFull(who);
        return transactions.execute(() -> {
            Map<UUID, List<Administrator>> admins = administrators(who.organizationId());
            return jdbc.sql(COLUMNS + "where organization_id = :org order by name, id")
                    .param("org", who.organizationId()).query(UnitList::row).list().stream()
                    .map(r -> summary(r, admins)).toList();
        });
    }

    public UnitSummary adjustShortName(AccountPrincipal who, UUID unitId, String requested, String ip) {
        requireFull(who);
        String shortName;
        try {
            shortName = ShortName.validate(requested);
        } catch (IllegalArgumentException e) {
            throw new ApiValidationException("shortName", e.getMessage());
        }
        return transactions.execute(() -> {
            Row before = jdbc.sql(COLUMNS + "where id = :id and organization_id = :org")
                    .param("id", unitId).param("org", who.organizationId()).query(UnitList::row).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (before.adjusted()) throw new ApiStatusException(ErrorCode.CONFLICT);
            // Nome de outra organização não aparece aqui (RLS): a chave primária da história e unique(short_name) recusam com 409.
            boolean taken = jdbc.sql("select exists(select 1 from unit_short_name_history where short_name = :n) "
                    + "or exists(select 1 from unit where short_name = :n)").param("n", shortName).query(Boolean.class).single();
            if (taken) throw new ApiStatusException(ErrorCode.CONFLICT);
            jdbc.sql("""
                    insert into unit_short_name_history (short_name, organization_id, unit_id, replaced_at) values (:old, :org, :unit, :now)
                    on conflict (short_name) do update set replaced_at = excluded.replaced_at""")
                    .param("old", before.shortName()).param("org", who.organizationId()).param("unit", unitId)
                    .param("now", OffsetDateTime.now(clock)).update();
            jdbc.sql("insert into unit_short_name_history (short_name, organization_id, unit_id) values (:n, :org, :unit)")
                    .param("n", shortName).param("org", who.organizationId()).param("unit", unitId).update();
            jdbc.sql("update unit set short_name = :n, short_name_adjusted = true where id = :id and organization_id = :org")
                    .param("n", shortName).param("id", unitId).param("org", who.organizationId()).update();
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "unit.short_name.update",
                    "unit", unitId.toString(), unitId, Map.of("shortName", before.shortName()), Map.of("shortName", shortName), null, ip));
            return summary(new Row(unitId, before.name(), shortName, true, before.city(), before.active()), administrators(who.organizationId()));
        });
    }

    private void requireFull(AccountPrincipal who) {
        if (permissions.grant(who, Permission.UNITS, null) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }

    private Map<UUID, List<Administrator>> administrators(UUID organizationId) {
        Map<UUID, List<Administrator>> byUnit = new HashMap<>();
        jdbc.sql("""
                select lu.unit_id, coalesce(a.name, a.email) from account_link l
                join account_link_unit lu on lu.link_id = l.id and lu.organization_id = l.organization_id
                join account a on a.id = l.account_id
                where l.organization_id = :org and l.role = 'ADMINISTRATOR' and l.status = 'ACTIVE'
                order by a.name, a.id""").param("org", organizationId)
                .query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), new Administrator(rs.getString(2)))).list()
                .forEach(e -> byUnit.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return byUnit;
    }

    // ponytail: sem tabela de alunos até a Sprint de cadastro (D2); trocar por count de alunos ativos da unidade.
    private static int activeStudents(UUID unitId) {
        return 0;
    }

    private static UnitSummary summary(Row r, Map<UUID, List<Administrator>> admins) {
        return new UnitSummary(r.id(), r.name(), r.shortName(), r.adjusted(), r.city(), activeStudents(r.id()),
                admins.getOrDefault(r.id(), List.of()), r.active());
    }

    private static Row row(ResultSet rs, int i) throws SQLException {
        return new Row(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5), rs.getBoolean(6));
    }
}
