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
            OffsetDateTime createdAt) {}

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
    public PageResponse<Row> list(AccountPrincipal who, String q, UUID unitId, UUID ageRangeId, PageRequest page) {
        requireAccess(who);
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
            String age = "extract(year from age(cast(:today as date), s.birth_date))";
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

            long total = jdbc.sql("select count(*) from student s where " + where).params(params).query(Long.class).single();
            // Faixas podem se sobrepor: vale a de maior idade mínima (D6).
            List<Row> rows = jdbc.sql("select s.id, s.name, s.photo, s.birth_date, " + age + ", r.id, r.name, u.id, u.name, s.created_at "
                    + "from student s join unit u on u.id = s.registered_unit_id "
                    + "left join lateral (select a.id, a.name from age_range a where a.organization_id = s.organization_id and a.min_age <= " + age
                    + " and (a.max_age is null or a.max_age >= " + age + ") order by a.min_age desc, a.name_key limit 1) r on true "
                    + "where " + where + " order by s.name_key, s.name, s.id limit :limit offset :offset")
                    .params(params).param("limit", page.size()).param("offset", page.offset())
                    .query((rs, n) -> {
                        LocalDate birth = rs.getObject(4, LocalDate.class);
                        UUID rangeId = rs.getObject(6, UUID.class);
                        return new Row(rs.getObject(1, UUID.class), rs.getString(2), photoUrl(rs.getString(3)), birth, rs.getInt(5),
                                !InviteRules.adult(birth, today), rangeId == null ? null : new RangeRef(rangeId, rs.getString(7)),
                                new UnitRef(rs.getObject(8, UUID.class), rs.getString(9)), rs.getObject(10, OffsetDateTime.class));
                    }).list();
            return PageResponse.of(rows, page, total);
        });
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
    Optional<Ref> byNameAndBirth(UUID organizationId, String nameKey, LocalDate birthDate) {
        return jdbc.sql("select id, name, photo from student where organization_id = :org and name_key = :key and birth_date = :birth "
                + "order by created_at, id limit 1").param("org", organizationId).param("key", nameKey).param("birth", birthDate)
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
