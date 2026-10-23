package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.billing.Charges;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileStorage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-ALU-01: leitura do aluno para a gestão (detalhe e busca por CPF). Organização, perfil e alcance vêm da sessão. O alcance do Administrador
 * é ter modalidade em unidade dele (RN-ALU-07, D11), ou a unidade de cadastro se não tem nenhuma; fora dele, é o mesmo 404 de quem não existe.
 */
@Service
public class Students {
    private static final Logger LOG = LoggerFactory.getLogger(Students.class);

    /** O que a recusa por duplicidade e a busca por CPF mostram: só nome e foto (RN-ALU-06). */
    public record Ref(UUID id, String name, String photoUrl) {}

    /** R8: a busca da matrícula; {@code enrollmentStatus} é NONE, ACTIVE ou FORMER. */
    public record Lookup(UUID id, String name, String photoUrl, String enrollmentStatus) {}

    public record GuardianRow(UUID guardianId, String name, String email, String phone, boolean financial, String access, boolean emailProblem) {}

    public record Detail(UUID id, UUID registeredUnitId, String name, LocalDate birthDate, boolean minor, String postalCode, String street,
            String number, String complement, String district, String city, String state, String cpf, String email, String phone,
            String gender, String referral, String emergencyName, String emergencyRelationship, String emergencyPhone, String photoUrl,
            boolean emailProblem, List<GuardianRow> guardians) {}

    public record UnitRef(UUID id, String name) {}

    public record RangeRef(UUID id, String name) {}

    /** RF-ALU-01: linha da lista, sem CPF, e-mail, telefone, endereço nem emergência (minimização). */
    public record Row(UUID id, String name, String photoUrl, LocalDate birthDate, int age, boolean minor, RangeRef ageRange, UnitRef registeredUnit,
            OffsetDateTime createdAt, List<StudentSeal> seals, String status, String financialStatus, List<RowModality> modalities) {
        Row withModalities(List<RowModality> list) {
            return new Row(id, name, photoUrl, birthDate, age, minor, ageRange, registeredUnit, createdAt, seals, status, financialStatus, list);
        }
    }

    /** R18: modalidade ativa na linha da lista; {@code level} é nulo até a graduação (#105 e #106, D13). */
    public record RowModality(UUID id, UnitRef unit, Enrollments.Named modality, Enrollments.Named primaryClass, Enrollments.Named level) {}

    /** R20: contagens dos filtros rápidos, sobre o alcance e a unidade, sem o filtro rápido. */
    public record Counts(long active, long former, long overdue) {}

    /** R19: filtros da lista; {@code status} é ACTIVE ou FORMER (filtro rápido). */
    public record Filter(String q, UUID unitId, UUID ageRangeId, String seal, UUID modalityId, UUID classId, UUID levelId, String status, String financial) {}

    public record StudentList(List<Row> items, int page, int size, long total, Counts counts) {}

    /** RF-ALU-06: aniversariante do dia, sem CPF, e-mail, endereço nem data de nascimento; o contato é o do WhatsApp. */
    public record Birthday(UUID id, String name, String photoUrl, int age, RangeRef ageRange, UnitRef registeredUnit, String contactName,
            String contactPhone, boolean contactIsGuardian) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final FileStorage storage;
    private final Clock clock;

    public Students(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, FileStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.storage = storage;
        this.clock = clock;
    }

