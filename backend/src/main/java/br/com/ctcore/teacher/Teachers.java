package br.com.ctcore.teacher;

import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PRO-01: lista de professores da organização. Organização, perfil e alcance vêm da sessão; telefone e e-mail nunca saem
 * (RN-PRO-04). O Coordenador vê todos; o Administrador, os que dão aula em ao menos uma unidade dele (D7).
 */
@Service
public class Teachers {
    private static final Logger LOG = LoggerFactory.getLogger(Teachers.class);
    static final Set<String> SORT = Set.of("name");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
    /** Turmas só existem a partir da Sprint 05 (D4): ponto único a trocar por contagem real. */
    private static final int CLASS_COUNT = 0;

    public record UnitRef(UUID id, String name) {}

    public record ModalityRef(UUID id, String name, String level, Integer degree) {}

    public record Row(UUID id, String name, String photoUrl, List<ModalityRef> modalities, List<UnitRef> units, int classCount,
            String status, boolean directoryConsent, String access) {}

    private record Head(UUID id, String name, String photo, String status, boolean consent, String access) {}

    private record Loaded(long total, List<Head> heads, Map<UUID, List<UnitRef>> units, Map<UUID, List<ModalityRef>> modalities) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final FileStorage storage;

    public Teachers(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, FileStorage storage) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.storage = storage;
    }

    public PageResponse<Row> list(AccountPrincipal who, String q, UUID modalityId, UUID unitId, String status, PageRequest page) {
        if (who.role() == null || permissions.effective(who).get(Permission.TEACHERS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        String st = status == null || status.isBlank() ? null : status.strip().toUpperCase(Locale.ROOT);
        if (st != null && !STATUSES.contains(st)) throw new ApiValidationException("status", "valor inválido");
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && (reach.isEmpty() || unitId != null && !reach.contains(unitId))) return PageResponse.of(List.of(), page, 0);

        StringBuilder where = new StringBuilder("t.organization_id = :org");
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        if (q != null && !q.isBlank()) {
            where.append(" and t.name_key like :q escape '\\'");
            params.put("q", "%" + Modalities.normalize(q).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (modalityId != null) {
            where.append(" and exists (select 1 from teacher_modality tm where tm.teacher_id = t.id and tm.modality_id = :modality)");
            params.put("modality", modalityId);
        }
        if (unitId != null) {
            where.append(" and exists (select 1 from teacher_unit tu where tu.teacher_id = t.id and tu.unit_id = :unit)");
            params.put("unit", unitId);
        }
        if (st != null) {
            where.append(" and t.status = :status");
            params.put("status", st);
        }
        if (admin) {
            where.append(" and exists (select 1 from teacher_unit tr where tr.teacher_id = t.id and tr.unit_id in (:reach))");
            params.put("reach", reach);
        }
        String order = page.sort() != null && page.sort().desc() ? "t.name_key desc, t.name desc, t.id desc" : "t.name_key, t.name, t.id";

        Loaded loaded = transactions.execute(() -> {
            long total = jdbc.sql("select count(*) from teacher t where " + where).params(params).query(Long.class).single();
            List<Head> heads = jdbc.sql("select t.id, t.name, t.photo, t.status, t.directory_consent, coalesce(l.status, 'NONE') from teacher t "
                    + "left join account_link l on l.organization_id = t.organization_id and l.account_id = t.account_id and l.role = 'TEACHER' "
                    + "where " + where + " order by " + order + " limit :limit offset :offset")
                    .params(params).param("limit", page.size()).param("offset", page.offset())
                    .query((rs, n) -> new Head(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getBoolean(5), rs.getString(6))).list();
            List<UUID> ids = heads.stream().map(Head::id).toList();
            Map<UUID, List<UnitRef>> units = new HashMap<>();
            Map<UUID, List<ModalityRef>> modalities = new HashMap<>();
            if (!ids.isEmpty()) {
                jdbc.sql("select tu.teacher_id, u.id, u.name from teacher_unit tu join unit u on u.id = tu.unit_id "
                        + "where tu.organization_id = :org and tu.teacher_id in (:ids) order by u.name, u.id")
                        .param("org", who.organizationId()).param("ids", ids)
                        .query((rs, n) -> units.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                                .add(new UnitRef(rs.getObject(2, UUID.class), rs.getString(3)))).list();
                jdbc.sql("select tm.teacher_id, m.id, m.name, g.name, tm.degree from teacher_modality tm join modality m on m.id = tm.modality_id "
                        + "left join graduation_level g on g.id = tm.level_id where tm.organization_id = :org and tm.teacher_id in (:ids) order by m.name, m.id")
                        .param("org", who.organizationId()).param("ids", ids)
                        .query((rs, n) -> {
                            String level = rs.getString(4);
                            return modalities.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                                    .add(new ModalityRef(rs.getObject(2, UUID.class), rs.getString(3), level, level == null ? null : rs.getInt(5)));
                        }).list();
            }
            return new Loaded(total, heads, units, modalities);
        });
        List<Row> rows = loaded.heads().stream().map(h -> new Row(h.id(), h.name(), photoUrl(h.photo()),
                loaded.modalities().getOrDefault(h.id(), List.of()), loaded.units().getOrDefault(h.id(), List.of()), CLASS_COUNT,
                h.status(), h.consent(), h.access())).toList();
        return PageResponse.of(rows, page, loaded.total());
    }

    private String photoUrl(String photo) {
        if (photo == null) return null;
        try {
            return storage.readUrl(photo);
        } catch (FileStorage.NotFound e) {
            LOG.warn("Teacher photo file is missing from storage");
            return null;
        }
    }
}
