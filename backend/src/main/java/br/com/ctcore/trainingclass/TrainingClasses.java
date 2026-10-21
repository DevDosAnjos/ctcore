package br.com.ctcore.trainingclass;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Clock;
import java.time.LocalDate;
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

    public record NextClass(String date, String startsAt, String endsAt) {}

    public record Student(UUID id, String name, boolean primary) {}

    public record Deactivation(boolean allowed, int blockingStudents) {}

    public record Detail(UUID id, String name, String status, Ref unit, Ref modality, AgeRangeRef ageRange, List<Slot> schedule, Ref head,
            List<Ref> assistants, Integer capacity, int activeStudents, boolean overCapacity, String groupLink, NextClass nextClass,
            List<Student> students, Deactivation deactivation,
            @JsonInclude(JsonInclude.Include.NON_NULL) Boolean scheduleChanged) {
        Detail withScheduleChanged(boolean changed) {
            return new Detail(id, name, status, unit, modality, ageRange, schedule, head, assistants, capacity, activeStudents, overCapacity,
                    groupLink, nextClass, students, deactivation, changed);
        }
    }

    private record Head(UUID id, String name, Ref unit, Ref modality, AgeRangeRef ageRange, Ref head, Integer capacity, String status) {}

    private record Loaded(long total, List<Head> heads, Map<UUID, List<Slot>> schedule, Map<UUID, List<Ref>> assistants, Map<UUID, Integer> students) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Clock clock;

    public TrainingClasses(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
    }

    public PageResponse<Row> list(AccountPrincipal who, Filter f, PageRequest page) {
        requireAccess(who);
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
            children(who.organizationId(), ids, schedule, assistants);
            Map<UUID, Integer> counts = new HashMap<>();
            heads.forEach(h -> counts.put(h.id(), activeStudents(h.id())));
            return new Loaded(total, heads, schedule, assistants, counts);
        });
        List<Row> rows = loaded.heads().stream().map(h -> {
            int students = loaded.students().get(h.id());
            return new Row(h.id(), h.name(), h.unit(), h.modality(), h.ageRange(), loaded.schedule().getOrDefault(h.id(), List.of()), h.head(),
                    loaded.assistants().getOrDefault(h.id(), List.of()), students, h.capacity(), overCapacity(students, h.capacity()), h.status());
        }).toList();
        return PageResponse.of(rows, page, loaded.total());
    }

    /** Horários (por dia) e auxiliares das turmas; roda dentro da transação da organização. */
    private void children(UUID org, List<UUID> ids, Map<UUID, List<Slot>> schedule, Map<UUID, List<Ref>> assistants) {
        if (ids.isEmpty()) return;
        jdbc.sql("select class_id, weekday, starts_at, ends_at from training_class_schedule where organization_id = :org and class_id in (:ids) order by weekday")
                .param("org", org).param("ids", ids)
                .query((rs, n) -> schedule.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new Slot(rs.getInt(2), rs.getObject(3, LocalTime.class).toString(), rs.getObject(4, LocalTime.class).toString()))).list();
        jdbc.sql("select a.class_id, t.id, t.name from training_class_assistant a join teacher t on t.id = a.teacher_id "
                + "where a.organization_id = :org and a.class_id in (:ids) order by t.name_key, t.id")
                .param("org", org).param("ids", ids)
                .query((rs, n) -> assistants.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                        .add(new Ref(rs.getObject(2, UUID.class), rs.getString(3)))).list();
    }

    void requireAccess(AccountPrincipal who) {
        if (who.role() == null || permissions.effective(who).get(Permission.CLASSES_MANAGE) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    /** RF-TUR-02: painel da turma. Fora do alcance do Administrador ou de outra organização, 404. */
    public Detail get(AccountPrincipal who, UUID id) {
        requireAccess(who);
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        return transactions.execute(() -> {
            var query = jdbc.sql("select c.id, c.name, u.id, u.name, m.id, m.name, r.id, r.name, r.min_age, r.max_age, h.id, h.name, c.capacity, c.status "
                    + "from training_class c join unit u on u.id = c.unit_id join modality m on m.id = c.modality_id "
                    + "left join age_range r on r.id = c.age_range_id join teacher h on h.id = c.head_teacher_id "
                    + "where c.organization_id = :org and c.id = :id" + (admin ? " and c.unit_id in (:reach)" : ""))
                    .param("org", who.organizationId()).param("id", id);
            if (admin) query = query.param("reach", reach);
            Head h = query.query((rs, n) -> new Head(rs.getObject(1, UUID.class), rs.getString(2),
                    new Ref(rs.getObject(3, UUID.class), rs.getString(4)), new Ref(rs.getObject(5, UUID.class), rs.getString(6)),
                    rs.getObject(7, UUID.class) == null ? null
                            : new AgeRangeRef(rs.getObject(7, UUID.class), rs.getString(8), rs.getInt(9), (Integer) rs.getObject(10)),
                    new Ref(rs.getObject(11, UUID.class), rs.getString(12)), (Integer) rs.getObject(13), rs.getString(14)))
                    .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            String groupLink = jdbc.sql("select group_link from training_class where id = :id and organization_id = :org")
                    .param("id", id).param("org", who.organizationId()).query(String.class).optional().orElse(null);
            Map<UUID, List<Slot>> schedule = new HashMap<>();
            Map<UUID, List<Ref>> assistants = new HashMap<>();
            children(who.organizationId(), List.of(id), schedule, assistants);
            int students = activeStudents(id);
            int blocking = primaryStudents(id);
            return new Detail(h.id(), h.name(), h.status(), h.unit(), h.modality(), h.ageRange(), schedule.getOrDefault(id, List.of()), h.head(),
                    assistants.getOrDefault(id, List.of()), h.capacity(), students, overCapacity(students, h.capacity()), groupLink, nextClass(id),
                    students(id), new Deactivation(h.status().equals("ACTIVE") && blocking == 0, blocking), null);
        });
    }

    /** RF-TUR-02: primeira aula não cancelada que ainda não terminou, pelo relógio no fuso da unidade; nula se não houver. */
    NextClass nextClass(UUID classId) {
        return jdbc.sql("select l.lesson_date, l.starts_at, l.ends_at from lesson l join unit u on u.id = l.unit_id and u.organization_id = l.organization_id "
                + "where l.class_id = :id and not l.cancelled and (l.lesson_date + l.ends_at) > (cast(:now as timestamptz) at time zone u.timezone) "
                + "order by l.lesson_date, l.starts_at limit 1")
                .param("id", classId).param("now", java.time.OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))
                .query((rs, n) -> new NextClass(rs.getObject(1, LocalDate.class).toString(), rs.getObject(2, LocalTime.class).toString(),
                        rs.getObject(3, LocalTime.class).toString()))
                .optional().orElse(null);
    }

    /** Alunos ativos da turma (principal ou outro horário), com a marca de turma principal (RN-TUR-04). Roda na transação da organização. */
    List<Student> students(UUID classId) {
        return jdbc.sql("select s.id, s.name, m.primary_class_id = :class from enrollment_modality m join student s on s.organization_id = m.organization_id "
                + "and s.id = m.student_id where m.status = 'ACTIVE' and (m.primary_class_id = :class or exists (select 1 from enrollment_modality_class o "
                + "where o.organization_id = m.organization_id and o.modality_row_id = m.id and o.class_id = :class)) order by s.name_key, s.name, s.id")
                .param("class", classId).query((rs, n) -> new Student(rs.getObject(1, UUID.class), rs.getString(2), rs.getBoolean(3))).list();
    }

    /** Alunos ativos que têm esta turma como principal (RN-TUR-03). */
    int primaryStudents(UUID classId) {
        return jdbc.sql("select count(*) from enrollment_modality where primary_class_id = :class and status = 'ACTIVE'")
                .param("class", classId).query(Integer.class).single();
    }

    /** RN-TUR-04: a turma é a principal do aluno na modalidade; nas demais, é outro horário. */
    static boolean isPrimary(UUID classId, UUID studentPrimaryClassId) {
        return classId.equals(studentPrimaryClassId);
    }

    /** Alunos distintos ativos da turma, principal ou outro horário (RN-TUR-02). */
    int activeStudents(UUID classId) {
        return jdbc.sql("select count(distinct m.student_id) from enrollment_modality m where m.status = 'ACTIVE' and (m.primary_class_id = :class "
                + "or exists (select 1 from enrollment_modality_class o where o.organization_id = m.organization_id and o.modality_row_id = m.id "
                + "and o.class_id = :class))").param("class", classId).query(Integer.class).single();
    }

    /** Mesmo ponto para outros pacotes (painel da aula, RF-AGE-02). */
    public int activeStudentsOf(UUID classId) {
        return activeStudents(classId);
    }

    /** RN-TUR-02: a capacidade só avisa. */
    static boolean overCapacity(int activeStudents, Integer capacity) {
        return capacity != null && activeStudents > capacity;
    }
}
