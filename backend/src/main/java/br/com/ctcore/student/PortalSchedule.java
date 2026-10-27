package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-04, RN-POR-01, RN-POR-02, RN-POR-04, RN-POR-05, RN-POR-06, RN-POR-08, RN-POR-09, RN-AGE-03, RN-AGE-04, RN-AGE-05: aba Agenda do portal. As aulas de uma
 * semana (segunda a domingo) do aluno escolhido, com o alcance do Início, e os dias sem aula que valem para ele. Só leitura; sem professor, presença,
 * frequência, progresso, lista de espera nem cobrança.
 */
@Service
public class PortalSchedule {
    public enum Scope { ALL, MAIN }

    public record Link(UUID id, String name, boolean self) {}

    public record Lesson(UUID id, LocalDate date, String startsAt, String endsAt, String title, String kind, String status, String cancelReasonCode,
            String cancelReason, boolean changed, LocalDate originalDate, String originalStartsAt, boolean substitute, boolean past, UUID noticeId,
            PortalHome.Ref modality, PortalHome.Ref unit) {}

    public record NoClassDay(LocalDate date, String reason, PortalHome.Ref unit) {}

    public record View(boolean reduced, List<Link> students, UUID studentId, Scope scope, LocalDate weekStart, LocalDate weekEnd, LocalDate today, String timezone,
            List<Lesson> lessons, List<NoClassDay> noClassDays) {}

    private static final String CHANGE_TYPES = "('SCHEDULE_CHANGE', 'TEACHER_CHANGE')";

    // D4: o comunicado enviado mais recente do tipo certo que alcança a turma da aula e o aluno; para cancelada, também o do dia sem aula.
    private static final String NOTICE = "(select a.id from announcement a where a.organization_id = l.organization_id and a.sent_at is not null and l.class_id is not null "
            + "and (l.cancelled or l.substitute or l.manually_edited) and exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
            + " and em.unit_id = l.unit_id and " + PortalShell.REACH + ") and ((l.cancelled and l.no_class_day_id is not null and a.no_class_day_id = l.no_class_day_id) "
            + "or (((a.target_kind = 'CLASS' and a.class_id = l.class_id) or (a.target_kind = 'CLASSES' and exists (select 1 from announcement_class k "
            + "where k.organization_id = a.organization_id and k.announcement_id = a.id and k.class_id = l.class_id))) "
            + "and (case when l.cancelled then a.type = 'CANCELED_CLASS' else a.type in " + CHANGE_TYPES + " end))) order by a.sent_at desc, a.id limit 1)";

    private static final String KIND = "case when l.class_id is null then 'ADHOC' when exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
            + " and em.unit_id = l.unit_id and em.primary_class_id = l.class_id) then 'MAIN' else 'OTHER' end";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalShell shell;
    private final Clock clock;