    void requireAccess(AccountPrincipal who) {
        if (who == null || who.role() == null || permissions.effective(who).get(Permission.STUDENT_MANAGE) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
    }

    /**
     * RF-ALU-01: lista paginada por nome. O alcance do Administrador (RN-ALU-07) fica em {@link #reachPredicate}, o único ponto que a
     * matrícula (#63) troca. Faixa de outra organização dá 404; unidade fora do alcance, lista vazia.
     */
    public StudentList list(AccountPrincipal who, Filter f, PageRequest page) {
        requireAccess(who);
        String q = f.q(), seal = f.seal();
        UUID unitId = f.unitId(), ageRangeId = f.ageRangeId();
        StudentSeal wanted = seal == null ? null : StudentSeal.parse(seal).orElseThrow(() -> new ApiValidationException("seal", "inválido"));
        if (f.status() != null && !f.status().equals("ACTIVE") && !f.status().equals("FORMER")) throw new ApiValidationException("status", "inválido");
        if (f.financial() != null && !f.financial().equals("OVERDUE")) throw new ApiValidationException("financial", "inválido");
        return transactions.execute(() -> {
            // R19: unidade ou turma que não existe na organização é 404, como a faixa etária.
            if (unitId != null) exists("unit", who.organizationId(), unitId);
            if (f.classId() != null) exists("training_class", who.organizationId(), f.classId());
            Integer min = null, max = null;
            if (ageRangeId != null) {
                var limits = jdbc.sql("select min_age, max_age from age_range where organization_id = :org and id = :id").param("org", who.organizationId())
                        .param("id", ageRangeId).query((rs, n) -> new Integer[] {rs.getInt(1), (Integer) rs.getObject(2)}).optional()
                        .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                min = limits[0];
                max = limits[1];
            }
            boolean admin = who.role() == Role.ADMINISTRATOR;
            Set<UUID> reach = who.units() == null ? Set.of() : who.units();
            if (admin && (reach.isEmpty() || unitId != null && !reach.contains(unitId))) {
                return new StudentList(List.of(), page.page(), page.size(), 0, new Counts(0, 0, 0));
            }

            String age = AGE;
            StringBuilder where = new StringBuilder("s.organization_id = :org");
            Map<String, Object> params = new HashMap<>();
            params.put("org", who.organizationId());
            params.put("now", nowUtc());
            if (q != null && !q.isBlank()) {
                where.append(" and s.name_key like :q escape '\\'");
                params.put("q", "%" + Modalities.normalize(q).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
            if (unitId != null) {
                where.append(" and ").append(inUnits(":unit"));
                params.put("unit", unitId);
            }
            if (min != null) {
                where.append(" and ").append(age).append(" >= :min");
                params.put("min", min);
                if (max != null) {
                    where.append(" and ").append(age).append(" <= :max");
                    params.put("max", max);
                }
            }
            where.append(reachPredicate(admin));
            if (admin) params.put("reach", reach);
            // R20: as contagens dos filtros rápidos valem só para o alcance e a unidade.
            Counts counts = counts("s.organization_id = :org" + (unitId != null ? " and " + inUnits(":unit") : "") + reachPredicate(admin), params);
            if (wanted != null) where.append(" and ").append(sealPredicate(wanted, age));
            // Modalidade e turma valem na mesma linha da matrícula (a turma é principal ou outro horário dela).
            if (f.modalityId() != null || f.classId() != null) {
                where.append(" and exists (select 1 from enrollment_modality m where m.organization_id = s.organization_id and m.student_id = s.id and m.status = 'ACTIVE'");
                if (f.modalityId() != null) {
                    where.append(" and m.modality_id = :modality");
                    params.put("modality", f.modalityId());
                }
                if (f.classId() != null) {
                    where.append(" and (m.primary_class_id = :class or exists (select 1 from enrollment_modality_class o where o.organization_id = m.organization_id "
                            + "and o.modality_row_id = m.id and o.class_id = :class))");
                    params.put("class", f.classId());
                }
                where.append(")");
            }
            // D13: o nível chega com a graduação (#105 e #106); até lá o filtro é aceito e ninguém tem nível.
            if (f.levelId() != null) where.append(" and false");
            if (f.financial() != null) where.append(" and ").append(OVERDUE);
            if (f.status() != null) {
                where.append(" and ").append(STATUS).append(" = :status");
                params.put("status", f.status());
            }

            long total = jdbc.sql("select count(*) from student s join unit u on u.id = s.registered_unit_id " + PAYER + " where " + where).params(params).query(Long.class).single();
            params.put("limit", page.size());
            params.put("offset", page.offset());
            List<Row> rows = rows(where.toString(), params, "order by s.name_key, s.name, s.id limit :limit offset :offset");
            return new StudentList(rows, page.page(), page.size(), total, counts);
        });
    }

    private void exists(String table, UUID org, UUID id) {
        jdbc.sql("select 1 from " + table + " where organization_id = :org and id = :id").param("org", org).param("id", id).query(Integer.class).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private Counts counts(String where, Map<String, Object> params) {
        return jdbc.sql("select count(*) filter (where " + STATUS + " = 'ACTIVE'), count(*) filter (where " + STATUS + " = 'FORMER'), count(*) filter (where " + OVERDUE + ") "
                + "from student s where " + where)
                .params(params).query((rs, n) -> new Counts(rs.getLong(1), rs.getLong(2), rs.getLong(3))).single();
    }

    /** RN-MAT-04, D7: a situação do aluno é calculada das modalidades (NONE, ACTIVE, FORMER). Alias {@code s}. */
    static final String STATUS = "(case when not exists (select 1 from enrollment e where e.organization_id = s.organization_id and e.student_id = s.id) "
            + "then 'NONE' when exists (select 1 from enrollment_modality m where m.organization_id = s.organization_id and m.student_id = s.id "
            + "and m.status = 'ACTIVE') then 'ACTIVE' else 'FORMER' end)";

    /** R10, D5: cobrança aberta em atraso de qualquer tipo, na data do fuso da unidade dela. Alias {@code s}. */
    private static final String OVERDUE = "exists (select 1 from charge c join unit cu on cu.organization_id = c.organization_id and cu.id = c.unit_id "
            + "where c.organization_id = s.organization_id and c.student_id = s.id and " + Charges.overdue("c", "cu") + ")";
    private static final String FINANCIAL = "(case when " + OVERDUE + " then 'OVERDUE' else 'CURRENT' end)";

    /** A data de hoje é a do fuso da unidade de cadastro (alias {@code u}), não a do UTC do servidor (RF-ALU-02, RF-ALU-06). */
    private static final String TODAY = "(cast(:now as timestamptz) at time zone u.timezone)::date";
    private static final String AGE = "extract(year from age(" + TODAY + ", s.birth_date))";

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC);
    }

    /** S8, R28: fuso da unidade de cadastro (UTC se a unidade não existe); chamar dentro da transação da organização. */
    java.time.ZoneId unitZone(UUID org, UUID unitId) {
        return jdbc.sql("select timezone from unit where organization_id = :org and id = :id").param("org", org).param("id", unitId)
                .query(String.class).optional().map(java.time.ZoneId::of).orElse(java.time.ZoneOffset.UTC);
    }

    java.time.ZoneId studentZone(UUID org, UUID studentId) {
        return jdbc.sql("select u.timezone from student s join unit u on u.id = s.registered_unit_id and u.organization_id = s.organization_id "
                + "where s.organization_id = :org and s.id = :id").param("org", org).param("id", studentId)
                .query(String.class).optional().map(java.time.ZoneId::of).orElse(java.time.ZoneOffset.UTC);
    }

    private LocalDate today(String zone) {
        return LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of(zone));
    }

    /** O select da linha (lista e ficha); {@code params} já traz org e now. Faixas podem se sobrepor: vale a de maior idade mínima (D6). */
    private List<Row> rows(String where, Map<String, Object> params, String tail) {
        String age = AGE;
        List<Row> found = jdbc.sql("select s.id, s.name, s.photo, s.birth_date, " + age + ", r.id, r.name, u.id, u.name, s.created_at, "
                + sealPredicate(StudentSeal.INCOMPLETE_REGISTRATION, age) + ", " + sealPredicate(StudentSeal.EMAIL_PROBLEM, age) + ", "
                + sealPredicate(StudentSeal.BIRTHDAY_TODAY, age) + ", " + sealPredicate(StudentSeal.NEW, age) + ", " + sealPredicate(StudentSeal.SCHOLARSHIP, age) + ", u.timezone, " + STATUS + ", " + FINANCIAL + " from student s join unit u on u.id = s.registered_unit_id " + PAYER + " "
                + "left join lateral (select a.id, a.name from age_range a where a.organization_id = s.organization_id and a.min_age <= " + age
                + " and (a.max_age is null or a.max_age >= " + age + ") order by a.min_age desc, a.name_key limit 1) r on true "
                + "where " + where + " " + tail)
                .params(params)
                .query((rs, n) -> {
                    LocalDate birth = rs.getObject(4, LocalDate.class);
                    UUID rangeId = rs.getObject(6, UUID.class);
                    return new Row(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)), birth, rs.getInt(5),
                            !InviteRules.adult(birth, today(rs.getString(16))), rangeId == null ? null : new RangeRef(rangeId, rs.getString(7)),
                            new UnitRef(rs.getObject(8, UUID.class), rs.getString(9)), rs.getObject(10, OffsetDateTime.class), seals(rs), rs.getString(17), rs.getString(18),
                            List.of());
                }).list();
        if (found.isEmpty()) return found;
        Map<UUID, List<RowModality>> byStudent = new HashMap<>();
        jdbc.sql("select m.student_id, m.id, u.id, u.name, md.id, md.name, c.id, c.name from enrollment_modality m "
                + "join unit u on u.organization_id = m.organization_id and u.id = m.unit_id join modality md on md.id = m.modality_id "
                + "join training_class c on c.organization_id = m.organization_id and c.id = m.primary_class_id "
                + "where m.organization_id = :org and m.status = 'ACTIVE' and m.student_id in (:ids) order by md.name, m.started_at, m.id")
                .param("org", params.get("org")).param("ids", found.stream().map(Row::id).toList())
                .query((rs, n) -> {
                    byStudent.computeIfAbsent(rs.getObject(1, UUID.class), k -> new java.util.ArrayList<>()).add(new RowModality(rs.getObject(2, UUID.class),
                            new UnitRef(rs.getObject(3, UUID.class), rs.getString(4)), new Enrollments.Named(rs.getObject(5, UUID.class), rs.getString(6)),
                            new Enrollments.Named(rs.getObject(7, UUID.class), rs.getString(8)), null));
                    return 0;
                }).list();
        return found.stream().map(r -> r.withModalities(byStudent.getOrDefault(r.id(), List.of()))).toList();
    }

    /**
     * RF-ALU-06, dentro da transação da organização: quem faz aniversário hoje (mesmo predicado do selo) e tem modalidade ativa (R17). Administrador: unidades do alcance;
     * Professor: quem chama filtra por {@code reachable} (turmas dele). {@code contacts} liga o telefone.
     */
    List<Birthday> birthdays(AccountPrincipal who, boolean contacts, java.util.function.Predicate<UUID> reachable) {
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) return List.of();
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        params.put("now", nowUtc());
        if (admin) params.put("reach", reach);
        String age = AGE;
        return jdbc.sql("select s.id, s.name, s.photo, " + age + ", r.id, r.name, u.id, u.name, c.name, c.phone, c.guardian "
                + "from student s join unit u on u.id = s.registered_unit_id "
                + "left join lateral (select a.id, a.name from age_range a where a.organization_id = s.organization_id and a.min_age <= " + age
                + " and (a.max_age is null or a.max_age >= " + age + ") order by a.min_age desc, a.name_key limit 1) r on true "
                + "left join lateral (select x.name, x.phone, x.guardian from (select s.name, s.phone, false as guardian, 0 as rank "
                + "union all select g.name, g.phone, true, case when sg.financial then 1 else 2 end from student_guardian sg "
                + "join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id "
                + "where sg.organization_id = s.organization_id and sg.student_id = s.id) x where coalesce(trim(x.phone), '') <> '' "
                + "order by x.rank, x.name limit 1) c on true "
                + "where s.organization_id = :org and " + sealPredicate(StudentSeal.BIRTHDAY_TODAY, age) + reachPredicate(admin)
                + " and exists (select 1 from enrollment_modality m where m.organization_id = s.organization_id and m.student_id = s.id and m.status = 'ACTIVE'"
                + (admin ? " and m.unit_id in (:reach)" : "") + ")"
                + " order by s.name_key, s.name, s.id")
                .params(params)
                .query((rs, n) -> {
                    UUID rangeId = rs.getObject(5, UUID.class);
                    String phone = contacts ? rs.getString(10) : null;
                    return new Birthday(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)), rs.getInt(4),
                            rangeId == null ? null : new RangeRef(rangeId, rs.getString(6)), new UnitRef(rs.getObject(7, UUID.class), rs.getString(8)),
                            phone == null ? null : rs.getString(9), phone, phone != null && rs.getBoolean(11));
                }).list().stream().filter(b -> reachable == null || reachable.test(b.id())).toList();
    }

    /** Roda dentro da transação da organização: a linha da lista de um aluno (id sem alcance; quem chama já conferiu). */
    Optional<Row> row(UUID organizationId, UUID id) {
        Map<String, Object> params = new HashMap<>(Map.of("org", organizationId, "id", id, "now", nowUtc()));
        return rows("s.organization_id = :org and s.id = :id", params, "").stream().findFirst();
    }

    /** Quem paga (RN-ALU-09): o aluno adulto, ou o responsável financeiro do menor (sem ele, tudo nulo). */
    private static final String PAYER = "left join lateral (select g.cpf, a.email, g.email_problem_at from student_guardian sg "
            + "join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id join account a on a.id = g.account_id "
            + "where sg.organization_id = s.organization_id and sg.student_id = s.id and sg.financial limit 1) p on true";

    /** Um ponto só para o select e o filtro, para as duas leituras nunca divergirem. */
    private static String sealPredicate(StudentSeal seal, String age) {
        String adult = "(" + age + " >= 18)";
        return switch (seal) {
            case INCOMPLETE_REGISTRATION -> "(case when " + adult + " then s.cpf is null or coalesce(trim(s.email), '') = '' "
                    + "else p.cpf is null or coalesce(trim(p.email), '') = '' end)";
            case EMAIL_PROBLEM -> "(case when " + adult + " then s.email_problem_at is not null else coalesce(p.email_problem_at is not null, false) end)";
            case BIRTHDAY_TODAY -> "(to_char(s.birth_date, 'MMDD') = to_char(" + TODAY + ", 'MMDD') or (to_char(" + TODAY + ", 'MMDD') = '0228' "
                    + "and to_char(s.birth_date, 'MMDD') = '0229' and extract(day from (date_trunc('year', " + TODAY + ") + interval '2 months' - interval '1 day')) = 28))";
            // RF-ALU-02, D8: até 30 dias (dia 30 incluso) da matrícula mais recente, contados no fuso da unidade de cadastro.
            case NEW -> "exists (select 1 from enrollment e where e.organization_id = s.organization_id and e.student_id = s.id "
                    + "and (greatest(e.enrolled_at, coalesce(e.reopened_at, e.enrolled_at)) at time zone u.timezone)::date >= " + TODAY + " - 30)";
            // RF-BEN-02, D10: bolsa (percentual) ativa e dentro da validade, numa modalidade ativa; desconto em reais não conta.
            case SCHOLARSHIP -> "exists (select 1 from benefit b join enrollment_modality em on em.organization_id = b.organization_id and em.id = b.enrollment_modality_id "
                    + "where b.organization_id = s.organization_id and b.student_id = s.id and b.type = 'PERCENT' and b.status = 'ACTIVE' and em.status = 'ACTIVE' "
                    + "and (b.valid_until is null or b.valid_until >= " + TODAY + "))";
        };
    }

    private static List<StudentSeal> seals(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<StudentSeal> out = new java.util.ArrayList<>();
        for (StudentSeal seal : StudentSeal.values()) if (rs.getBoolean(11 + seal.ordinal())) out.add(seal);
        return out;
    }

    /** Alcance do Administrador (RN-ALU-07): modalidade numa unidade do alcance, ver {@link #inUnits}. */
    private static String reachPredicate(boolean admin) {
        return admin ? " and " + inUnits(":reach") : "";
    }

    /** RN-ALU-07 (D11): o aluno tem modalidade (ativa ou encerrada) numa das unidades; sem nenhuma modalidade, vale a unidade de cadastro. Alias {@code s}. */
    private static String inUnits(String units) {
        String mine = "from enrollment_modality m where m.organization_id = s.organization_id and m.student_id = s.id";
        return "(exists (select 1 " + mine + " and m.unit_id in (" + units + ")) or (not exists (select 1 " + mine + ") and s.registered_unit_id in (" + units + ")))";
    }

    public Detail get(AccountPrincipal who, UUID id) {
        requireAccess(who);
        return transactions.execute(() -> detail(who, id));
    }

    /** RN-ALU-06 e R8: só id, nome, foto e situação da matrícula do aluno da organização com esse CPF ou e-mail (um dos dois), de qualquer unidade. */
    public Lookup lookup(AccountPrincipal who, String rawCpf, String rawEmail) {
        requireAccess(who);
        boolean hasCpf = rawCpf != null && !rawCpf.isBlank(), hasEmail = rawEmail != null && !rawEmail.isBlank();
        if (hasCpf == hasEmail) throw new ApiValidationException(hasCpf ? "email" : "cpf", "informe o CPF ou o e-mail, só um dos dois");
        String cpf = hasCpf ? StudentRegistration.cpf("cpf", rawCpf) : null;
        String email = hasEmail ? br.com.ctcore.organization.NewUnit.email("email", rawEmail.strip()) : null;
        return transactions.execute(() -> jdbc.sql("select s.id, s.name, s.photo, " + STATUS + " from student s where s.organization_id = :org and "
                + (cpf != null ? "s.cpf = :v" : "lower(s.email) = lower(:v)") + " order by s.created_at, s.id limit 1")
                .param("org", who.organizationId()).param("v", cpf != null ? cpf : email)
                .query((rs, n) -> new Lookup(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)), rs.getString(4))).optional())
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    /** Roda dentro da transação da organização. */
    Optional<Ref> byCpf(UUID organizationId, String cpf) {
        return jdbc.sql("select id, name, photo from student where organization_id = :org and cpf = :cpf").param("org", organizationId)
                .param("cpf", cpf).query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)))).optional();
    }

    /** Roda dentro da transação da organização. */
    Optional<Ref> byNameAndBirth(UUID organizationId, String nameKey, LocalDate birthDate, UUID except) {
        return jdbc.sql("select id, name, photo from student where organization_id = :org and name_key = :key and birth_date = :birth "
                + "and id <> :except order by created_at, id limit 1").param("org", organizationId)
                .param("key", nameKey).param("birth", birthDate).param("except", except == null ? new UUID(0, 0) : except)
                .query((rs, n) -> new Ref(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)))).optional();
    }

    /** Roda dentro da transação da organização; fora do alcance de quem chama, 404. */
    Detail detail(AccountPrincipal who, UUID id) {
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
        var query = jdbc.sql("select id, registered_unit_id, name, birth_date, postal_code, street, number, complement, district, city, state, cpf, email, "
                + "phone, gender, referral, emergency_name, emergency_relationship, emergency_phone, photo, email_problem_at is not null, "
                + "(select u.timezone from unit u where u.id = registered_unit_id) from student s "
                + "where s.organization_id = :org and s.id = :id" + (admin ? " and " + inUnits(":reach") : ""))
                .param("org", who.organizationId()).param("id", id);
        if (admin) query = query.param("reach", reach);
        return query.query((rs, n) -> new Detail(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getObject(4, LocalDate.class), !InviteRules.adult(rs.getObject(4, LocalDate.class), today(rs.getString(22))), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13),
                rs.getString(14), rs.getString(15), rs.getString(16), rs.getString(17), rs.getString(18), rs.getString(19), photoUrl(rs.getString(20)),
                rs.getBoolean(21), guardians(who.organizationId(), id))).optional()
                .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    private List<GuardianRow> guardians(UUID organizationId, UUID studentId) {
        return jdbc.sql("select g.id, g.name, a.email, g.phone, sg.financial, coalesce(l.status, 'PENDING'), g.email_problem_at is not null "
                + "from student_guardian sg join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id "
                + "join account a on a.id = g.account_id left join account_link l on l.account_id = g.account_id and l.organization_id = g.organization_id "
                + "and l.role = 'GUARDIAN' where sg.organization_id = :org and sg.student_id = :id order by sg.financial desc, g.name, g.id")
                .param("org", organizationId).param("id", studentId)
                .query((rs, n) -> new GuardianRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5),
                        "ACTIVE".equals(rs.getString(6)) ? "ACTIVE" : "PENDING", rs.getBoolean(7))).list();
    }

    public String photoUrl(String photo) {
        if (photo == null) return null;
        try {
            return storage.readUrl(photo);
        } catch (FileStorage.NotFound e) {
            LOG.warn("Student photo file is missing from storage");
            return null;
        }
    }
}
