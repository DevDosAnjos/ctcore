package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.billing.Charges;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-02, RN-POR-04, RN-POR-05, RN-POR-09, RN-INA-08: Início do portal. Cobrança, próximas aulas, avisos e faixa do aluno escolhido entre os ligados à
 * conta da sessão; o responsável ainda recebe um cartão por aluno com o em aberto. Só leitura. Sem presença, frequência nem progresso de graduação.
 */
@Service
public class PortalHome {
    public record Ref(UUID id, String name) {}

    public record Student(UUID id, String name, String photoUrl, boolean self, long openCents) {}

    public record Charge(UUID id, UUID studentId, String description, LocalDate referenceMonth, LocalDate dueOn, long amountCents, String situation, boolean canPay,
            Ref modality, Ref unit) {}

    public record ChargeSection(List<Charge> items, LocalDate nextDueOn) {}

    public record Lesson(UUID id, LocalDate date, String startsAt, String endsAt, String title, String status, String cancelReason, boolean changed, boolean adhoc,
            Ref modality, Ref unit) {}

    public record Notice(UUID id, String title, OffsetDateTime sentAt, boolean pinned, boolean unread, String authorName) {}

    public record Notices(int unreadCount, List<Notice> items) {}

    public record Belt(Ref modality, String levelName, String color, String secondaryColor, int degree, LocalDate achievedOn, boolean isNew) {}

    public record View(boolean reduced, List<Student> students, long familyOpenCents, UUID selectedStudentId, ChargeSection charges, List<Lesson> lessons,
            Notices announcements, List<Belt> belts) {}

    private static final int SOON_DAYS = 3; // D1
    private static final int LESSONS = 3;
    private static final int NOTICES = 5;

    /** Modalidade ativa do aluno {@code :sid} (alias {@code em}). */
    static final String ACTIVE_ROW = "em.organization_id = :org and em.student_id = :sid and em.status = 'ACTIVE'";
    static final String ROW_CLASS = "(em.primary_class_id = %1$s or exists (select 1 from enrollment_modality_class x where x.organization_id = em.organization_id "
            + "and x.modality_row_id = em.id and x.class_id = %1$s))";

    /** Situação da cobrança em aberto (aliases {@code c} e {@code u}; parâmetro {@code :now}); reusada pelo Financeiro. */
    static final String SITUATION = "case when c.status = 'IN_REVIEW' then 'IN_REVIEW' when " + Charges.overdue("c", "u") + " then 'OVERDUE' when c.due_on = " + Charges.today("u")
            + " then 'DUE_TODAY' when c.due_on <= " + Charges.today("u") + " + " + SOON_DAYS + " then 'DUE_SOON' else 'PENDING' end";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalTerms terms;
    private final PortalShell shell;
    private final Clock clock;

    public PortalHome(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalTerms terms, PortalShell shell, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.terms = terms;
        this.shell = shell;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão. Não ligado à conta: 404. */
    public View view(AccountPrincipal who, UUID studentId) {
        terms.requirePortal(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            List<PortalStudents.Card> cards = portal.cards(who);
            if (cards.isEmpty() && studentId == null) return new View(false, List.of(), 0, null, new ChargeSection(List.of(), null), List.of(), new Notices(0, List.of()), List.of());
            UUID selected = studentId != null ? studentId : cards.get(0).id();
            if (cards.stream().noneMatch(c -> c.id().equals(selected))) throw new ApiStatusException(ErrorCode.NOT_FOUND);

            Map<UUID, PortalTerms.Linked> linked = terms.candidates(who, null, false).stream().collect(Collectors.toMap(PortalTerms.Linked::id, Function.identity()));
            List<UUID> payable = cards.stream().map(PortalStudents.Card::id).filter(id -> terms.canAccept(linked.get(id))).toList();
            Map<UUID, Long> open = openCents(org, payable);
            List<Student> students = cards.stream().map(c -> new Student(c.id(), c.name(), c.photoUrl(), c.self(), open.getOrDefault(c.id(), 0L))).toList();
            long family = open.values().stream().mapToLong(Long::longValue).sum();

            OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
            ChargeSection charges = payable.contains(selected) ? charges(org, selected, now) : new ChargeSection(List.of(), null);
            boolean reduced = shell.reduced(who);
            if (reduced) return new View(true, students, family, selected, charges, List.of(), new Notices(0, List.of()), List.of());
            return new View(false, students, family, selected, charges, lessons(org, selected, now), notices(org, selected, now), belts(org, selected, now));
        });
    }