    public PortalSchedule(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalShell shell, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.shell = shell;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão; {@code week} nulo: a semana de hoje na unidade de cadastro. */
    public View view(AccountPrincipal who, UUID studentId, LocalDate week, Scope scope) {
        List<PortalStudents.Card> cards = portal.cards(who);
        if (cards.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        UUID selected = studentId != null ? studentId : cards.get(0).id();
        if (cards.stream().noneMatch(c -> c.id().equals(selected))) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        Scope chosen = scope == null ? Scope.ALL : scope;
        List<Link> links = cards.stream().map(c -> new Link(c.id(), c.name(), c.self())).toList();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            String zone = jdbc.sql("select u.timezone from student s join unit u on u.organization_id = s.organization_id and u.id = s.registered_unit_id "
                    + "where s.organization_id = :org and s.id = :sid").param("org", org).param("sid", selected).query(String.class).optional().orElse("America/Sao_Paulo");
            LocalDate today = now.atZoneSameInstant(ZoneId.of(zone)).toLocalDate();
            LocalDate start = (week != null ? week : today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = start.plusDays(6);
            if (shell.reduced(who)) return new View(true, links, selected, chosen, start, end, today, zone, List.of(), List.of());
            List<Lesson> lessons = lessons(org, selected, start, end, now).stream().filter(l -> chosen == Scope.ALL || l.kind().equals("MAIN")).toList();
            return new View(false, links, selected, chosen, start, end, today, zone, lessons, noClassDays(org, selected, start, end));
        });
    }

    private List<Lesson> lessons(UUID org, UUID student, LocalDate start, LocalDate end, OffsetDateTime now) {
        return jdbc.sql("select l.id, l.lesson_date, to_char(l.starts_at, 'HH24:MI'), to_char(l.ends_at, 'HH24:MI'), coalesce(tc.name, l.title), " + KIND
                + ", l.cancelled, l.cancel_reason_code, l.cancel_reason, (l.substitute or l.manually_edited), l.original_date, to_char(l.original_starts_at, 'HH24:MI'), l.substitute, "
                + "(l.lesson_date + l.ends_at) <= (cast(:now as timestamptz) at time zone u.timezone), " + NOTICE + ", md.id, md.name, u.id, u.name "
                + "from lesson l join unit u on u.organization_id = l.organization_id and u.id = l.unit_id "
                + "left join training_class tc on tc.organization_id = l.organization_id and tc.id = l.class_id "
                + "join modality md on md.id = coalesce(tc.modality_id, l.modality_id) "
                + "where l.organization_id = :org and l.lesson_date between :start and :end and exists ("
                + "select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW + " and em.unit_id = l.unit_id and ("
                + "(l.class_id is not null and " + PortalHome.ROW_CLASS.formatted("l.class_id") + ") or (l.class_id is null and (l.visibility_class_id is null or "
                + PortalHome.ROW_CLASS.formatted("l.visibility_class_id") + ")))) order by l.lesson_date, l.starts_at, l.id")
                .param("org", org).param("sid", student).param("start", start).param("end", end).param("now", now)
                .query((rs, n) -> {
                    boolean cancelled = rs.getBoolean(7);
                    return new Lesson(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            cancelled ? "CANCELLED" : "SCHEDULED", cancelled ? rs.getString(8) : null, cancelled ? rs.getString(9) : null, rs.getBoolean(10),
                            rs.getObject(11, LocalDate.class), rs.getString(12), rs.getBoolean(13), rs.getBoolean(14), rs.getObject(15, UUID.class),
                            new PortalHome.Ref(rs.getObject(16, UUID.class), rs.getString(17)), new PortalHome.Ref(rs.getObject(18, UUID.class), rs.getString(19)));
                }).list();
    }

    // RN-AGE-09: dia da unidade de uma modalidade ativa do aluno; com turmas indicadas, só se for uma das dele. Um item por dia da semana coberto.
    private List<NoClassDay> noClassDays(UUID org, UUID student, LocalDate start, LocalDate end) {
        record Range(LocalDate from, LocalDate to, String reason, PortalHome.Ref unit) {}
        List<Range> ranges = jdbc.sql("select n.starts_on, n.ends_on, n.reason, u.id, u.name from no_class_day n join unit u on u.organization_id = n.organization_id and u.id = n.unit_id "
                + "where n.organization_id = :org and n.starts_on <= :end and n.ends_on >= :start and exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
                + " and em.unit_id = n.unit_id and (not exists (select 1 from no_class_day_class c where c.organization_id = n.organization_id and c.no_class_day_id = n.id) "
                + "or exists (select 1 from no_class_day_class c where c.organization_id = n.organization_id and c.no_class_day_id = n.id and "
                + PortalHome.ROW_CLASS.formatted("c.class_id") + "))) order by n.starts_on, n.id")
                .param("org", org).param("sid", student).param("start", start).param("end", end)
                .query((rs, n) -> new Range(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class), rs.getString(3),
                        new PortalHome.Ref(rs.getObject(4, UUID.class), rs.getString(5)))).list();
        List<NoClassDay> days = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            for (Range r : ranges) if (!d.isBefore(r.from()) && !d.isAfter(r.to())) days.add(new NoClassDay(d, r.reason(), r.unit()));
        }
        return days;
    }
}
