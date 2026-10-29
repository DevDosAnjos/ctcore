package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.billing.ChargePricing;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.EmailPreferences;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-07, RN-POR-01, RN-POR-02, RN-POR-06 a RN-POR-09, RN-INA-08: aba Minhas informações do portal. Dados pessoais (CPF mascarado), contato das unidades,
 * treinos, plano com a mensalidade, graduação e histórico, termos e preferência de e-mail do aluno escolhido entre os ligados à conta. Só leitura; ver não
 * exige ser o pagador (D2). Sem presença, frequência, progresso nem contato do professor. Com o portal reduzido, treinos, plano e graduação não vêm.
 */
@Service
public class PortalInfo {
    public record Address(String postalCode, String street, String number, String complement, String district, String city, String state) {}

    public record Emergency(String name, String relationship, String phone) {}

    public record Personal(String name, LocalDate birthDate, String maskedCpf, String photoUrl, String phone, String email, Address address, Emergency emergency,
            boolean canEdit, List<String> editableFields) {}

    public record UnitContact(UUID id, String name, String phone, String whatsapp, String email) {}

    public record Slot(int weekday, String startsAt, String endsAt) {}

    public record ClassInfo(PortalHome.Ref clazz, String teacherName, List<Slot> schedule) {}

    public record Training(PortalHome.Ref modality, UnitContact unit, ClassInfo mainClass, List<ClassInfo> otherClasses) {}

    public record Benefit(String type, long value, LocalDate validUntil) {}

    public record NextPlan(UUID id, String name, long valueCents) {}

    public record PlanInfo(PortalHome.Ref modality, PortalHome.Ref plan, long planCents, Benefit benefit, long benefitCents, long monthlyCents, int dueDay, NextPlan nextPlan) {}

    public record Level(UUID id, String levelName, String color, String secondaryColor, int degree, LocalDate achievedOn, boolean origin) {}

    public record Graduation(PortalHome.Ref modality, Level belt, List<Level> history) {}

    public record Accepted(int version, OffsetDateTime acceptedAt) {}

    public record ImageUse(boolean granted, Integer version, OffsetDateTime at) {}

    public record Terms(Accepted enrollment, ImageUse imageUse) {}

    public record View(boolean reduced, List<PortalStudents.Card> students, UUID selectedStudentId, Personal personal, List<UnitContact> unitContacts,
            List<Training> trainings, List<PlanInfo> plans, List<Graduation> graduation, boolean emailAnnouncements, Terms terms) {}

    /** Campos que o aluno ou responsável edita pelo portal (RN-ALU-11); nome, CPF, nascimento e e-mail não entram. */
    static final List<String> EDITABLE = List.of("phone", "postalCode", "street", "number", "complement", "district", "city", "state", "emergencyName",
            "emergencyRelationship", "emergencyPhone", "photo");

    private record Row(UUID id, PortalHome.Ref modality, UnitContact unit, String zone, UUID primaryClass, int dueDay, PortalHome.Ref plan, long planCents,
            NextPlan next, ChargePricing.BenefitTerms benefit) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalStudents portal;
    private final PortalTerms terms;
    private final PortalShell shell;
    private final Students students;
    private final EmailPreferences preferences;
    private final Clock clock;

    public PortalInfo(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalStudents portal, PortalTerms terms, PortalShell shell,
            Students students, EmailPreferences preferences, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.portal = portal;
        this.terms = terms;
        this.shell = shell;
        this.students = students;
        this.preferences = preferences;
        this.clock = clock;
    }

