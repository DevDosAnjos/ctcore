package br.com.ctcore.modality;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.UnitSettings;
import br.com.ctcore.permission.Access;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-MOD-03 (RN-MOD-02, RN-MOD-03): modalidades ativas da organização que a unidade oferece. Leitura e escrita pela linha UNIT_SETTINGS. */
@Service
public class UnitModalities {
    public record Item(UUID id, String name, boolean offered) {}

    public record Listing(boolean canManage, List<Item> items) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final UnitSettings units;
    private final AuditService audit;

    public UnitModalities(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, UnitSettings units, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.units = units;
        this.audit = audit;
    }

    public Listing list(AccountPrincipal who, UUID unitId) {
        Access access = units.access(who, unitId);
        if (access == Access.NONE) throw new AccessDeniedException("Permission denied");
        return new Listing(access == Access.FULL, transactions.execute(() -> jdbc.sql("""
                select m.id, m.name, coalesce(u.offered, false) from modality m
                join organization_modality o on o.modality_id = m.id and o.organization_id = :org and o.active
                left join unit_modality u on u.modality_id = m.id and u.unit_id = :unit and u.organization_id = :org
                order by m.name""").param("org", who.organizationId()).param("unit", unitId)
                .query((rs, i) -> new Item(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3))).list()));
    }

    public Item setOffered(AccountPrincipal who, UUID unitId, UUID modalityId, Boolean offered, String ip) {
        if (units.access(who, unitId) != Access.FULL) throw new AccessDeniedException("Permission denied");
        if (offered == null) throw new ApiValidationException("offered", "obrigatório");
        return transactions.execute(() -> {
            String name = jdbc.sql("select m.name from modality m join organization_modality o on o.modality_id = m.id "
                    + "where m.id = :id and o.organization_id = :org and o.active").param("id", modalityId).param("org", who.organizationId())
                    .query(String.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            boolean current = jdbc.sql("select offered from unit_modality where unit_id = :unit and modality_id = :id")
                    .param("unit", unitId).param("id", modalityId).query(Boolean.class).optional().orElse(false);
            if (current && !offered) refuseIfClasses(unitId, modalityId);
            if (current != offered) {
                jdbc.sql("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (:org, :unit, :id, :offered) "
                        + "on conflict (unit_id, modality_id) do update set offered = :offered, updated_at = now()")
                        .param("org", who.organizationId()).param("unit", unitId).param("id", modalityId).param("offered", offered).update();
                audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()),
                        offered ? "unit.modality.offered" : "unit.modality.withdrawn", "unit_modality", modalityId.toString(), unitId,
                        Map.of("offered", current), Map.of("offered", offered), null, ip));
            }
            return new Item(modalityId, name, offered);
        });
    }

    /** RN-MOD-06: desmarcar na unidade exige que não haja turma ativa da modalidade nela; a recusa lista as turmas. */
    private void refuseIfClasses(UUID unitId, UUID modalityId) {
        List<ApiError.Field> classes = jdbc.sql("select name from training_class where unit_id = :unit and modality_id = :id and status = 'ACTIVE' order by name, id")
                .param("unit", unitId).param("id", modalityId).query((rs, i) -> new ApiError.Field("class", rs.getString(1))).list();
        if (!classes.isEmpty()) throw new ApiStatusException(ErrorCode.UNIT_MODALITY_IN_USE, classes);
    }
}
