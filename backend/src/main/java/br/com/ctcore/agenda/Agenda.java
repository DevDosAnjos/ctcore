package br.com.ctcore.agenda;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.trainingclass.TrainingClasses;
import br.com.ctcore.trainingclass.TrainingClasses.Ref;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-AGE-01: aulas do período e dias sem aula de uma unidade. Organização, perfil e alcance vêm da sessão. A situação (RN-AGE-03) é calculada na
 * leitura pelo relógio no fuso da unidade e nunca gravada. Sem lista de espera, presença ou frequência (RN-AGE-13).
 */
@Service
public class Agenda {
    static final int MAX_DAYS = 31;
    static final Duration EDIT_WINDOW = Duration.ofDays(30);

    public enum Status { SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED }

    public record Lesson(UUID id, String date, String startsAt, String endsAt, Status status, String cancelReason,
            @JsonProperty("class") Ref trainingClass, Ref unit, Ref modality, Ref teacher, boolean substitute, boolean canEdit, boolean canCancel) {}

    /** RF-AGE-02: painel da aula. {@code head} só vem na substituição (D6). */
    public record LessonDetail(UUID id, String date, String startsAt, String endsAt, Status status, String cancelReason,
            @JsonProperty("class") Ref trainingClass, Ref unit, Ref modality, Ref teacher, Ref head, boolean substitute, int activeStudents,
            int trialClasses, String groupLink, boolean canEdit, boolean canCancel, boolean canViewClass) {}

    public record NoClassDay(String startsOn, String endsOn, String reason) {}

    public record View(List<Ref> units, Ref unit, List<Ref> teachers, List<Ref> modalities, List<Lesson> lessons, List<NoClassDay> noClassDays) {}

    public record Filter(String from, String to, UUID unitId, UUID teacherId, UUID modalityId) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Clock clock;
    private final TrainingClasses classes;

    public Agenda(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Clock clock,
            TrainingClasses classes) {
        this.classes = classes;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.clock = clock;
    }

    /** RN-AGE-03: cancelada vale sobre qualquer horário; início inclusive, término exclusive. */
    static Status status(boolean cancelled, LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (cancelled) return Status.CANCELLED;
        if (now.isBefore(start)) return Status.SCHEDULED;
        return now.isBefore(end) ? Status.IN_PROGRESS : Status.COMPLETED;
    }

    record Can(boolean edit, boolean cancel) {}

    /**
     * RN-AGE-08: concluída nunca é cancelada; cancelada não é editável; concluída só é editável por Administrador e Coordenador, em até 30 dias
     * depois do término. Professor precisa de AGENDA_EDIT e de aula sua. São dicas: quem grava (#51) confere de novo.
     */
    static Can can(Role role, boolean editPermission, boolean mine, Status status, LocalDateTime end, LocalDateTime now) {
        if (!editPermission || (role == Role.TEACHER && !mine)) return new Can(false, false);
        boolean cancel = status == Status.SCHEDULED || status == Status.IN_PROGRESS;
        boolean edit = switch (status) {
            case CANCELLED -> false;
            case COMPLETED -> role != Role.TEACHER && !now.isAfter(end.plus(EDIT_WINDOW));
            default -> true;
        };
        return new Can(edit, cancel);
    }

