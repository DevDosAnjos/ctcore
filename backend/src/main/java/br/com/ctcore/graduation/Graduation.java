package br.com.ctcore.graduation;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** RF-MOD-02 (RN-GRA-01): sistema de graduação da organização por modalidade ativa; só o Coordenador grava. */
@Service
public class Graduation {
    static final int MAX_LEVELS = 30;
    static final int MAX_NAME = 40;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    public record Level(UUID id, String name, String color, String secondaryColor, int maxDegree, Integer minAge, Integer maxAge, boolean kids) {}

    public record LevelInput(UUID id, String name, String color, String secondaryColor, Integer maxDegree, Integer minAge, Integer maxAge,
            Boolean kids) {}

    public record View(boolean enabled, boolean requireOriginProof, boolean canManage, List<Level> levels) {}

    public record Update(Boolean enabled, Boolean requireOriginProof, List<LevelInput> levels) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final AuditService audit;

    public Graduation(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.audit = audit;
    }

    public View get(AccountPrincipal who, UUID modalityId) {
        Access access = access(who);
        return transactions.execute(() -> {
            boolean[] options = options(who, modalityId);
            return new View(options[0], options[1], access == Access.FULL, levels(who, modalityId));
        });
    }

    public View save(AccountPrincipal who, UUID modalityId, Update body, String ip) {
        if (access(who) != Access.FULL) throw new AccessDeniedException("Permission denied");
        List<LevelInput> input = validate(body);
        boolean enabled = body.enabled();
        boolean proof = Boolean.TRUE.equals(body.requireOriginProof());
        return transactions.execute(() -> {
            boolean[] before = options(who, modalityId);
            List<Level> current = levels(who, modalityId);
            Set<UUID> known = new HashSet<>();
            current.forEach(l -> known.add(l.id()));
            Set<UUID> kept = new HashSet<>();
            for (int i = 0; i < input.size(); i++) {
                UUID id = input.get(i).id();
                if (id != null && (!known.contains(id) || !kept.add(id))) throw new ApiValidationException("levels[" + i + "].id", "inválido");
            }
            List<Level> next = new ArrayList<>();
            for (LevelInput l : input) {
                next.add(new Level(l.id(), l.name().strip(), l.color(), blankToNull(l.secondaryColor()), l.maxDegree(), l.minAge(), l.maxAge(), l.kids()));
            }
            if (before[0] == enabled && before[1] == proof && next.equals(current)) return new View(enabled, proof, true, current);
            write(who, modalityId, enabled, proof, next, current, kept);
            List<Level> after = levels(who, modalityId);
            audit.record(new AuditEntry(actor(who), "modality.graduation_updated", "modality", modalityId.toString(), null,
                    snapshot(before[0], before[1], current), snapshot(enabled, proof, after), null, ip));
            return new View(enabled, proof, true, after);
        });
    }

    private void write(AccountPrincipal who, UUID modalityId, boolean enabled, boolean proof, List<Level> next, List<Level> current, Set<UUID> kept) {
        UUID org = who.organizationId();
        jdbc.sql("update organization_modality set graduation_enabled = :e, require_origin_proof = :p, updated_at = now() "
                + "where organization_id = :org and modality_id = :m").param("e", enabled).param("p", proof).param("org", org).param("m", modalityId).update();
        for (Level old : current) {
            if (!kept.contains(old.id())) jdbc.sql("delete from graduation_level where id = :id and organization_id = :org").param("id", old.id()).param("org", org).update();
        }
        // the name index is not deferrable: free the names first so swaps and renames do not collide halfway
        jdbc.sql("update graduation_level set name = id::text where organization_id = :org and modality_id = :m").param("org", org).param("m", modalityId).update();
        for (int i = 0; i < next.size(); i++) {
            Level l = next.get(i);
            UUID id = l.id() == null ? UUID.randomUUID() : l.id();
            var sql = l.id() == null
                    ? jdbc.sql("insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, min_age, max_age, kids) "
                            + "values (:id, :org, :m, :pos, :name, :color, :second, :max, :min, :maxAge, :kids)")
                    : jdbc.sql("update graduation_level set position = :pos, name = :name, color = :color, secondary_color = :second, max_degree = :max, "
                            + "min_age = :min, max_age = :maxAge, kids = :kids where id = :id and organization_id = :org and modality_id = :m");
            sql.param("id", id).param("org", org).param("m", modalityId).param("pos", i).param("name", l.name()).param("color", l.color())
                    .param("second", l.secondaryColor()).param("max", l.maxDegree()).param("min", l.minAge()).param("maxAge", l.maxAge())
                    .param("kids", l.kids()).update();
        }
    }