    Map<UUID, Long> openCents(UUID org, List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return jdbc.sql("select student_id, sum(amount_cents) from charge where organization_id = :org and student_id in (:ids) and status in ('OPEN', 'IN_REVIEW') "
                + "group by student_id").param("org", org).param("ids", ids).query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getLong(2))).list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // D2: vencidas, que vencem hoje ou em até 3 dias, e as em conferência; "PENDING" é o que ainda está longe (só dá o próximo vencimento).
    private ChargeSection charges(UUID org, UUID student, OffsetDateTime now) {
        List<Charge> all = jdbc.sql("select c.id, c.student_id, c.description, c.reference_month, c.due_on, c.amount_cents, " + SITUATION + ", md.id, md.name, u.id, u.name "
                + "from charge c join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
                + "left join enrollment_modality em on em.organization_id = c.organization_id and em.id = c.enrollment_modality_id left join modality md on md.id = em.modality_id "
                + "where c.organization_id = :org and c.student_id = :sid and c.status in ('OPEN', 'IN_REVIEW')")
                .param("org", org).param("sid", student).param("now", now)
                .query((rs, n) -> new Charge(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getObject(4, LocalDate.class),
                        rs.getObject(5, LocalDate.class), rs.getLong(6), rs.getString(7), !"IN_REVIEW".equals(rs.getString(7)), ref(rs.getObject(8, UUID.class), rs.getString(9)),
                        ref(rs.getObject(10, UUID.class), rs.getString(11)))).list();
        LocalDate next = all.stream().filter(c -> c.situation().equals("PENDING")).map(Charge::dueOn).min(Comparator.naturalOrder()).orElse(null);
        List<Charge> items = all.stream().filter(c -> !c.situation().equals("PENDING"))
                .sorted(Comparator.comparing((Charge c) -> !c.situation().equals("OVERDUE")).thenComparing(Charge::dueOn).thenComparing(Charge::id)).toList();
        return new ChargeSection(items, next);
    }

    // D3: turma principal, outros horários e avulsas visíveis (unidade toda ou turma do aluno); o relógio é o do fuso da unidade da aula.
    private List<Lesson> lessons(UUID org, UUID student, OffsetDateTime now) {
        return jdbc.sql("select l.id, l.lesson_date, to_char(l.starts_at, 'HH24:MI'), to_char(l.ends_at, 'HH24:MI'), coalesce(tc.name, l.title), l.cancelled, l.cancel_reason, "
                + "(l.substitute or l.manually_edited), l.class_id is null, md.id, md.name, u.id, u.name "
                + "from lesson l join unit u on u.organization_id = l.organization_id and u.id = l.unit_id "
                + "left join training_class tc on tc.organization_id = l.organization_id and tc.id = l.class_id "
                + "join modality md on md.id = coalesce(tc.modality_id, l.modality_id) "
                + "where l.organization_id = :org and (l.lesson_date + l.ends_at) > (cast(:now as timestamptz) at time zone u.timezone) and exists ("
                + "select 1 from enrollment_modality em where " + ACTIVE_ROW + " and em.unit_id = l.unit_id and ("
                + "(l.class_id is not null and " + ROW_CLASS.formatted("l.class_id") + ") or (l.class_id is null and (l.visibility_class_id is null or "
                + ROW_CLASS.formatted("l.visibility_class_id") + ")))) order by l.lesson_date, l.starts_at, l.id limit " + LESSONS)
                .param("org", org).param("sid", student).param("now", now)
                .query((rs, n) -> new Lesson(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getBoolean(6) ? "CANCELLED" : "SCHEDULED", rs.getBoolean(6) ? rs.getString(7) : null, rs.getBoolean(8), rs.getBoolean(9),
                        ref(rs.getObject(10, UUID.class), rs.getString(11)), ref(rs.getObject(12, UUID.class), rs.getString(13)))).list();
    }

    // D4: o fixado vale até o fim de pinned_until, no fuso da unidade do aviso (sem unidade, a da matrícula do aluno).
    private Notices notices(UUID org, UUID student, OffsetDateTime now) {
        List<Notice> all = jdbc.sql("select a.id, a.title, a.sent_at, coalesce(a.pinned_until >= (cast(:now as timestamptz) at time zone coalesce(ua.timezone, su.timezone))::date, false), "
                + "not exists (select 1 from announcement_read r where r.organization_id = a.organization_id and r.announcement_id = a.id and r.student_id = :sid), "
                + "(select ac.name from account ac where ac.id = a.author_account_id) "
                + "from announcement a join student st on st.organization_id = a.organization_id and st.id = :sid "
                + "join unit su on su.organization_id = st.organization_id and su.id = st.registered_unit_id "
                + "left join unit ua on ua.organization_id = a.organization_id and ua.id = a.unit_id "
                + "where a.organization_id = :org and a.sent_at is not null and exists (select 1 from enrollment_modality em where " + ACTIVE_ROW + " and " + PortalShell.REACH + ")")
                .param("org", org).param("sid", student).param("now", now)
                .query((rs, n) -> new Notice(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, OffsetDateTime.class), rs.getBoolean(4), rs.getBoolean(5),
                        rs.getString(6))).list();
        int unread = (int) all.stream().filter(Notice::unread).count();
        List<Notice> items = new ArrayList<>(all.stream().filter(a -> a.unread() || a.pinned())
                .sorted(Comparator.comparing((Notice a) -> !a.pinned()).thenComparing(Notice::sentAt, Comparator.reverseOrder()).thenComparing(Notice::id)).limit(NOTICES).toList());
        return new Notices(unread, items);
    }

    // D5: o registro mais recente de cada modalidade ativa; "Nova" só para registro que não é de origem, dos últimos 14 dias.
    List<Belt> belts(UUID org, UUID student, OffsetDateTime now) {
        return jdbc.sql("select * from (select distinct on (g.modality_id) md.id, md.name, gl.name as level_name, gl.color, gl.secondary_color, g.degree, g.achieved_on, "
                + "(not g.origin and g.created_at >= cast(:now as timestamptz) - interval '14 days') as is_new "
                + "from student_graduation g join graduation_level gl on gl.id = g.level_id join modality md on md.id = g.modality_id "
                + "where g.organization_id = :org and g.student_id = :sid and exists (select 1 from enrollment_modality em where " + ACTIVE_ROW + " and em.modality_id = g.modality_id) "
                + "order by g.modality_id, g.achieved_on desc nulls last, g.created_at desc) b order by b.name")
                .param("org", org).param("sid", student).param("now", now)
                .query((rs, n) -> new Belt(ref(rs.getObject(1, UUID.class), rs.getString(2)), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                        rs.getObject(7, LocalDate.class), rs.getBoolean(8))).list();
    }

    private static Ref ref(UUID id, String name) {
        return id == null ? null : new Ref(id, name);
    }
}
