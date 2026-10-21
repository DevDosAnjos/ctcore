package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiError;
import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-MAT-01, 02, 03, 04 e 19: a matrícula do aluno (uma por organização) e as suas modalidades. Cada pedido roda numa transação só, com o
 * aluno travado, e valida tudo antes de gravar. A situação do aluno é calculada das modalidades, não gravada (D7).
 */
@Service
public class Enrollments {
    static final int MAX_ACTIVE = 10;

    public record ModalityInput(UUID unitId, UUID modalityId, UUID primaryClassId, List<UUID> otherClassIds, UUID planId, Integer dueDay) {}

    public record AddInput(List<ModalityInput> modalities) {}

    public record Named(UUID id, String name) {}

    public record ModalityView(UUID id, Named unit, Named modality, Named primaryClass, List<Named> otherClasses, Named plan, Named nextPlan,
            int dueDay, String status, OffsetDateTime startedAt, OffsetDateTime endedAt, boolean canEdit) {}

    /** {@code status}: NONE (sem matrícula), ACTIVE ou FORMER (ex-aluno). */
    public record Enrollment(UUID id, String status, OffsetDateTime enrolledAt, OffsetDateTime reopenedAt, List<ModalityView> modalities) {}

    private record Active(UUID unitId, UUID modalityId, int dueDay) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;
    private final Permissions permissions;
    private final AuditService audit;
    private final Clock clock;

    public Enrollments(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students, Permissions permissions, AuditService audit,
            Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
        this.permissions = permissions;
        this.audit = audit;
        this.clock = clock;
    }

    private static ApiStatusException rule(String field, String message) {
        return new ApiStatusException(ErrorCode.UNPROCESSABLE, List.of(new ApiError.Field(field, message)));
    }

    private static ApiStatusException missing() {
        return new ApiStatusException(ErrorCode.NOT_FOUND);
    }

    /** R3: grava as modalidades pedidas (cria a matrícula, acrescenta ou reabre), tudo ou nada. */
    public Enrollment add(AccountPrincipal who, UUID studentId, AddInput in, String ip) {
        students.requireAccess(who);
        checkInputs(in == null ? null : in.modalities());
        return transactions.execute(() -> {
            addLocked(who, studentId, in.modalities(), ip);
            return read(who, studentId);
        });
    }