    public View view(AccountPrincipal who, Filter f) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.AGENDA_VIEW) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        LocalDate from = date("from", f.from());
        LocalDate to = date("to", f.to());
        if (to.isBefore(from)) throw new ApiValidationException("to", "não pode ser anterior a from");
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) throw new ApiValidationException("to", "período de no máximo " + MAX_DAYS + " dias");
        boolean coordinator = who.role() == Role.COORDINATOR;
        boolean teacher = who.role() == Role.TEACHER;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();

        return transactions.execute(() -> {
            UUID org = who.organizationId();
            List<Ref> units = jdbc.sql("select id, name from unit where organization_id = :org and active order by name, id")
                    .param("org", org).query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2))).list().stream()
                    .filter(u -> coordinator || reach.contains(u.id())).toList();
            Ref unit;
            if (f.unitId() != null) {
                unit = units.stream().filter(u -> u.id().equals(f.unitId())).findFirst().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            } else if (units.isEmpty()) {
                return new View(units, null, List.of(), List.of(), List.of(), List.of());
            } else {
                unit = units.get(0);
            }
            ZoneId zone = ZoneId.of(jdbc.sql("select timezone from unit where id = :id and organization_id = :org")
                    .param("id", unit.id()).param("org", org).query(String.class).single());
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);

            UUID me = teacher
                    ? jdbc.sql("select id from teacher where organization_id = :org and account_id = :acc")
                            .param("org", org).param("acc", who.accountId()).query(UUID.class).optional().orElse(null)
                    : null;
            boolean editPermission = permissions.grant(who, Permission.AGENDA_EDIT, unit.id()) != Access.NONE;
            String mine = "(c.head_teacher_id = :me or l.teacher_id = :me or exists (select 1 from training_class_assistant a "
                    + "where a.class_id = c.id and a.teacher_id = :me and a.organization_id = :org))";
            List<Lesson> lessons = teacher && me == null ? List.of() : jdbc.sql(
                    "select l.id, l.lesson_date, l.starts_at, l.ends_at, l.cancelled, l.cancel_reason, c.id, c.name, m.id, m.name, t.id, t.name, "
                    + "l.substitute, " + mine + " from lesson l join training_class c on c.id = l.class_id and c.organization_id = l.organization_id "
                    + "join modality m on m.id = c.modality_id join teacher t on t.id = l.teacher_id and t.organization_id = l.organization_id "
                    + "where l.organization_id = :org and l.unit_id = :unit and l.lesson_date between :from and :to "
                    + "and (cast(:teacher as uuid) is null or l.teacher_id = :teacher) and (cast(:modality as uuid) is null or c.modality_id = :modality)"
                    + (teacher ? " and " + mine : "") + " order by l.lesson_date, l.starts_at, c.name_key, l.id")
                    .param("org", org).param("unit", unit.id()).param("from", from).param("to", to).param("teacher", f.teacherId())
                    .param("modality", f.modalityId()).param("me", me)
                    .query((rs, n) -> {
                        LocalDate day = rs.getObject(2, LocalDate.class);
                        LocalTime s = rs.getObject(3, LocalTime.class);
                        LocalTime e = rs.getObject(4, LocalTime.class);
                        Status st = status(rs.getBoolean(5), day.atTime(s), day.atTime(e), now);
                        Can can = can(who.role(), editPermission, rs.getBoolean(14), st, day.atTime(e), now);
                        return new Lesson(rs.getObject(1, UUID.class), day.toString(), s.toString(), e.toString(), st,
                                st == Status.CANCELLED ? rs.getString(6) : null, new Ref(rs.getObject(7, UUID.class), rs.getString(8)), unit,
                                new Ref(rs.getObject(9, UUID.class), rs.getString(10)), new Ref(rs.getObject(11, UUID.class), rs.getString(12)),
                                rs.getBoolean(13), can.edit(), can.cancel());
                    }).list();
            List<NoClassDay> days = jdbc.sql("select starts_on, ends_on, reason from no_class_day where organization_id = :org and unit_id = :unit "
                    + "and starts_on <= :to and ends_on >= :from order by starts_on, id")
                    .param("org", org).param("unit", unit.id()).param("from", from).param("to", to)
                    .query((rs, n) -> new NoClassDay(rs.getObject(1, LocalDate.class).toString(), rs.getObject(2, LocalDate.class).toString(),
                            rs.getString(3))).list();
            List<Ref> teachers = jdbc.sql("select t.id, t.name from teacher t join teacher_unit tu on tu.teacher_id = t.id and tu.unit_id = :unit "
                    + "where t.organization_id = :org and t.status = 'ACTIVE' order by t.name_key, t.id")
                    .param("org", org).param("unit", unit.id()).query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2))).list();
            List<Ref> modalities = jdbc.sql("select m.id, m.name from modality m join organization_modality o on o.modality_id = m.id "
                    + "and o.organization_id = :org and o.active join unit_modality um on um.modality_id = m.id and um.unit_id = :unit and um.offered "
                    + "order by m.name, m.id").param("org", org).param("unit", unit.id())
                    .query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2))).list();
            return new View(units, unit, teachers, modalities, lessons, days);
        });
    }

    /** RN-PRO-06: o substituto só abre a aula do início ao fim do dia dela, no fuso da unidade. */
    static boolean substituteWindow(LocalDate lessonDate, ZoneId zone, Instant now) {
        return !now.isBefore(lessonDate.atStartOfDay(zone).toInstant()) && now.isBefore(lessonDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /** Aulas experimentais marcadas: 0 até a Sprint 18; é o único ponto a trocar. */
    int trialClasses(UUID lessonId) {
        return 0;
    }

    private record LessonRow(LessonDetail d, boolean inClass, boolean lessonTeacher, ZoneId zone, LocalDateTime start, LocalDateTime end,
            boolean cancelled) {}

    /** RF-AGE-02: leitura do painel. Fora do alcance, da janela do substituto ou de outra organização, 404. */
    public LessonDetail lesson(AccountPrincipal who, UUID id) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.AGENDA_VIEW) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        boolean teacher = who.role() == Role.TEACHER;
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            UUID me = teacher
                    ? jdbc.sql("select id from teacher where organization_id = :org and account_id = :acc")
                            .param("org", org).param("acc", who.accountId()).query(UUID.class).optional().orElse(null)
                    : null;
            if (teacher && me == null) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            LessonRow r = jdbc.sql("select l.id, l.lesson_date, l.starts_at, l.ends_at, l.cancelled, l.cancel_reason, c.id, c.name, m.id, m.name, t.id, t.name, "
                    + "l.substitute, u.id, u.name, u.timezone, c.group_link, h.id, h.name, "
                    + "(c.head_teacher_id = :me or exists (select 1 from training_class_assistant a where a.class_id = c.id and a.teacher_id = :me "
                    + "and a.organization_id = :org)), l.teacher_id = :me "
                    + "from lesson l join training_class c on c.id = l.class_id and c.organization_id = l.organization_id "
                    + "join modality m on m.id = c.modality_id join teacher t on t.id = l.teacher_id and t.organization_id = l.organization_id "
                    + "join teacher h on h.id = c.head_teacher_id and h.organization_id = l.organization_id "
                    + "join unit u on u.id = l.unit_id and u.organization_id = l.organization_id "
                    + "where l.organization_id = :org and l.id = :id")
                    .param("org", org).param("id", id).param("me", me)
                    .query((rs, n) -> {
                        LocalDate day = rs.getObject(2, LocalDate.class);
                        LocalTime s = rs.getObject(3, LocalTime.class);
                        LocalTime e = rs.getObject(4, LocalTime.class);
                        boolean sub = rs.getBoolean(13);
                        LessonDetail d = new LessonDetail(rs.getObject(1, UUID.class), day.toString(), s.toString(), e.toString(), null,
                                rs.getString(6), new Ref(rs.getObject(7, UUID.class), rs.getString(8)),
                                new Ref(rs.getObject(14, UUID.class), rs.getString(15)), new Ref(rs.getObject(9, UUID.class), rs.getString(10)),
                                new Ref(rs.getObject(11, UUID.class), rs.getString(12)),
                                sub ? new Ref(rs.getObject(18, UUID.class), rs.getString(19)) : null, sub, 0, 0, rs.getString(17), false, false, false);
                        return new LessonRow(d, rs.getBoolean(20), rs.getBoolean(21), ZoneId.of(rs.getString(16)), day.atTime(s), day.atTime(e),
                                rs.getBoolean(5));
                    }).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            LessonDetail d = r.d();
            UUID unitId = d.unit().id();
            if (admin && !reach.contains(unitId)) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            Instant now = clock.instant();
            if (teacher && !r.inClass() && !(r.lessonTeacher() && d.substitute() && substituteWindow(LocalDate.parse(d.date()), r.zone(), now))) {
                throw new ApiStatusException(ErrorCode.NOT_FOUND);
            }
            LocalDateTime local = LocalDateTime.ofInstant(now, r.zone());
            Status st = status(r.cancelled(), r.start(), r.end(), local);
            boolean editPermission = permissions.grant(who, Permission.AGENDA_EDIT, unitId) != Access.NONE;
            Can can = can(who.role(), editPermission, r.inClass() || r.lessonTeacher(), st, r.end(), local);
            boolean viewClass = permissions.effective(who).get(Permission.CLASSES_MANAGE) != Access.NONE && (!admin || reach.contains(unitId));
            return new LessonDetail(d.id(), d.date(), d.startsAt(), d.endsAt(), st, st == Status.CANCELLED ? d.cancelReason() : null,
                    d.trainingClass(), d.unit(), d.modality(), d.teacher(), d.head(), d.substitute(), classes.activeStudentsOf(d.trainingClass().id()),
                    trialClasses(d.id()), d.groupLink(), can.edit(), can.cancel(), viewClass);
        });
    }

    private static LocalDate date(String field, String raw) {
        if (raw == null || raw.isBlank()) throw new ApiValidationException(field, "obrigatório");
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new ApiValidationException(field, "data inválida, use yyyy-MM-dd");
        }
    }
}
