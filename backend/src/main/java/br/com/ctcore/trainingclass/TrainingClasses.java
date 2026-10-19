package br.com.ctcore.trainingclass;

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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-TUR-01: lista de turmas da organização. Organização, perfil e alcance vêm da sessão. O Coordenador vê todas as unidades;
 * o Administrador, as dele. Presença e frequência não existem aqui (RN-TUR-06).
 */
@Service
public class TrainingClasses {
    static final Set<String> SORT = Set.of("name");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");

    public record Filter(String q, UUID modalityId, UUID teacherId, UUID ageRangeId, Integer weekday, UUID unitId, String status) {}

    public record Ref(UUID id, String name) {}

    public record AgeRangeRef(UUID id, String name, int minAge, Integer maxAge) {}

    public record Slot(int weekday, String startsAt, String endsAt) {}

    public record Row(UUID id, String name, Ref unit, Ref modality, AgeRangeRef ageRange, List<Slot> schedule, Ref head,
            List<Ref> assistants, int activeStudents, Integer capacity, boolean overCapacity, String status) {}

    private record Head(UUID id, String name, Ref unit, Ref modality, AgeRangeRef ageRange, Ref head, Integer capacity, String status) {}

    private record Loaded(long total, List<Head> heads, Map<UUID, List<Slot>> schedule, Map<UUID, List<Ref>> assistants) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;

    public TrainingClasses(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    public PageResponse<Row> list(AccountPrincipal who, Filter f, PageRequest page) {
        if (who.role() == null || permissions.effective(who).get(Permission.CLASSES_MANAGE) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        String st = f.status() == null || f.status().isBlank() ? null : f.status().strip().toUpperCase(Locale.ROOT);
        if (st != null && !STATUSES.contains(st)) throw new ApiValidationException("status", "valor inválido");
        if (f.weekday() != null && (f.weekday() < 1 || f.weekday() > 7)) throw new ApiValidationException("weekday", "deve ser de 1 a 7");
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && (reach.isEmpty() || f.unitId() != null && !reach.contains(f.unitId()))) return PageResponse.of(List.of(), page, 0);

        StringBuilder where = new StringBuilder("c.organization_id = :org");
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        if (f.q() != null && !f.q().isBlank()) {
            where.append(" and c.name_key like :q escape '\\'");
            params.put("q", "%" + Modalities.normalize(f.q()).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (f.modalityId() != null) {
            where.append(" and c.modality_id = :modality");
            params.put("modality", f.modalityId());
        }
        if (f.teacherId() != null) {
            where.append(" and (c.head_teacher_id = :teacher or exists (select 1 from training_class_assistant a where a.class_id = c.id and a.teacher_id = :teacher))");
            params.put("teacher", f.teacherId());
        }
        if (f.ageRangeId() != null) {
            where.append(" and c.age_range_id = :ageRange");
            params.put("ageRange", f.ageRangeId());
        }
        if (f.weekday() != null) {
            where.append(" and exists (select 1 from training_class_schedule s where s.class_id = c.id and s.weekday = :weekday)");
            params.put("weekday", f.weekday());
        }
        if (f.unitId() != null) {
            where.append(" and c.unit_id = :unit");
            params.put("unit", f.unitId());
        }
        if (st != null) {
            where.append(" and c.status = :status");
            params.put("status", st);
        }
        if (admin) {
            where.append(" and c.unit_id in (:reach)");
            params.put("reach", reach);
        }
        String order = page.sort() != null && page.sort().desc() ? "c.name_key desc, c.name desc, c.id desc" : "c.name_key, c.name, c.id";

        Loaded loaded = transactions.execute(() -> {
            long total = jdbc.sql("select count(*) from training_class c where " + where).params(params).query(Long.class).single();
            List<Head> heads = jdbc.sql("select c.id, c.name, u.id, u.name, m.id, m.name, r.id, r.name, r.min_age, r.max_age, h.id, h.name, c.capacity, c.status "
                    + "from training_class c join unit u on u.id = c.unit_id join modality m on m.id = c.modality_id "
                    + "left join age_range r on r.id = c.age_range_id join teacher h on h.id = c.head_teacher_id "
                    + "where " + where + " order by " + order + " limit :limit offset :offset")
                    .params(params).param("limit", page.size()).param("offset", page.offset())
                    .query((rs, n) -> new Head(rs.getObject(1, UUID.class), rs.getString(2),
                            new Ref(rs.getObject(3, UUID.class), rs.getString(4)), new Ref(rs.getObject(5, UUID.class), rs.getString(6)),
                            rs.getObject(7, UUID.class) == null ? null
                                    : new AgeRangeRef(rs.getObject(7, UUID.class), rs.getString(8), rs.getInt(9), (Integer) rs.getObject(10)),
                            new Ref(rs.getObject(11, UUID.class), rs.getString(12)), (Integer) rs.getObject(13), rs.getString(14))).list();
            List<UUID> ids = heads.stream().map(Head::id).toList();
            Map<UUID, List<Slot>> schedule = new HashMap<>();
            Map<UUID, List<Ref>> assistants = new HashMap<>();
            if (!ids.isEmpty()) {
                jdbc.sql("select class_id, weekday, starts_at, ends_at from training_class_schedule where organization_id = :org and class_id in (:ids) order by weekday")
                        .param("org", who.organizationId()).param("ids", ids)
                        .query((rs, n) -> schedule.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                                .add(new Slot(rs.getInt(2), rs.getObject(3, LocalTime.class).toString(), rs.getObject(4, LocalTime.class).toString()))).list();
                jdbc.sql("select a.class_id, t.id, t.name from training_class_assistant a join teacher t on t.id = a.teacher_id "
                        + "where a.organization_id = :org and a.class_id in (:ids) order by t.name_key, t.id")
                        .param("org", who.organizationId()).param("ids", ids)
                        .query((rs, n) -> assistants.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                                .add(new Ref(rs.getObject(2, UUID.class), rs.getString(3)))).list();
            }
            return new Loaded(total, heads, schedule, assistants);
        });
        List<Row> rows = loaded.heads().stream().map(h -> {
            int students = activeStudents(h.id());
            return new Row(h.id(), h.name(), h.unit(), h.modality(), h.ageRange(), loaded.schedule().getOrDefault(h.id(), List.of()), h.head(),
                    loaded.assistants().getOrDefault(h.id(), List.of()), students, h.capacity(), overCapacity(students, h.capacity()), h.status());
        }).toList();
        return PageResponse.of(rows, page, loaded.total());
    }

    /** Alunos ativos da turma: 0 até a matrícula existir (Sprint 07, D5); é o único ponto a trocar. */
    int activeStudents(UUID classId) {
        return 0;
    }

    /** RN-TUR-02: a capacidade só avisa. */
    static boolean overCapacity(int activeStudents, Integer capacity) {
        return capacity != null && activeStudents > capacity;
    }
}