    private static Map<String, Object> snapshot(boolean enabled, boolean proof, List<Level> levels) {
        var map = new LinkedHashMap<String, Object>();
        map.put("enabled", enabled);
        map.put("requireOriginProof", proof);
        map.put("levels", levels.stream().map(Level::name).toList());
        return map;
    }

    private List<LevelInput> validate(Update body) {
        if (body == null || body.enabled() == null) throw new ApiValidationException("enabled", "obrigatório");
        if (body.levels() == null) throw new ApiValidationException("levels", "obrigatório");
        List<LevelInput> levels = body.levels();
        if (levels.size() > MAX_LEVELS) throw new ApiValidationException("levels", "no máximo 30 níveis");
        if (body.enabled() && levels.isEmpty()) throw new ApiValidationException("levels", "inclua ao menos um nível para ligar a graduação");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < levels.size(); i++) {
            LevelInput l = levels.get(i);
            if (l == null) throw new ApiValidationException("levels[" + i + "]", "obrigatório");
            String at = "levels[" + i + "].";
            String name = l.name() == null ? "" : l.name().strip();
            if (name.isEmpty() || name.length() > MAX_NAME) throw new ApiValidationException(at + "name", "de 1 a 40 caracteres");
            if (!names.add(Modalities.normalize(name))) throw new ApiValidationException(at + "name", "nome repetido");
            if (l.color() == null || !COLOR.matcher(l.color()).matches()) throw new ApiValidationException(at + "color", "use o formato #RRGGBB");
            String second = blankToNull(l.secondaryColor());
            if (second != null && !COLOR.matcher(second).matches()) throw new ApiValidationException(at + "secondaryColor", "use o formato #RRGGBB");
            if (l.maxDegree() == null || l.maxDegree() < 0 || l.maxDegree() > 10) throw new ApiValidationException(at + "maxDegree", "de 0 a 10");
            if (l.minAge() != null && (l.minAge() < 0 || l.minAge() > 120)) throw new ApiValidationException(at + "minAge", "de 0 a 120");
            if (l.maxAge() != null && (l.maxAge() < 0 || l.maxAge() > 120)) throw new ApiValidationException(at + "maxAge", "de 0 a 120");
            if (l.minAge() != null && l.maxAge() != null && l.minAge() > l.maxAge()) throw new ApiValidationException(at + "minAge", "maior que a idade máxima");
            if (l.kids() == null) throw new ApiValidationException(at + "kids", "obrigatório");
        }
        return levels;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Dentro da transação da organização: graduação ligada e comprovante exigido. 404 fora do catálogo, 409 se não está ativa. */
    private boolean[] options(AccountPrincipal who, UUID modalityId) {
        if (jdbc.sql("select count(*) from modality where id = :id").param("id", modalityId).query(Integer.class).single() == 0) {
            throw new ApiStatusException(ErrorCode.NOT_FOUND);
        }
        return jdbc.sql("select graduation_enabled, require_origin_proof from organization_modality where organization_id = :org and modality_id = :m and active")
                .param("org", who.organizationId()).param("m", modalityId)
                .query((rs, i) -> new boolean[] {rs.getBoolean(1), rs.getBoolean(2)}).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.MODALITY_NOT_ACTIVE));
    }

    private List<Level> levels(AccountPrincipal who, UUID modalityId) {
        return jdbc.sql("select id, name, color, secondary_color, max_degree, min_age, max_age, kids from graduation_level "
                + "where organization_id = :org and modality_id = :m order by position").param("org", who.organizationId()).param("m", modalityId)
                .query((rs, i) -> new Level(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5),
                        (Integer) rs.getObject(6), (Integer) rs.getObject(7), rs.getBoolean(8))).list();
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private Access access(AccountPrincipal who) {
        Access access = permissions.grant(who, Permission.ORGANIZATION_SETTINGS, null);
        if (access == Access.NONE) throw new AccessDeniedException("Permission denied");
        return access;
    }
}
