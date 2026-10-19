package br.com.ctcore.teacher;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
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

    public record ModalityRef(UUID id, String name, UUID levelId, String level, Integer degree) {}

    public record Row(UUID id, String name, String photoUrl, List<ModalityRef> modalities, List<UnitRef> units, int classCount,
            String status, boolean directoryConsent, String access) {}

    /** Turmas e aulas (D1): sem tipo próprio até a Sprint 05; hoje a lista é sempre vazia. */
    public record ClassRef(UUID id, String name, String role) {}

    public record UpcomingClass(UUID classId, String name, java.time.OffsetDateTime startsAt) {}

    public record Detail(UUID id, String name, String photoUrl, String bio, String email, String phone, boolean directoryConsent, boolean consentDeclaredByManagement,
            String status, List<ModalityRef> modalities, List<UnitRef> units, List<ClassRef> classes,
            List<UpcomingClass> upcomingClasses, String access) {}

    record Basic(UUID id, String name, String photo, String bio, String email, String phone, boolean consent, boolean declared, String status,
            UUID accountId, String access) {}

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
        requireAccess(who);
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
            refs(who.organizationId(), ids, units, modalities);
            return new Loaded(total, heads, units, modalities);
        });
        List<Row> rows = loaded.heads().stream().map(h -> new Row(h.id(), h.name(), photoUrl(h.photo()),
                loaded.modalities().getOrDefault(h.id(), List.of()), loaded.units().getOrDefault(h.id(), List.of()), CLASS_COUNT,
                h.status(), h.consent(), h.access())).toList();
        return PageResponse.of(rows, page, loaded.total());
    }

    private void refs(UUID org, List<UUID> ids, Map<UUID, List<UnitRef>> units, Map<UUID, List<ModalityRef>> modalities) {
        if (ids.isEmpty()) return;
        jdbc.sql("select tu.teacher_id, u.id, u.name from teacher_unit tu join unit u on u.id = tu.unit_id "
                + "where tu.organization_id = :org and tu.teacher_id in (:ids) order by u.name, u.id")
                .param("org", org).param("ids", ids)
                .query((rs, n) -> units.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new UnitRef(rs.getObject(2, UUID.class), rs.getString(3)))).list();
        // RF-MOD-02: com a graduação da modalidade desligada o nível e o grau gravados ficam, mas não saem (volta ao religar)
        jdbc.sql("select tm.teacher_id, m.id, m.name, g.name, tm.degree, tm.level_id from teacher_modality tm join modality m on m.id = tm.modality_id "
                + "left join organization_modality om on om.organization_id = tm.organization_id and om.modality_id = tm.modality_id "
                + "left join graduation_level g on g.id = tm.level_id and coalesce(om.graduation_enabled, false) "
                + "where tm.organization_id = :org and tm.teacher_id in (:ids) order by m.name, m.id")
                .param("org", org).param("ids", ids)
                .query((rs, n) -> {
                    String level = rs.getString(4);
                    return modalities.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new ModalityRef(rs.getObject(2, UUID.class), rs.getString(3), level == null ? null : rs.getObject(6, UUID.class), level, level == null ? null : rs.getInt(5)));
                }).list();
    }

    /** RF-PRO-02: o painel. Contato só aqui (D2). Turmas e aulas vêm vazias até a Sprint 05 (D1): ponto único abaixo. */
    public Detail get(AccountPrincipal who, UUID id) {
        requireAccess(who);
        return transactions.execute(() -> detail(who, id));
    }

    void requireAccess(AccountPrincipal who) {
        if (who.role() == null || permissions.effective(who).get(Permission.TEACHERS) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    /** Linha do professor dentro do alcance de quem chama; fora dele, é o mesmo 404 de quem não existe. Roda dentro da transação da organização. */
    Basic find(AccountPrincipal who, UUID id) {
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        String scope = admin ? " and exists (select 1 from teacher_unit tr where tr.teacher_id = t.id and tr.unit_id in (:reach))" : "";
        var query = jdbc.sql("select t.id, t.name, t.photo, t.bio, t.email, t.phone, t.directory_consent, t.consent_declared_by_management, t.status, t.account_id, "
                + "coalesce(l.status, 'NONE') from teacher t left join account_link l on l.organization_id = t.organization_id "
                + "and l.account_id = t.account_id and l.role = 'TEACHER' where t.organization_id = :org and t.id = :id" + scope)
                .param("org", who.organizationId()).param("id", id);
        if (admin) query = query.param("reach", reach);
        return query.query((rs, n) -> new Basic(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getBoolean(7), rs.getBoolean(8), rs.getString(9), rs.getObject(10, UUID.class), rs.getString(11)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    Detail detail(AccountPrincipal who, UUID id) {
        Basic b = find(who, id);
        Map<UUID, List<UnitRef>> units = new HashMap<>();
        Map<UUID, List<ModalityRef>> modalities = new HashMap<>();
        refs(who.organizationId(), List.of(id), units, modalities);
        return new Detail(b.id(), b.name(), photoUrl(b.photo()), b.bio(), b.email(), b.phone(), b.consent(), b.declared(), b.status(),
                modalities.getOrDefault(id, List.of()), units.getOrDefault(id, List.of()), headClasses(id), List.of(), b.access());
    }

    /** RN-PRO-05: turmas em que o professor é titular. Turmas só nascem na Sprint 05 (D1): ponto único a trocar. */
    List<ClassRef> headClasses(UUID teacherId) {
        return List.of();
    }

    String photoUrl(String photo) {
        if (photo == null) return null;
        try {
            return storage.readUrl(photo);
        } catch (FileStorage.NotFound e) {
            LOG.warn("Teacher photo file is missing from storage");
            return null;
        }
    }
}
