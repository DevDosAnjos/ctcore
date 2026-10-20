package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import br.com.ctcore.storage.FileStorage;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
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
