package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.api.page.PageResponse;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Access;
import br.com.ctcore.permission.Permission;
import br.com.ctcore.permission.Permissions;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-ALU-03: ficha do aluno e histórico. Gestão (STUDENT_MANAGE) com o alcance de {@link Students#detail}; Professor só com a chave
 * "ver ficha do aluno" e só para aluno das suas turmas (D4). A omissão de campos do Professor é montada aqui, no servidor (R4).
 */
@Service
public class StudentRecords {
    public record Contact(String phone, String email, boolean emailProblem) {}

    public record Address(String postalCode, String street, String number, String complement, String district, String city, String state) {}

    public record Emergency(String name, String relationship, String phone) {}

    /** Telefone, e-mail, problema de e-mail, papel financeiro e acesso saem do JSON quando nulos (ficha do Professor). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Guardian(UUID guardianId, String name, String email, String phone, Boolean financial, String access, Boolean emailProblem) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecordView(UUID id, String name, String photoUrl, LocalDate birthDate, int age, boolean minor, Students.UnitRef registeredUnit,
            Students.RangeRef ageRange, List<StudentSeal> seals, Contact contact, String cpf, Address address, Emergency emergency,
            List<Guardian> guardians) {}

    public record HistoryEntry(String action, OffsetDateTime at, String actor, String actorProfile, String reason) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Permissions permissions;
    private final Students students;

    public StudentRecords(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Permissions permissions, Students students) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.permissions = permissions;
        this.students = students;
    }

    public RecordView record(AccountPrincipal who, UUID id) {
        if (who == null || who.role() == null) throw new AccessDeniedException("Permission denied");
        boolean teacher = who.role() == Role.TEACHER;
        var effective = permissions.effective(who);
        if (teacher ? effective.get(Permission.STUDENT_RECORD) == Access.NONE : effective.get(Permission.STUDENT_MANAGE) == Access.NONE) {
            throw new AccessDeniedException("Permission denied");
        }
        boolean contacts = !teacher || effective.get(Permission.STUDENT_CONTACTS) != Access.NONE;
        return transactions.execute(() -> {
            if (teacher && !teacherReach(who, id)) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            // Administrador fora do alcance: o mesmo 404 de aluno inexistente (RN-ALU-07).
            var detail = students.detail(who, id);
            var row = students.row(who.organizationId(), id).orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            return assemble(row, detail, teacher, contacts);
        });
    }

    /** RF-ALU-06: aniversariantes de hoje para o Início do Administrador e do Professor; só leitura, sem auditoria. */
    public List<Students.Birthday> birthdays(AccountPrincipal who) {
        if (who == null || who.role() == null) throw new AccessDeniedException("Permission denied");
        boolean teacher = who.role() == Role.TEACHER;
        if (!teacher && who.role() != Role.ADMINISTRATOR) throw new AccessDeniedException("Permission denied");
        var effective = permissions.effective(who);
        if (effective.get(teacher ? Permission.STUDENT_VIEW : Permission.STUDENT_MANAGE) == Access.NONE) throw new AccessDeniedException("Permission denied");
        boolean contacts = !teacher || effective.get(Permission.STUDENT_CONTACTS) != Access.NONE;
        return transactions.execute(() -> students.birthdays(who, contacts, teacher ? id -> teacherReach(who, id) : null));
    }

    /** O único ponto que a matrícula (#63) troca: hoje não há "aluno das suas turmas", então nenhum aluno é alcançável pelo Professor. */
    boolean teacherReach(AccountPrincipal who, UUID studentId) {
        return false;
    }

    /** R4: o corte de campos do Professor; {@code contacts} liga telefone, e-mail e emergência (ver contatos). */
    static RecordView assemble(Students.Row row, Students.Detail d, boolean teacher, boolean contacts) {
        List<Guardian> guardians = d.guardians().stream().map(g -> new Guardian(g.guardianId(), g.name(), contacts ? g.email() : null,
                contacts ? g.phone() : null, teacher ? null : g.financial(), teacher ? null : g.access(), contacts ? g.emailProblem() : null)).toList();
        return new RecordView(row.id(), row.name(), row.photoUrl(), teacher ? null : row.birthDate(), row.age(), row.minor(), row.registeredUnit(),
                row.ageRange(), teacher ? null : row.seals(), contacts ? new Contact(d.phone(), d.email(), d.emailProblem()) : null,
                teacher ? null : d.cpf(),
                teacher ? null : new Address(d.postalCode(), d.street(), d.number(), d.complement(), d.district(), d.city(), d.state()),
                contacts ? new Emergency(d.emergencyName(), d.emergencyRelationship(), d.emergencyPhone()) : null, guardians);
    }

    /** R5: ações auditadas do aluno, da mais recente para a mais antiga, sem os valores antes e depois. */
    public PageResponse<HistoryEntry> history(AccountPrincipal who, UUID id, PageRequest page) {
        students.requireAccess(who);
        return transactions.execute(() -> {
            students.detail(who, id);
            String from = " from audit_log a left join account c on c.id = a.actor_id where a.organization_id = :org "
                    + "and a.entity_type = 'student' and a.entity_id = :id";
            long total = jdbc.sql("select count(*)" + from).param("org", who.organizationId()).param("id", id.toString()).query(Long.class).single();
            var items = jdbc.sql("select a.action, a.occurred_at, coalesce(nullif(btrim(c.name), ''), c.email), a.actor_profile, a.reason" + from
                    + " order by a.occurred_at desc, a.id desc limit :limit offset :offset")
                    .param("org", who.organizationId()).param("id", id.toString()).param("limit", page.size()).param("offset", page.offset())
                    .query((rs, n) -> new HistoryEntry(rs.getString(1), rs.getObject(2, OffsetDateTime.class), rs.getString(3), rs.getString(4),
                            rs.getString(5))).list();
            return PageResponse.of(items, page, total);
        });
    }
}
