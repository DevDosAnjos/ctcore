package br.com.ctcore.agerange;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-MOD-04 (RN-MOD-07, RN-ORG-17): faixas etárias administrativas da organização. */
@Service
public class AgeRanges {
    static final int MIN_NAME = 2;
    static final int MAX_NAME = 40;
    static final int MAX_AGE = 120;

    public record Item(UUID id, String name, int minAge, Integer maxAge) {}

    public record Listing(boolean canManage, List<Item> items) {}

    private record Input(String name, int min, Integer max) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;

    public AgeRanges(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
    }

    public Listing list(AccountPrincipal who) {
        boolean manage = access(who) == Access.FULL;
        return new Listing(manage, transactions.execute(() -> jdbc.sql("select id, name, min_age, max_age from age_range "
                + "where organization_id = :org order by min_age, name").param("org", who.organizationId()).query(AgeRanges::map).list()));
    }

    public Item create(AccountPrincipal who, String name, Integer minAge, Integer maxAge, String ip) {
        requireFull(who);
        var input = validate(name, minAge, maxAge);
        UUID id = UUID.randomUUID();
        return transactions.execute(() -> {
            refuseTakenName(who, input.name(), null);
            try {
                jdbc.sql("insert into age_range (id, organization_id, name, name_key, min_age, max_age) values (:id, :org, :name, :key, :min, :max)")
                        .param("id", id).param("org", who.organizationId()).param("name", input.name())
                        .param("key", Modalities.normalize(input.name())).param("min", input.min()).param("max", input.max()).update();
            } catch (DuplicateKeyException e) {
                throw new ApiStatusException(ErrorCode.AGE_RANGE_NAME_TAKEN);
            }
            audit.record(new AuditEntry(actor(who), "age_range.created", "age_range", id.toString(), null, Map.of(), snapshot(input), null, ip));
            return new Item(id, input.name(), input.min(), input.max());
        });
    }

    public Item update(AccountPrincipal who, UUID id, String name, Integer minAge, Integer maxAge, String ip) {
        requireFull(who);
        var input = validate(name, minAge, maxAge);
        return transactions.execute(() -> {
            Item current = find(who, id);
            var before = snapshot(new Input(current.name(), current.minAge(), current.maxAge()));
            var after = snapshot(input);
            if (before.equals(after)) return current;
            refuseTakenName(who, input.name(), id);
            try {
                jdbc.sql("update age_range set name = :name, name_key = :key, min_age = :min, max_age = :max, updated_at = now() "
                        + "where id = :id and organization_id = :org").param("name", input.name()).param("key", Modalities.normalize(input.name()))
                        .param("min", input.min()).param("max", input.max()).param("id", id).param("org", who.organizationId()).update();
            } catch (DuplicateKeyException e) {
                throw new ApiStatusException(ErrorCode.AGE_RANGE_NAME_TAKEN);
            }
            audit.record(new AuditEntry(actor(who), "age_range.updated", "age_range", id.toString(), null, before, after, null, ip));
            return new Item(id, input.name(), input.min(), input.max());
        });
    }

    public void delete(AccountPrincipal who, UUID id, String ip) {
        requireFull(who);
        transactions.execute(() -> {
            Item current = find(who, id);
            jdbc.sql("delete from age_range where id = :id and organization_id = :org").param("id", id).param("org", who.organizationId()).update();
            audit.record(new AuditEntry(actor(who), "age_range.deleted", "age_range", id.toString(), null,
                    snapshot(new Input(current.name(), current.minAge(), current.maxAge())), Map.of(), null, ip));
            return null;
        });
    }

    private static Input validate(String name, Integer min, Integer max) {
        String newName = name == null ? "" : name.strip();
        if (newName.length() < MIN_NAME || newName.length() > MAX_NAME) throw new ApiValidationException("name", "de 2 a 40 caracteres");
        if (min == null) throw new ApiValidationException("minAge", "obrigatório");
        if (min < 0 || min > MAX_AGE) throw new ApiValidationException("minAge", "de 0 a 120");
        if (max != null && (max < min || max > MAX_AGE)) throw new ApiValidationException("maxAge", "da idade mínima a 120");
        return new Input(newName, min, max);
    }

    private static Map<String, Object> snapshot(Input i) {
        var m = new LinkedHashMap<String, Object>();
        m.put("name", i.name());
        m.put("minAge", i.min());
        m.put("maxAge", i.max());
        return m;
    }

    private static Item map(ResultSet rs, int row) throws SQLException {
        Integer max = rs.getObject(4, Integer.class);
        return new Item(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), max);
    }

    private Item find(AccountPrincipal who, UUID id) {
        return jdbc.sql("select id, name, min_age, max_age from age_range where id = :id and organization_id = :org")
                .param("id", id).param("org", who.organizationId()).query(AgeRanges::map).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    /** Nome único por organização, sem acento nem diferença de maiúsculas; {@code except} é a própria faixa ao alterar. */
    private void refuseTakenName(AccountPrincipal who, String name, UUID except) {
        var owner = jdbc.sql("select id from age_range where organization_id = :org and name_key = :key")
                .param("org", who.organizationId()).param("key", Modalities.normalize(name)).query(UUID.class).optional();
        if (owner.isPresent() && !owner.get().equals(except)) throw new ApiStatusException(ErrorCode.AGE_RANGE_NAME_TAKEN);
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private Access access(AccountPrincipal who) {
        Access access = permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null);
        if (access == Access.NONE) throw new AccessDeniedException("Permission denied");
        return access;
    }

    private void requireFull(AccountPrincipal who) {
        if (access(who) != Access.FULL) throw new AccessDeniedException("Permission denied");
    }
}
