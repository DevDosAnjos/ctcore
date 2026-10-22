package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.benefit.BenefitWrite;
import br.com.ctcore.benefit.Benefits;
import br.com.ctcore.billing.FirstCharges;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.trainingclass.TrainingClasses;
import java.time.Period;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
 * RF-MAT-01: a "Nova matrícula" no servidor. A prévia calcula o resumo sem gravar nada; a conclusão grava matrícula, benefício, graduação de origem
 * e o início da matrícula por unidade numa transação só (as outras classes entram nela, sem abrir transação isolada) e convida o aluno adulto
 * depois do commit. A primeira cobrança sai na mesma transação ({@link FirstCharges}); não registra aceite de termo (#85, D4).
 */
@Service
public class EnrollmentConclusion {
    private static final Logger LOG = LoggerFactory.getLogger(EnrollmentConclusion.class);
    /** RN-MAT-05: a primeira cobrança vence na data da matrícula, com 5 dias de tolerância. */
    static final int TOLERANCE_DAYS = 5;
    /** RN-MAT-07: a segunda cobrança fica a pelo menos 15 dias da matrícula; o teto de 60 é suposição (D5). */
    static final int MIN_SECOND_DAYS = 15;
    static final int MAX_SECOND_DAYS = 60;

    public record BenefitInput(String type, BigDecimal value, String reason, String decidedBy, LocalDate validUntil, UUID teacherId) {}

    public record OriginInput(UUID levelId, Integer degree, LocalDate achievedOn, String proofKey) {}

    public record ModalityRequest(UUID unitId, UUID modalityId, UUID primaryClassId, List<UUID> otherClassIds, UUID planId, Integer dueDay,
            BenefitInput benefit, OriginInput origin) {}

    public record UnitRequest(UUID unitId, Boolean feeExempt, String feeExemptReason, LocalDate secondDueOn) {}

    public record Request(List<ModalityRequest> modalities, List<UnitRequest> units) {}

    public record ModalitySummary(UUID unitId, UUID modalityId, UUID planId, String planName, long planCents, long discountCents, long monthlyCents) {}

    public record UnitSummary(UUID unitId, String unitName, int dueDay, LocalDate firstDueOn, int toleranceDays, long feeCents, boolean feeExempt,
            String feeExemptReason, long firstChargeCents, LocalDate secondDueOn, LocalDate suggestedSecondDueOn) {}

    /** RN-MAT-19: cobranças em aberto do aluno nas unidades do pedido, para o Administrador decidir (a decisão por cobrança é da #73). */
    public record OpenDebt(int count, long totalCents) {}

    public record Preview(List<ModalitySummary> modalities, List<UnitSummary> units, OpenDebt openDebt) {}

    /** {@code inviteStatus}: SENT, FAILED, MINOR (sem convite próprio), EXISTING_ACCOUNT (conta ativa) ou NO_EMAIL. */
    public record Concluded(Enrollments.Enrollment enrollment, Preview summary, boolean termPending, String inviteStatus, List<FirstCharges.Charge> charges) {}

    private record Facts(UUID studentId, boolean exists, int activeCount, Map<UUID, Integer> activeDue, Set<UUID> feeHandled) {
        boolean returning() {
            return exists && activeCount == 0;
        }
    }

    private record UnitRow(UUID id, String name, boolean active, ZoneId zone, List<Integer> dueDays, boolean feeEnabled, long feeCents) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;
    private final Enrollments enrollments;
    private final BenefitWrite benefits;
    private final Permissions permissions;
    private final AuditService audit;
    private final InviteService invites;
    private final OriginProof proofs;
    private final Clock clock;
    private final TrainingClasses classes;
    private final FirstCharges firstCharges;

    public EnrollmentConclusion(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students, Enrollments enrollments,
            BenefitWrite benefits, Permissions permissions, AuditService audit, InviteService invites, OriginProof proofs, Clock clock,
            TrainingClasses classes, FirstCharges firstCharges) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
        this.enrollments = enrollments;
        this.benefits = benefits;
        this.permissions = permissions;
        this.audit = audit;
        this.invites = invites;
        this.proofs = proofs;
        this.clock = clock;
        this.classes = classes;
        this.firstCharges = firstCharges;
    }

    private static ApiStatusException rule(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    private static ApiStatusException missing() {
        return new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    /** RN-MAT-07: a primeira data a pelo menos 15 dias da matrícula que cai no dia de vencimento (1 a 28, existe em todo mês). */
    static LocalDate suggestSecond(LocalDate enrolledOn, int dueDay) {
        LocalDate min = enrolledOn.plusDays(MIN_SECOND_DAYS);
        LocalDate candidate = min.withDayOfMonth(dueDay);
        return candidate.isBefore(min) ? candidate.plusMonths(1) : candidate;
    }

    /** RN-MAT-05: a taxa vale se a unidade a ligou e o aluno ainda não tem taxa cobrada nem isenção nela, ou se é retorno de ex-aluno (RN-MAT-19, R21). */
    static long fee(boolean enabled, long cents, boolean handled, boolean returning) {
        return enabled && (returning || !handled) ? cents : 0;
    }

    private static List<Enrollments.ModalityInput> inputs(Request req) {
        if (req == null || req.modalities() == null) return null;
        List<Enrollments.ModalityInput> list = new ArrayList<>();
        for (ModalityRequest m : req.modalities()) {
            list.add(m == null ? null : new Enrollments.ModalityInput(m.unitId(), m.modalityId(), m.primaryClassId(), m.otherClassIds(), m.planId(), m.dueDay()));
        }
        return list;
    }

    private void requireManage(AccountPrincipal who, UUID unitId) {
        if (permissions.grant(who, Permission.STUDENT_MANAGE, unitId) == Access.NONE) throw new AccessDeniedException("Permission denied");
    }

    /** R1: o resumo da matrícula, sem gravar nada. */
    public Preview preview(AccountPrincipal who, UUID studentId, Request req) {
        students.requireAccess(who);
        Enrollments.checkInputs(inputs(req));
        return transactions.execute(() -> {
            students.detail(who, studentId);
            return compute(who, req, facts(who.organizationId(), studentId));
        });
    }

    /** RF-MAT-02, R1 e R2: avisos de faixa etária e capacidade da turma, sem gravar nada. */
    public ClassWarnings warnings(AccountPrincipal who, UUID studentId, UUID classId) {
        students.requireAccess(who);
        if (classId == null) throw new ApiValidationException("classId", "obrigatório");
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            LocalDate birth = students.detail(who, studentId).birthDate();
            var c = jdbc.sql("select c.unit_id, u.timezone, c.capacity, a.id, a.name, a.min_age, a.max_age from training_class c "
                    + "join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
                    + "left join age_range a on a.organization_id = c.organization_id and a.id = c.age_range_id where c.organization_id = :org and c.id = :id")
                    .param("org", org).param("id", classId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), ZoneId.of(rs.getString(2)),
                            rs.getObject(3), rs.getObject(4) == null ? null : new TrainingClasses.AgeRangeRef(rs.getObject(4, UUID.class), rs.getString(5),
                                    rs.getInt(6), (Integer) rs.getObject(7))})
                    .optional().orElseThrow(EnrollmentConclusion::missing);
            requireManage(who, (UUID) c[0]);
            int age = Period.between(birth, LocalDate.ofInstant(clock.instant(), (ZoneId) c[1])).getYears();
            var range = (TrainingClasses.AgeRangeRef) c[3];
            Integer capacity = (Integer) c[2];
            int active = classes.activeStudentsOf(classId);
            boolean in = jdbc.sql("select count(*) from enrollment_modality m where m.organization_id = :org and m.student_id = :s and m.status = 'ACTIVE' "
                    + "and (m.primary_class_id = :c or exists (select 1 from enrollment_modality_class o where o.organization_id = m.organization_id "
                    + "and o.modality_row_id = m.id and o.class_id = :c))").param("org", org).param("s", studentId).param("c", classId)
                    .query(Integer.class).single() > 0;
            return new ClassWarnings(age, range, ClassWarnings.outOfRange(age, range), capacity, active, ClassWarnings.over(active, in, capacity));
        });
    }

    /** R4: grava tudo ou nada; a segunda chamada com a mesma modalidade é 409 e não muda nada. */
    public Concluded conclude(AccountPrincipal who, UUID studentId, Request req, String ip) {
        students.requireAccess(who);
        List<Enrollments.ModalityInput> inputs = inputs(req);
        Enrollments.checkInputs(inputs);
        String[] mail = new String[2]; // e-mail e código do convite, para enviar depois do commit
        Concluded done = transactions.execute(() -> {
            UUID org = who.organizationId();
            jdbc.sql("select id from student where organization_id = :org and id = :id for update").param("org", org).param("id", studentId)
                    .query(UUID.class).optional().orElseThrow(EnrollmentConclusion::missing);
            Students.Detail student = students.detail(who, studentId);
            for (Enrollments.ModalityInput m : inputs) { // unidade de outra organização não existe para quem chama (antes do alcance)
                jdbc.sql("select id from unit where organization_id = :org and id = :id").param("org", org).param("id", m.unitId()).query(UUID.class)
                        .optional().orElseThrow(EnrollmentConclusion::missing);
            }
            Facts facts = facts(org, studentId);
            Enrollments.Added added = enrollments.addLocked(who, studentId, inputs, ip);
            Preview summary = compute(who, req, facts);
            Map<UUID, UnitRow> unitRows = new HashMap<>();
            for (int i = 0; i < req.modalities().size(); i++) {
                ModalityRequest m = req.modalities().get(i);
                UnitRow unit = unitRows.computeIfAbsent(m.unitId(), id -> unit(org, id));
                if (m.benefit() != null) {
                    BenefitInput b = m.benefit();
                    benefits.grant(who, new BenefitWrite.Input(studentId, added.rowIds().get(i), b.type(), b.value(), b.reason(), b.decidedBy(), b.validUntil(),
                            false, b.teacherId()), ip);
                }
                if (m.origin() != null) registerOrigin(who, studentId, m, unit, ip);
            }
            OffsetDateTime now = OffsetDateTime.now(clock);
            Map<UUID, FirstCharges.UnitTerms> terms = new LinkedHashMap<>();
            for (UnitSummary u : summary.units()) {
                UUID startId = startUnit(who, studentId, added.enrollmentId(), u, now, ip);
                terms.put(u.unitId(), new FirstCharges.UnitTerms(u.unitId(), startId, u.firstDueOn(), u.toleranceDays(), u.feeCents(), u.secondDueOn()));
            }
            List<FirstCharges.Charge> charges = firstCharges.create(who, studentId, added.rowIds(), terms, ip);
            String invite = invite(who, student, mail);
            return new Concluded(enrollments.get(who, studentId), summary, true, invite, charges);
        });
        if (mail[1] == null) return done;
        try {
            invites.sendInvite(mail[0], mail[1]);
            return new Concluded(done.enrollment(), done.summary(), true, "SENT", done.charges());
        } catch (RuntimeException e) { // R8: a matrícula já está gravada; a falha fica no log (sem e-mail nem código)
            LOG.warn("Student invite e-mail was not sent after enrollment: {}", e.getClass().getSimpleName());
            return new Concluded(done.enrollment(), done.summary(), true, "FAILED", done.charges());
        }
    }

    /** RN-MAT-08: só o aluno adulto com e-mail e sem conta ativa na organização recebe convite próprio (D6). */
    private String invite(AccountPrincipal who, Students.Detail student, String[] mail) {
        if (student.minor()) return "MINOR";
        if (student.email() == null || student.email().isBlank()) return "NO_EMAIL";
        var issued = invites.inviteStudent(new Inviter(who.accountId(), who.role(), who.units()), who.organizationId(), student.email(), student.name());
        if (issued.code() == null) return "EXISTING_ACCOUNT";
        mail[0] = student.email();
        mail[1] = issued.code();
        return "SENT";
    }

    private Facts facts(UUID org, UUID studentId) {
        boolean exists = jdbc.sql("select count(*) from enrollment where organization_id = :org and student_id = :s").param("org", org).param("s", studentId)
                .query(Integer.class).single() > 0;
        Map<UUID, Integer> activeDue = new HashMap<>();
        int[] active = {0};
        jdbc.sql("select unit_id, due_day, status from enrollment_modality where organization_id = :org and student_id = :s").param("org", org)
                .param("s", studentId).query((rs, n) -> {
                    UUID unit = rs.getObject(1, UUID.class);
                    if ("ACTIVE".equals(rs.getString(3))) {
                        active[0]++;
                        activeDue.put(unit, rs.getInt(2));
                    }
                    return 0;
                }).list();
        // R21: taxa já tratada = cobrança de taxa do aluno na unidade (qualquer situação) ou isenção registrada no início da matrícula dela.
        Set<UUID> handled = new HashSet<>(jdbc.sql("select unit_id from charge where organization_id = :org and student_id = :s and type = 'ENROLLMENT_FEE' "
                + "union select es.unit_id from enrollment_start es join enrollment e on e.organization_id = es.organization_id and e.id = es.enrollment_id "
                + "where es.organization_id = :org and e.student_id = :s and es.fee_exempt_reason is not null").param("org", org).param("s", studentId)
                .query(UUID.class).list());
        return new Facts(studentId, exists, active[0], activeDue, handled);
    }

    private UnitRow unit(UUID org, UUID id) {
        return jdbc.sql("select u.name, u.active, u.timezone, s.due_days, s.enrollment_fee_enabled, s.enrollment_fee_cents from unit u "
                + "join unit_settings s on s.unit_id = u.id where u.organization_id = :org and u.id = :id").param("org", org).param("id", id)
                .query((rs, n) -> new UnitRow(id, rs.getString(1), rs.getBoolean(2), ZoneId.of(rs.getString(3)),
                        Arrays.asList((Integer[]) rs.getArray(4).getArray()), rs.getBoolean(5), rs.getLong(6)))
                .optional().orElseThrow(EnrollmentConclusion::missing);
    }

    /** R1, R2, R3: valores por modalidade e, por unidade, vencimento, taxa e segunda cobrança. Só lê. */
    private Preview compute(AccountPrincipal who, Request req, Facts facts) {
        UUID org = who.organizationId();
        Map<UUID, UnitRow> units = new LinkedHashMap<>();
        for (ModalityRequest m : req.modalities()) {
            if (!units.containsKey(m.unitId())) {
                UnitRow row = unit(org, m.unitId());
                requireManage(who, m.unitId());
                if (!row.active()) throw rule("unitId", "unidade desativada");
                units.put(m.unitId(), row);
            }
        }
        Map<UUID, UnitRequest> asked = new HashMap<>();
        if (req.units() != null) {
            for (UnitRequest u : req.units()) {
                if (u == null || u.unitId() == null) throw new ApiValidationException("units", "obrigatório");
                if (!units.containsKey(u.unitId()) || asked.put(u.unitId(), u) != null) throw rule("units", "unidade fora do pedido ou repetida");
            }
        }
        List<ModalitySummary> modalities = new ArrayList<>();
        Map<UUID, Long> monthlyByUnit = new HashMap<>();
        for (ModalityRequest m : req.modalities()) {
            var plan = jdbc.sql("select name, value_cents, unit_id, modality_id, status from plan where organization_id = :org and id = :id").param("org", org)
                    .param("id", m.planId()).query((rs, n) -> new Object[] {rs.getString(1), rs.getLong(2), rs.getObject(3, UUID.class),
                            rs.getObject(4, UUID.class), rs.getString(5)}).optional().orElseThrow(EnrollmentConclusion::missing);
            if (!m.unitId().equals(plan[2]) || !m.modalityId().equals(plan[3]) || !"ACTIVE".equals(plan[4])) {
                throw rule("planId", "plano inativo ou de outra unidade ou modalidade");
            }
            long planCents = (Long) plan[1], monthly = planCents;
            if (m.benefit() != null) {
                String type = BenefitWrite.type(m.benefit().type());
                monthly = Benefits.monthlyValueCents(type, BenefitWrite.value(type, m.benefit().value()), planCents);
            }
            modalities.add(new ModalitySummary(m.unitId(), m.modalityId(), m.planId(), (String) plan[0], planCents, planCents - monthly, monthly));
            monthlyByUnit.merge(m.unitId(), monthly, Long::sum);
        }
        List<UnitSummary> summaries = new ArrayList<>();
        for (UnitRow unit : units.values()) {
            LocalDate today = LocalDate.ofInstant(clock.instant(), unit.zone());
            int due = dueDay(req, unit, facts);
            long standard = fee(unit.feeEnabled(), unit.feeCents(), facts.feeHandled().contains(unit.id()), facts.returning());
            UnitRequest ask = asked.get(unit.id());
            long charged = standard;
            String reason = null;
            if (ask != null && Boolean.TRUE.equals(ask.feeExempt())) {
                String text = ask.feeExemptReason() == null ? "" : ask.feeExemptReason().strip();
                if (text.length() < 3 || text.length() > 200) throw rule("feeExemptReason", "de 3 a 200 caracteres");
                if (!unit.feeEnabled()) throw rule("feeExemptReason", "a unidade não cobra taxa de matrícula");
                if (standard > 0) {
                    charged = 0;
                    reason = text;
                }
            }
            LocalDate suggested = suggestSecond(today, due), second = suggested;
            if (ask != null && ask.secondDueOn() != null) {
                second = ask.secondDueOn();
                if (second.isBefore(today.plusDays(MIN_SECOND_DAYS)) || second.isAfter(today.plusDays(MAX_SECOND_DAYS))) {
                    throw rule("secondDueOn", "entre 15 e 60 dias da matrícula");
                }
            }
            summaries.add(new UnitSummary(unit.id(), unit.name(), due, today, TOLERANCE_DAYS, charged, reason != null, reason,
                    monthlyByUnit.get(unit.id()) + charged, second, suggested));
        }
        var debt = jdbc.sql("select count(*), coalesce(sum(amount_cents), 0) from charge where organization_id = :org and student_id = :s and status = 'OPEN' "
                + "and unit_id in (:units)").param("org", org).param("s", facts.studentId()).param("units", units.keySet())
                .query((rs, n) -> new OpenDebt(rs.getInt(1), rs.getLong(2))).single();
        return new Preview(modalities, summaries, debt);
    }

    /** RN-MAT-03: um dia de vencimento por unidade, entre os permitidos; quem já paga na unidade mantém o dia. */
    private int dueDay(Request req, UnitRow unit, Facts facts) {
        Set<Integer> asked = new HashSet<>();
        for (ModalityRequest m : req.modalities()) if (m.unitId().equals(unit.id()) && m.dueDay() != null) asked.add(m.dueDay());
        Integer inherited = facts.activeDue().get(unit.id());
        if (inherited != null) {
            if (!asked.isEmpty() && !asked.equals(Set.of(inherited))) throw rule("dueDay", "o aluno já paga nesta unidade no dia " + inherited);
            return inherited;
        }
        if (asked.isEmpty()) throw new ApiValidationException("dueDay", "obrigatório");
        if (asked.size() > 1) throw rule("dueDay", "um único dia de vencimento por unidade");
        int due = asked.iterator().next();
        if (!unit.dueDays().contains(due)) throw rule("dueDay", "dia não permitido pela unidade");
        return due;
    }

    /** R6: valida e grava a graduação de origem da modalidade recém-criada. */
    private void registerOrigin(AccountPrincipal who, UUID studentId, ModalityRequest m, UnitRow unit, String ip) {
        UUID org = who.organizationId();
        OriginInput o = m.origin();
        var options = jdbc.sql("select graduation_enabled, require_origin_proof from organization_modality where organization_id = :org and modality_id = :m")
                .param("org", org).param("m", m.modalityId()).query((rs, n) -> new boolean[] {rs.getBoolean(1), rs.getBoolean(2)}).optional()
                .orElseThrow(EnrollmentConclusion::missing);
        if (!options[0]) throw rule("origin", "a modalidade não tem graduação ligada");
        if (o.levelId() == null) throw new ApiValidationException("origin.levelId", "obrigatório");
        var level = jdbc.sql("select modality_id, max_degree from graduation_level where organization_id = :org and id = :id").param("org", org)
                .param("id", o.levelId()).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getInt(2)}).optional()
                .orElseThrow(EnrollmentConclusion::missing);
        if (!m.modalityId().equals(level[0])) throw rule("origin.levelId", "nível de outra modalidade");
        if (o.degree() == null) throw new ApiValidationException("origin.degree", "obrigatório");
        if (o.degree() < 0 || o.degree() > (Integer) level[1]) throw rule("origin.degree", "de 0 a " + level[1]);
        if (o.achievedOn() != null && o.achievedOn().isAfter(LocalDate.ofInstant(clock.instant(), unit.zone()))) {
            throw rule("origin.achievedOn", "não pode ser no futuro");
        }
        String key = o.proofKey() == null || o.proofKey().isBlank() ? null : o.proofKey();
        if (key == null && options[1]) throw rule("origin.proofKey", "a modalidade exige o comprovante da graduação de origem");
        if (key != null && !proofs.valid(studentId, key)) throw rule("origin.proofKey", "comprovante inválido");
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into student_graduation (id, organization_id, student_id, modality_id, level_id, degree, achieved_on, origin, proof_file, has_proof, "
                + "registered_by_account_id) values (:id, :org, :s, :m, :level, :degree, :on, true, :proof, :has, :by)").param("id", id).param("org", org)
                .param("s", studentId).param("m", m.modalityId()).param("level", o.levelId()).param("degree", o.degree()).param("on", o.achievedOn())
                .param("proof", key).param("has", key != null).param("by", who.accountId()).update();
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("modalityId", m.modalityId().toString());
        after.put("levelId", o.levelId().toString());
        after.put("degree", o.degree());
        after.put("achievedOn", o.achievedOn() == null ? null : o.achievedOn().toString());
        after.put("hasProof", key != null); // a chave do arquivo não vai para a auditoria
        audit.record(new AuditEntry(actor(who), "student.origin_graduation_registered", "student", studentId.toString(), m.unitId(), null, after, null, ip));
    }

    /** R5: guarda a taxa, a isenção e a segunda cobrança da unidade; devolve o id do início, a que a cobrança da taxa se liga. */
    private UUID startUnit(AccountPrincipal who, UUID studentId, UUID enrollmentId, UnitSummary u, OffsetDateTime now, String ip) {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into enrollment_start (id, organization_id, enrollment_id, unit_id, concluded_at, fee_cents, fee_exempt_reason, second_due_on) "
                + "values (:id, :org, :e, :unit, :now, :fee, :reason, :second)").param("id", id).param("org", who.organizationId())
                .param("e", enrollmentId).param("unit", u.unitId()).param("now", now).param("fee", u.feeCents()).param("reason", u.feeExemptReason())
                .param("second", u.secondDueOn()).update();
        if (u.feeExempt()) {
            audit.record(new AuditEntry(actor(who), "enrollment.fee_exempted", "student", studentId.toString(), u.unitId(), null,
                    Map.of("unitId", u.unitId().toString(), "reason", u.feeExemptReason()), null, ip));
        }
        return id;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }
}
