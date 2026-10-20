package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
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
 * RF-ALU-01: leitura do aluno para a gestão (detalhe e busca por CPF). Organização, perfil e alcance vêm da sessão. Até a matrícula
 * (#63) o alcance do Administrador é a unidade de cadastro do aluno (D2); fora dele, é o mesmo 404 de quem não existe.
 */
@Service
public class Students {
    private static final Logger LOG = LoggerFactory.getLogger(Students.class);

    /** O que a recusa por duplicidade e a busca por CPF mostram: só nome e foto (RN-ALU-06). */
    public record Ref(UUID id, String name, String photoUrl) {}

    public record GuardianRow(UUID guardianId, String name, String email, String phone, boolean financial, String access, boolean emailProblem) {}

    public record Detail(UUID id, UUID registeredUnitId, String name, LocalDate birthDate, boolean minor, String postalCode, String street,
            String number, String complement, String district, String city, String state, String cpf, String email, String phone,
            String gender, String referral, String emergencyName, String emergencyRelationship, String emergencyPhone, String photoUrl,
            boolean emailProblem, List<GuardianRow> guardians) {}

    public record UnitRef(UUID id, String name) {}

    public record RangeRef(UUID id, String name) {}

    /** RF-ALU-01: linha da lista, sem CPF, e-mail, telefone, endereço nem emergência (minimização). */
    public record Row(UUID id, String name, String photoUrl, LocalDate birthDate, int age, boolean minor, RangeRef ageRange, UnitRef registeredUnit,
            OffsetDateTime createdAt, List<StudentSeal> seals) {}

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
    public PageResponse<Row> list(AccountPrincipal who, String q, UUID unitId, UUID ageRangeId, String seal, PageRequest page) {
        requireAccess(who);
        StudentSeal wanted = seal == null ? null : StudentSeal.parse(seal).orElseThrow(() -> new ApiValidationException("seal", "inválido"));
        return transactions.execute(() -> {
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
            if (admin && (reach.isEmpty() || unitId != null && !reach.contains(unitId))) return PageResponse.of(List.<Row>of(), page, 0);

            LocalDate today = LocalDate.now(clock);
            String age = AGE;
            StringBuilder where = new StringBuilder("s.organization_id = :org");
            Map<String, Object> params = new HashMap<>();
            params.put("org", who.organizationId());
            params.put("today", today);
            if (q != null && !q.isBlank()) {
                where.append(" and s.name_key like :q escape '\\'");
                params.put("q", "%" + Modalities.normalize(q).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
            }
            if (unitId != null) {
                where.append(" and s.registered_unit_id = :unit");
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
            if (wanted != null) where.append(" and ").append(sealPredicate(wanted, age));
            sealParams(params, today);

            long total = jdbc.sql("select count(*) from student s " + PAYER + " where " + where).params(params).query(Long.class).single();
            params.put("limit", page.size());
            params.put("offset", page.offset());
            List<Row> rows = rows(where.toString(), params, today, "order by s.name_key, s.name, s.id limit :limit offset :offset");
            return PageResponse.of(rows, page, total);
        });
    }

    private static final String AGE = "extract(year from age(cast(:today as date), s.birth_date))";

    /** O select da linha (lista e ficha); {@code params} já traz org, today, md e feb28. Faixas podem se sobrepor: vale a de maior idade mínima (D6). */
    private List<Row> rows(String where, Map<String, Object> params, LocalDate today, String tail) {
        String age = AGE;
        return jdbc.sql("select s.id, s.name, s.photo, s.birth_date, " + age + ", r.id, r.name, u.id, u.name, s.created_at, "
                + sealPredicate(StudentSeal.INCOMPLETE_REGISTRATION, age) + ", " + sealPredicate(StudentSeal.EMAIL_PROBLEM, age) + ", "
                + sealPredicate(StudentSeal.BIRTHDAY_TODAY, age) + " from student s join unit u on u.id = s.registered_unit_id " + PAYER + " "
                + "left join lateral (select a.id, a.name from age_range a where a.organization_id = s.organization_id and a.min_age <= " + age
                + " and (a.max_age is null or a.max_age >= " + age + ") order by a.min_age desc, a.name_key limit 1) r on true "
                + "where " + where + " " + tail)
                .params(params)
                .query((rs, n) -> {
                    LocalDate birth = rs.getObject(4, LocalDate.class);
                    UUID rangeId = rs.getObject(6, UUID.class);
                    return new Row(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)), birth, rs.getInt(5),
                            !InviteRules.adult(birth, today), rangeId == null ? null : new RangeRef(rangeId, rs.getString(7)),
                            new UnitRef(rs.getObject(8, UUID.class), rs.getString(9)), rs.getObject(10, OffsetDateTime.class), seals(rs));
                }).list();
    }

    /**
     * RF-ALU-06, dentro da transação da organização: quem faz aniversário hoje (mesmo predicado do selo). Administrador: unidades do alcance;
     * Professor: quem chama filtra por {@code reachable} (turmas dele). {@code contacts} liga o telefone.
     */
    List<Birthday> birthdays(AccountPrincipal who, boolean contacts, java.util.function.Predicate<UUID> reachable) {
        boolean admin = who.role() == Role.ADMINISTRATOR;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        if (admin && reach.isEmpty()) return List.of();
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> params = new HashMap<>();
        params.put("org", who.organizationId());
        params.put("today", today);
        sealParams(params, today);
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
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> params = new HashMap<>(Map.of("org", organizationId, "id", id, "today", today));
        sealParams(params, today);
        return rows("s.organization_id = :org and s.id = :id", params, today, "").stream().findFirst();
    }

    private static void sealParams(Map<String, Object> params, LocalDate today) {
        // 29/02 comemora em 28/02 nos anos não bissextos (D4).
        params.put("md", "%02d%02d".formatted(today.getMonthValue(), today.getDayOfMonth()));
        params.put("feb28", today.getMonthValue() == 2 && today.getDayOfMonth() == 28 && !today.isLeapYear());
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
            case BIRTHDAY_TODAY -> "(to_char(s.birth_date, 'MMDD') = :md or (:feb28 and to_char(s.birth_date, 'MMDD') = '0229'))";
        };
    }

    private static List<StudentSeal> seals(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<StudentSeal> out = new java.util.ArrayList<>();
        for (StudentSeal seal : StudentSeal.values()) if (rs.getBoolean(11 + seal.ordinal())) out.add(seal);
        return out;
    }

    /** Alcance do Administrador: hoje a unidade de cadastro; com a matrícula (#63) passa a ser a modalidade na unidade (RN-ALU-07). */
    private static String reachPredicate(boolean admin) {
        return admin ? " and s.registered_unit_id in (:reach)" : "";
    }

    public Detail get(AccountPrincipal who, UUID id) {
        requireAccess(who);
        return transactions.execute(() -> detail(who, id));
    }

    /** RN-ALU-06: só id, nome e foto do aluno da organização com esse CPF, de qualquer unidade. */
    public Ref lookup(AccountPrincipal who, String rawCpf) {
        requireAccess(who);
        String cpf = StudentRegistration.cpf("cpf", rawCpf);
        if (cpf == null) throw new ApiValidationException("cpf", "obrigatório");
        return transactions.execute(() -> byCpf(who.organizationId(), cpf)).orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
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
                + "phone, gender, referral, emergency_name, emergency_relationship, emergency_phone, photo, email_problem_at is not null from student "
                + "where organization_id = :org and id = :id" + (admin ? " and registered_unit_id in (:reach)" : ""))
                .param("org", who.organizationId()).param("id", id);
        if (admin) query = query.param("reach", reach);
        LocalDate today = LocalDate.now(clock);
        return query.query((rs, n) -> new Detail(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getObject(4, LocalDate.class), !InviteRules.adult(rs.getObject(4, LocalDate.class), today), rs.getString(5), rs.getString(6),
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

    String photoUrl(String photo) {
        if (photo == null) return null;
        try {
            return storage.readUrl(photo);
        } catch (FileStorage.NotFound e) {
            LOG.warn("Student photo file is missing from storage");
            return null;
        }
    }
}