    /** Quem chama já conferiu o aceite do termo. {@code studentId} nulo: o primeiro cartão. Não ligado à conta: 404. */
    public View view(AccountPrincipal who, UUID studentId) {
        terms.requirePortal(who);
        View base = transactions.execute(() -> {
            UUID org = who.organizationId();
            List<PortalStudents.Card> cards = portal.cards(who);
            if (cards.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            UUID sid = studentId != null ? studentId : cards.get(0).id();
            if (cards.stream().noneMatch(c -> c.id().equals(sid))) throw new ApiStatusException(ErrorCode.NOT_FOUND);

            boolean reduced = shell.reduced(who);
            List<Row> rows = reduced ? List.of() : rows(org, sid);
            return new View(reduced, cards, sid, personal(org, sid), contacts(org, sid, reduced), reduced ? List.of() : trainings(org, rows), reduced ? List.of() : plans(rows),
                    reduced ? List.of() : graduation(org, sid, rows), false, terms(org, sid));
        });
        return new View(base.reduced(), base.students(), base.selectedStudentId(), base.personal(), base.unitContacts(), base.trainings(), base.plans(), base.graduation(),
                preferences.get(who).announcements(), base.terms());
    }

    Personal personal(UUID org, UUID sid) {
        return jdbc.sql("select name, birth_date, cpf, photo, phone, email, postal_code, street, number, complement, district, city, state, emergency_name, "
                + "emergency_relationship, emergency_phone from student where organization_id = :org and id = :sid").param("org", org).param("sid", sid)
                .query((rs, n) -> new Personal(rs.getString(1), rs.getObject(2, LocalDate.class), maskCpf(rs.getString(3)), students.photoUrl(rs.getString(4)), rs.getString(5),
                        rs.getString(6), new Address(rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13)),
                        new Emergency(rs.getString(14), rs.getString(15), rs.getString(16)), true, EDITABLE)).single();
    }

    /** CPF só leitura e mascarado: sobram os dois últimos dígitos (CA1). */
    static String maskCpf(String digits) {
        return digits == null || digits.length() != 11 ? null : "***.***.***-" + digits.substring(9);
    }