    /** Formato do pedido: campos obrigatórios e dia de vencimento de 1 a 28 (400). */
    static void checkInputs(List<ModalityInput> inputs) {
        if (inputs == null || inputs.isEmpty()) throw new ApiValidationException("modalities", "obrigatório");
        for (ModalityInput m : inputs) {
            if (m == null) throw new ApiValidationException("modalities", "obrigatório");
            if (m.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
            if (m.modalityId() == null) throw new ApiValidationException("modalityId", "obrigatório");
            if (m.primaryClassId() == null) throw new ApiValidationException("primaryClassId", "obrigatório");
            if (m.planId() == null) throw new ApiValidationException("planId", "obrigatório");
            if (m.dueDay() != null && (m.dueDay() < 1 || m.dueDay() > 28)) throw new ApiValidationException("dueDay", "de 1 a 28");
        }
    }

    /** O que {@link #addLocked} gravou: as linhas de modalidade na ordem do pedido e se a matrícula é nova ou reaberta. */
    record Added(UUID enrollmentId, boolean created, boolean reopened, List<UUID> rowIds) {}

    /** Dentro de uma transação da organização: trava o aluno (o limite de 10 e a matrícula única valem sob concorrência) e grava as modalidades. */
    Added addLocked(AccountPrincipal who, UUID studentId, List<ModalityInput> inputs, String ip) {
        jdbc.sql("select id from student where organization_id = :org and id = :id for update").param("org", who.organizationId()).param("id", studentId)
                .query(UUID.class).optional().orElseThrow(Enrollments::missing);
        students.detail(who, studentId);
        return doAdd(who, studentId, inputs, ip);
    }

    private Added doAdd(AccountPrincipal who, UUID studentId, List<ModalityInput> inputs, String ip) {
        UUID org = who.organizationId();
        var existing = jdbc.sql("select id from enrollment where organization_id = :org and student_id = :s").param("org", org)
                .param("s", studentId).query((rs, n) -> rs.getObject(1, UUID.class)).optional();
        List<Active> active = existing.isEmpty() ? List.of() : jdbc.sql("select unit_id, modality_id, due_day from enrollment_modality "
                + "where organization_id = :org and student_id = :s and status = 'ACTIVE'").param("org", org).param("s", studentId)
                .query((rs, n) -> new Active(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3))).list();
        Set<UUID> activeKinds = new HashSet<>();
        Map<UUID, Integer> dueByUnit = new HashMap<>();
        for (Active a : active) {
            activeKinds.add(a.modalityId());
            dueByUnit.put(a.unitId(), a.dueDay());
        }
        for (ModalityInput m : inputs) {
            if (!activeKinds.add(m.modalityId())) {
                throw new ApiStatusException(ErrorCode.CONFLICT, List.of(new ApiError.Field("modalityId", "o aluno já tem esta modalidade ativa")));
            }
        }
        if (active.size() + inputs.size() > MAX_ACTIVE) throw rule("modalities", "no máximo " + MAX_ACTIVE + " modalidades ativas");

        List<Resolved> resolved = new ArrayList<>();
        for (ModalityInput m : inputs) {
            if (permissions.grant(who, Permission.STUDENT_MANAGE, m.unitId()) == Access.NONE) {
                throw new org.springframework.security.access.AccessDeniedException("Permission denied");
            }
            resolved.add(validate(org, m, dueByUnit));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        boolean reopened = false;
        UUID enrollmentId;
        if (existing.isEmpty()) {
            enrollmentId = UUID.randomUUID();
            jdbc.sql("insert into enrollment (id, organization_id, student_id, enrolled_at) values (:id, :org, :s, :now)").param("id", enrollmentId)
                    .param("org", org).param("s", studentId).param("now", now).update();
        } else {
            enrollmentId = existing.get();
            if (active.isEmpty()) {
                reopened = true; // RN-MAT-19: a mesma matrícula volta; o selo Novo passa a contar de reopened_at (D8)
                jdbc.sql("update enrollment set reopened_at = :now where organization_id = :org and id = :id").param("now", now).param("org", org)
                        .param("id", enrollmentId).update();
            }
        }
        UUID registered = resolved.get(0).input.unitId();
        if (existing.isEmpty() || reopened) {
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("enrollmentId", enrollmentId.toString());
            audit.record(new AuditEntry(actor(who), existing.isEmpty() ? "student.enrolled" : "student.enrollment_reopened", "student", studentId.toString(),
                    registered, null, after, null, ip));
        }
        List<UUID> rowIds = new ArrayList<>();
        for (Resolved r : resolved) {
            ModalityInput m = r.input;
            UUID rowId = UUID.randomUUID();
            rowIds.add(rowId);
            jdbc.sql("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, "
                    + "due_day, status, started_at) values (:id, :org, :e, :s, :unit, :mod, :class, :plan, :due, 'ACTIVE', :now)").param("id", rowId)
                    .param("org", org).param("e", enrollmentId).param("s", studentId).param("unit", m.unitId()).param("mod", m.modalityId())
                    .param("class", m.primaryClassId()).param("plan", m.planId()).param("due", r.dueDay).param("now", now).update();
            for (UUID other : r.others) {
                jdbc.sql("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (:org, :row, :class)").param("org", org)
                        .param("row", rowId).param("class", other).update();
            }
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("modalityRowId", rowId.toString());
            after.put("unitId", m.unitId().toString());
            after.put("modalityId", m.modalityId().toString());
            after.put("primaryClassId", m.primaryClassId().toString());
            after.put("planId", m.planId().toString());
            after.put("dueDay", r.dueDay);
            audit.record(new AuditEntry(actor(who), "student.modality_added", "student", studentId.toString(), m.unitId(), null, after, null, ip));
        }
        return new Added(enrollmentId, existing.isEmpty(), reopened, rowIds);
    }

    private record Resolved(ModalityInput input, int dueDay, Set<UUID> others) {}

    /** R3 e R4 para uma modalidade; {@code dueByUnit} traz o vencimento das ativas por unidade e recebe o desta. */
    private Resolved validate(UUID org, ModalityInput m, Map<UUID, Integer> dueByUnit) {
        var unit = jdbc.sql("select active, (select due_days from unit_settings s where s.unit_id = u.id) from unit u where u.organization_id = :org and u.id = :id")
                .param("org", org).param("id", m.unitId()).query((rs, n) -> new Object[] {rs.getBoolean(1), (Object[]) rs.getArray(2).getArray()}).optional()
                .orElseThrow(Enrollments::missing);
        if (!(Boolean) unit[0]) throw rule("unitId", "unidade desativada");
        boolean offered = jdbc.sql("select count(*) from organization_modality om join unit_modality um on um.organization_id = om.organization_id "
                + "and um.modality_id = om.modality_id where om.organization_id = :org and om.modality_id = :mod and om.active and um.unit_id = :unit and um.offered")
                .param("org", org).param("mod", m.modalityId()).param("unit", m.unitId()).query(Long.class).single() > 0;
        if (!offered) throw rule("modalityId", "modalidade inativa ou não oferecida pela unidade");
        Set<UUID> others = validateClasses(org, m);
        validatePlan(org, m.unitId(), m.modalityId(), m.planId());
        Integer inherited = dueByUnit.get(m.unitId());
        int due;
        if (inherited != null) { // RN-MAT-03: o dia é um só por unidade
            if (m.dueDay() != null && m.dueDay() != inherited) throw rule("dueDay", "o aluno já paga nesta unidade no dia " + inherited);
            due = inherited;
        } else {
            if (m.dueDay() == null) throw new ApiValidationException("dueDay", "obrigatório");
            if (!allowed(unit[1], m.dueDay())) throw rule("dueDay", "dia não permitido pela unidade");
            due = m.dueDay();
        }
        dueByUnit.put(m.unitId(), due);
        return new Resolved(m, due, others);
    }

    /** R3: turma principal e outros horários ativos da unidade e da modalidade, diferentes entre si; devolve os outros horários sem repetição. */
    private Set<UUID> validateClasses(UUID org, ModalityInput m) {
        requireClass(org, m.primaryClassId(), m, "primaryClassId");
        Set<UUID> others = new LinkedHashSet<>(m.otherClassIds() == null ? List.of() : m.otherClassIds());
        for (UUID other : others) {
            if (other == null) throw new ApiValidationException("otherClassIds", "inválido");
            if (other.equals(m.primaryClassId())) throw rule("otherClassIds", "igual à turma principal");
            requireClass(org, other, m, "otherClassIds");
        }
        return others;
    }

    private void validatePlan(UUID org, UUID unitId, UUID modalityId, UUID planId) {
        var plan = jdbc.sql("select unit_id, modality_id, status from plan where organization_id = :org and id = :id").param("org", org)
                .param("id", planId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)})
                .optional().orElseThrow(Enrollments::missing);
        if (!unitId.equals(plan[0]) || !modalityId.equals(plan[1]) || !"ACTIVE".equals(plan[2])) {
            throw rule("planId", "plano inativo ou de outra unidade ou modalidade");
        }
    }

    private static boolean allowed(Object days, int day) {
        for (Object d : (Object[]) days) if (((Number) d).intValue() == day) return true;
        return false;
    }

    private void requireClass(UUID org, UUID classId, ModalityInput m, String field) {
        var c = jdbc.sql("select unit_id, modality_id, status from training_class where organization_id = :org and id = :id").param("org", org)
                .param("id", classId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3)})
                .optional().orElseThrow(Enrollments::missing);
        if (!m.unitId().equals(c[0]) || !m.modalityId().equals(c[1]) || !"ACTIVE".equals(c[2])) {
            throw rule(field, "turma inativa ou de outra unidade ou modalidade");
        }
    }

    public record ClassInput(UUID primaryClassId, List<UUID> otherClassIds) {}

    public record PlanInput(UUID planId) {}

    public record TransferInput(UUID unitId, UUID primaryClassId, List<UUID> otherClassIds, UUID planId, Integer dueDay) {}

    private record Row(UUID unitId, UUID modalityId, UUID primaryClassId, UUID planId, UUID nextPlanId, int dueDay) {}

    /** Trava o aluno, confere o alcance e devolve a modalidade ativa dele que o chamador pode alterar (RN-ALU-07: só as da sua unidade). */
    private Row lockRow(AccountPrincipal who, UUID studentId, UUID rowId) {
        students.requireAccess(who);
        UUID org = who.organizationId();
        jdbc.sql("select id from student where organization_id = :org and id = :id for update").param("org", org).param("id", studentId)
                .query(UUID.class).optional().orElseThrow(Enrollments::missing);
        students.detail(who, studentId);
        Row row = jdbc.sql("select unit_id, modality_id, primary_class_id, plan_id, next_plan_id, due_day from enrollment_modality "
                + "where organization_id = :org and id = :id and student_id = :s and status = 'ACTIVE'").param("org", org).param("id", rowId).param("s", studentId)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getInt(6)))
                .optional().orElseThrow(Enrollments::missing);
        requireManage(who, row.unitId());
        return row;
    }

    private void requireManage(AccountPrincipal who, UUID unitId) {
        if (permissions.grant(who, Permission.STUDENT_MANAGE, unitId) == Access.NONE) {
            throw new org.springframework.security.access.AccessDeniedException("Permission denied");
        }
    }

    private void replaceOthers(UUID org, UUID rowId, Set<UUID> others) {
        jdbc.sql("delete from enrollment_modality_class where organization_id = :org and modality_row_id = :row").param("org", org).param("row", rowId).update();
        for (UUID other : others) {
            jdbc.sql("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (:org, :row, :class)").param("org", org)
                    .param("row", rowId).param("class", other).update();
        }
    }

    private List<String> otherIds(UUID org, UUID rowId) {
        return jdbc.sql("select class_id::text from enrollment_modality_class where organization_id = :org and modality_row_id = :row order by 1")
                .param("org", org).param("row", rowId).query(String.class).list();
    }

    /** RN-MAT-15 (R9): troca a turma principal e os outros horários; vale na hora. */
    public Enrollment changeClass(AccountPrincipal who, UUID studentId, UUID rowId, ClassInput in, String ip) {
        if (in == null || in.primaryClassId() == null) throw new ApiValidationException("primaryClassId", "obrigatório");
        return transactions.execute(() -> {
            Row row = lockRow(who, studentId, rowId);
            UUID org = who.organizationId();
            Set<UUID> others = validateClasses(org, new ModalityInput(row.unitId(), row.modalityId(), in.primaryClassId(), in.otherClassIds(), null, null));
            Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
            before.put("modalityRowId", rowId.toString());
            before.put("primaryClassId", row.primaryClassId().toString());
            before.put("otherClassIds", otherIds(org, rowId));
            after.put("modalityRowId", rowId.toString());
            after.put("primaryClassId", in.primaryClassId().toString());
            after.put("otherClassIds", others.stream().map(UUID::toString).sorted().toList());
            jdbc.sql("update enrollment_modality set primary_class_id = :c where organization_id = :org and id = :id").param("c", in.primaryClassId())
                    .param("org", org).param("id", rowId).update();
            replaceOthers(org, rowId, others);
            audit.record(new AuditEntry(actor(who), "student.class_changed", "student", studentId.toString(), row.unitId(), before, after, null, ip));
            return read(who, studentId);
        });
    }

    /** RN-MAT-15, D10 (R10): guarda o plano novo em next_plan_id (vale na próxima cobrança); pedir o plano atual limpa o agendamento. */
    public Enrollment changePlan(AccountPrincipal who, UUID studentId, UUID rowId, PlanInput in, String ip) {
        if (in == null || in.planId() == null) throw new ApiValidationException("planId", "obrigatório");
        return transactions.execute(() -> {
            Row row = lockRow(who, studentId, rowId);
            UUID org = who.organizationId();
            boolean current = in.planId().equals(row.planId());
            if (current && row.nextPlanId() == null) throw rule("planId", "já é o plano atual");
            validatePlan(org, row.unitId(), row.modalityId(), in.planId());
            UUID next = current ? null : in.planId();
            Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
            before.put("modalityRowId", rowId.toString());
            before.put("planId", row.planId().toString());
            before.put("nextPlanId", row.nextPlanId() == null ? null : row.nextPlanId().toString());
            after.put("modalityRowId", rowId.toString());
            after.put("planId", row.planId().toString());
            after.put("nextPlanId", next == null ? null : next.toString());
            jdbc.sql("update enrollment_modality set next_plan_id = :n where organization_id = :org and id = :id").param("n", next).param("org", org)
                    .param("id", rowId).update();
            audit.record(new AuditEntry(actor(who), "student.plan_changed", "student", studentId.toString(), row.unitId(), before, after, null, ip));
            return read(who, studentId);
        });
    }

    /** RN-MAT-20, RN-ORG-12 (R11): leva a mesma linha para outra unidade da organização; origem e destino na auditoria. Não escreve em outra tabela (D17). */
    public Enrollment transfer(AccountPrincipal who, UUID studentId, UUID rowId, TransferInput in, String ip) {
        if (in == null || in.unitId() == null) throw new ApiValidationException("unitId", "obrigatório");
        if (in.primaryClassId() == null) throw new ApiValidationException("primaryClassId", "obrigatório");
        if (in.planId() == null) throw new ApiValidationException("planId", "obrigatório");
        if (in.dueDay() != null && (in.dueDay() < 1 || in.dueDay() > 28)) throw new ApiValidationException("dueDay", "de 1 a 28");
        return transactions.execute(() -> {
            Row row = lockRow(who, studentId, rowId);
            UUID org = who.organizationId();
            jdbc.sql("select id from unit where organization_id = :org and id = :id").param("org", org).param("id", in.unitId()).query(UUID.class).optional()
                    .orElseThrow(Enrollments::missing); // unidade de outra organização não existe para quem chama
            requireManage(who, in.unitId()); // D12: alcance também no destino
            if (in.unitId().equals(row.unitId())) throw rule("unitId", "a modalidade já está nesta unidade");
            Map<UUID, Integer> dueByUnit = new HashMap<>();
            jdbc.sql("select unit_id, due_day from enrollment_modality where organization_id = :org and student_id = :s and status = 'ACTIVE' and id <> :id")
                    .param("org", org).param("s", studentId).param("id", rowId).query((rs, n) -> dueByUnit.put(rs.getObject(1, UUID.class), rs.getInt(2))).list();
            Resolved r = validate(org, new ModalityInput(in.unitId(), row.modalityId(), in.primaryClassId(), in.otherClassIds(), in.planId(), in.dueDay()), dueByUnit);
            Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
            before.put("modalityRowId", rowId.toString());
            before.put("unitId", row.unitId().toString());
            before.put("primaryClassId", row.primaryClassId().toString());
            before.put("planId", row.planId().toString());
            before.put("dueDay", row.dueDay());
            after.put("modalityRowId", rowId.toString());
            after.put("unitId", in.unitId().toString());
            after.put("primaryClassId", in.primaryClassId().toString());
            after.put("planId", in.planId().toString());
            after.put("dueDay", r.dueDay);
            // Nova unidade, novo plano: o plano agendado da unidade antiga não vale mais.
            jdbc.sql("update enrollment_modality set unit_id = :u, primary_class_id = :c, plan_id = :p, next_plan_id = null, due_day = :d "
                    + "where organization_id = :org and id = :id").param("u", in.unitId()).param("c", in.primaryClassId()).param("p", in.planId())
                    .param("d", r.dueDay).param("org", org).param("id", rowId).update();
            replaceOthers(org, rowId, r.others);
            audit.record(new AuditEntry(actor(who), "student.modality_transferred", "student", studentId.toString(), in.unitId(), before, after, null, ip));
            return read(who, studentId);
        });
    }

    /** R6: a matrícula do aluno; sem matrícula, situação NONE e lista vazia. */
    public Enrollment get(AccountPrincipal who, UUID studentId) {
        students.requireAccess(who);
        return transactions.execute(() -> {
            students.detail(who, studentId);
            return read(who, studentId);
        });
    }

    private Enrollment read(AccountPrincipal who, UUID studentId) {
        UUID org = who.organizationId();
        var header = jdbc.sql("select id, enrolled_at, reopened_at from enrollment where organization_id = :org and student_id = :s").param("org", org)
                .param("s", studentId).query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, OffsetDateTime.class),
                        rs.getObject(3, OffsetDateTime.class)}).optional();
        if (header.isEmpty()) return new Enrollment(null, "NONE", null, null, List.of());
        Map<UUID, List<Named>> others = new HashMap<>();
        jdbc.sql("select x.modality_row_id, c.id, c.name from enrollment_modality_class x join training_class c on c.organization_id = x.organization_id "
                + "and c.id = x.class_id where x.organization_id = :org and x.modality_row_id in (select id from enrollment_modality "
                + "where organization_id = :org and student_id = :s) order by c.name_key, c.id").param("org", org).param("s", studentId)
                .query((rs, n) -> {
                    others.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(new Named(rs.getObject(2, UUID.class), rs.getString(3)));
                    return 0;
                }).list();
        Map<UUID, Boolean> editable = new HashMap<>();
        List<ModalityView> views = jdbc.sql("select m.id, m.unit_id, u.name, m.modality_id, md.name, m.primary_class_id, c.name, m.plan_id, p.name, "
                + "m.next_plan_id, np.name, m.due_day, m.status, m.started_at, m.ended_at from enrollment_modality m "
                + "join unit u on u.organization_id = m.organization_id and u.id = m.unit_id join modality md on md.id = m.modality_id "
                + "join training_class c on c.organization_id = m.organization_id and c.id = m.primary_class_id "
                + "join plan p on p.organization_id = m.organization_id and p.id = m.plan_id "
                + "left join plan np on np.organization_id = m.organization_id and np.id = m.next_plan_id "
                + "where m.organization_id = :org and m.student_id = :s order by m.status, m.started_at, m.id").param("org", org).param("s", studentId)
                .query((rs, n) -> {
                    UUID unit = rs.getObject(2, UUID.class), row = rs.getObject(1, UUID.class);
                    UUID next = rs.getObject(10, UUID.class);
                    boolean edit = editable.computeIfAbsent(unit, k -> permissions.grant(who, Permission.STUDENT_MANAGE, k) != Access.NONE);
                    return new ModalityView(row, new Named(unit, rs.getString(3)), new Named(rs.getObject(4, UUID.class), rs.getString(5)),
                            new Named(rs.getObject(6, UUID.class), rs.getString(7)), others.getOrDefault(row, List.of()),
                            new Named(rs.getObject(8, UUID.class), rs.getString(9)), next == null ? null : new Named(next, rs.getString(11)),
                            rs.getInt(12), rs.getString(13), rs.getObject(14, OffsetDateTime.class), rs.getObject(15, OffsetDateTime.class), edit);
                }).list();
        boolean anyActive = views.stream().anyMatch(v -> "ACTIVE".equals(v.status()));
        return new Enrollment((UUID) header.get()[0], anyActive ? "ACTIVE" : "FORMER", (OffsetDateTime) header.get()[1], (OffsetDateTime) header.get()[2], views);
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }
}