    /** Unidade de cadastro e, no portal completo, as das modalidades ativas (RN-POR-06); nunca o contato do professor. */
    private List<UnitContact> contacts(UUID org, UUID sid, boolean reduced) {
        return jdbc.sql("select u.id, u.name, u.phone, u.whatsapp, u.email from unit u where u.organization_id = :org and (u.id = (select registered_unit_id from student "
                + "where organization_id = :org and id = :sid) or (not :reduced and exists (select 1 from enrollment_modality em where " + PortalHome.ACTIVE_ROW
                + " and em.unit_id = u.id))) order by u.name, u.id").param("org", org).param("sid", sid).param("reduced", reduced)
                .query((rs, n) -> new UnitContact(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5))).list();
    }

    private List<Row> rows(UUID org, UUID sid) {
        return jdbc.sql("select em.id, md.id, md.name, un.id, un.name, un.phone, un.whatsapp, un.email, un.timezone, em.primary_class_id, em.due_day, p.id, p.name, p.value_cents, "
                + "np.id, np.name, np.value_cents, b.id, b.type, b.value, b.valid_until from enrollment_modality em join modality md on md.id = em.modality_id "
                + "join unit un on un.organization_id = em.organization_id and un.id = em.unit_id "
                + "join plan p on p.organization_id = em.organization_id and p.id = em.plan_id "
                + "left join plan np on np.organization_id = em.organization_id and np.id = em.next_plan_id "
                + "left join benefit b on b.organization_id = em.organization_id and b.enrollment_modality_id = em.id and b.status = 'ACTIVE' "
                + "where " + PortalHome.ACTIVE_ROW + " order by md.name, un.name, em.id").param("org", org).param("sid", sid)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), new PortalHome.Ref(rs.getObject(2, UUID.class), rs.getString(3)),
                        new UnitContact(rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8)), rs.getString(9),
                        rs.getObject(10, UUID.class), rs.getInt(11), new PortalHome.Ref(rs.getObject(12, UUID.class), rs.getString(13)), rs.getLong(14),
                        rs.getObject(15, UUID.class) == null ? null : new NextPlan(rs.getObject(15, UUID.class), rs.getString(16), rs.getLong(17)),
                        rs.getObject(18, UUID.class) == null ? null : new ChargePricing.BenefitTerms(rs.getObject(18, UUID.class), rs.getString(19), rs.getLong(20),
                                rs.getObject(21, LocalDate.class), false))).list();
    }

    private List<Training> trainings(UUID org, List<Row> rows) {
        if (rows.isEmpty()) return List.of();
        List<UUID> rowIds = rows.stream().map(Row::id).toList();
        Map<UUID, List<UUID>> extra = new HashMap<>();
        jdbc.sql("select modality_row_id, class_id from enrollment_modality_class where organization_id = :org and modality_row_id in (:ids) order by class_id")
                .param("org", org).param("ids", rowIds).query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class))).list()
                .forEach(e -> extra.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        List<UUID> classIds = new ArrayList<>(rows.stream().map(Row::primaryClass).toList());
        extra.values().forEach(classIds::addAll);
        Map<UUID, List<Slot>> slots = new HashMap<>();
        jdbc.sql("select class_id, weekday, to_char(starts_at, 'HH24:MI'), to_char(ends_at, 'HH24:MI') from training_class_schedule where organization_id = :org and class_id in (:ids) "
                + "order by weekday").param("org", org).param("ids", classIds)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), new Slot(rs.getInt(2), rs.getString(3), rs.getString(4)))).list()
                .forEach(e -> slots.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        Map<UUID, ClassInfo> classes = new LinkedHashMap<>();
        jdbc.sql("select tc.id, tc.name, t.name from training_class tc join teacher t on t.organization_id = tc.organization_id and t.id = tc.head_teacher_id "
                + "where tc.organization_id = :org and tc.id in (:ids)").param("org", org).param("ids", classIds)
                .query((rs, n) -> new ClassInfo(new PortalHome.Ref(rs.getObject(1, UUID.class), rs.getString(2)), rs.getString(3), slots.getOrDefault(rs.getObject(1, UUID.class), List.of()))).list()
                .forEach(c -> classes.put(c.clazz().id(), c));
        return rows.stream().map(r -> new Training(r.modality(), r.unit(), classes.get(r.primaryClass()),
                extra.getOrDefault(r.id(), List.of()).stream().filter(c -> !c.equals(r.primaryClass())).map(classes::get).toList())).toList();
    }

    // RN-COB-03: a mensalidade é a composição do plano com o benefício ativo e dentro da validade, na data de hoje do fuso da unidade.
    private List<PlanInfo> plans(List<Row> rows) {
        return rows.stream().map(r -> {
            LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneId.of(r.zone()));
            ChargePricing.Composition c = ChargePricing.compose(r.plan().id(), r.planCents(), r.benefit(), today);
            Benefit benefit = c.benefitId() == null ? null : new Benefit(r.benefit().type(), r.benefit().value(), r.benefit().validUntil());
            return new PlanInfo(r.modality(), r.plan(), c.planCents(), benefit, c.benefitCents(), c.amountCents(), r.dueDay(), r.next());
        }).toList();
    }

    // O registro mais recente pela data é a faixa atual; mesma ordem de PortalHome.belts.
    private List<Graduation> graduation(UUID org, UUID sid, List<Row> rows) {
        Map<UUID, List<Level>> byModality = new HashMap<>();
        jdbc.sql("select g.modality_id, g.id, gl.name, gl.color, gl.secondary_color, g.degree, g.achieved_on, g.origin from student_graduation g "
                + "join graduation_level gl on gl.id = g.level_id where g.organization_id = :org and g.student_id = :sid and exists (select 1 from enrollment_modality em where "
                + PortalHome.ACTIVE_ROW + " and em.modality_id = g.modality_id) order by g.achieved_on desc nulls last, g.created_at desc, g.id")
                .param("org", org).param("sid", sid)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), new Level(rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getInt(6),
                        rs.getObject(7, LocalDate.class), rs.getBoolean(8)))).list()
                .forEach(e -> byModality.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(e.getValue()));
        return rows.stream().map(r -> {
            List<Level> history = byModality.getOrDefault(r.modality().id(), List.of());
            return new Graduation(r.modality(), history.isEmpty() ? null : history.get(0), history);
        }).toList();
    }

    private Terms terms(UUID org, UUID sid) {
        Accepted enrollment = jdbc.sql("select version, accepted_at from term_acceptance where organization_id = :org and student_id = :sid and kind = 'ENROLLMENT' and event = 'ACCEPTED' "
                + "order by version desc limit 1").param("org", org).param("sid", sid)
                .query((rs, n) -> new Accepted(rs.getInt(1), rs.getObject(2, OffsetDateTime.class))).optional().orElse(null);
        Accepted last = jdbc.sql("select version, accepted_at from term_acceptance where organization_id = :org and student_id = :sid and kind = 'IMAGE_USE' "
                + "order by accepted_at desc, id limit 1").param("org", org).param("sid", sid)
                .query((rs, n) -> new Accepted(rs.getInt(1), rs.getObject(2, OffsetDateTime.class))).optional().orElse(null);
        boolean granted = terms.imageUse(org, sid).granted();
        return new Terms(enrollment, new ImageUse(granted, last == null ? null : last.version(), last == null ? null : last.acceptedAt()));
    }
}
