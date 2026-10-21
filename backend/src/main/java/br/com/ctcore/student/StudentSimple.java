package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * R22 (02): a lista simples de uma turma ou aula. O Professor vê a das turmas dele (titular ou auxiliar) e a das aulas dele, sem depender das
 * chaves do Professor; a gestão, a das unidades do alcance. Só nome, foto, faixa e bloqueio: sem CPF, contato, valores nem selos.
 */
@Service
public class StudentSimple {
    /** {@code belt} é nulo até a graduação (#105 e #106) e {@code blocked} é sempre falso até a inativação (#109), D13 e D14. */
    public record Item(UUID id, String name, String photoUrl, String belt, boolean blocked) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;

    public StudentSimple(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
    }

    public List<Item> list(AccountPrincipal who, UUID classId, UUID lessonId) {
        if (who == null || who.role() == null) throw new AccessDeniedException("Permission denied");
        boolean teacher = who.role() == Role.TEACHER;
        if (!teacher) students.requireAccess(who);
        if ((classId == null) == (lessonId == null)) throw new ApiValidationException(classId == null ? "classId" : "lessonId", "informe a turma ou a aula, só uma");
        UUID org = who.organizationId();
        return transactions.execute(() -> {
            UUID me = teacher ? jdbc.sql("select id from teacher where organization_id = :org and account_id = :account").param("org", org)
                    .param("account", who.accountId()).query(UUID.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND)) : null;
            UUID target = classId;
            UUID lessonTeacher = null;
            if (lessonId != null) {
                var lesson = jdbc.sql("select class_id, teacher_id from lesson where organization_id = :org and id = :id").param("org", org).param("id", lessonId)
                        .query((rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}).optional()
                        .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
                target = lesson[0];
                lessonTeacher = lesson[1];
            }
            // Aula sem turma não tem alunos; ainda assim o Professor só a vê se for dele (e a gestão só no alcance).
            UUID unit = target == null ? jdbc.sql("select unit_id from lesson where organization_id = :org and id = :id").param("org", org).param("id", lessonId)
                    .query(UUID.class).single() : jdbc.sql("select unit_id from training_class where organization_id = :org and id = :id and status = 'ACTIVE'")
                    .param("org", org).param("id", target).query(UUID.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (teacher) {
                boolean inClass = target != null && jdbc.sql("select count(*) from training_class c where c.organization_id = :org and c.id = :id "
                        + "and (c.head_teacher_id = :me or exists (select 1 from training_class_assistant a where a.organization_id = c.organization_id "
                        + "and a.class_id = c.id and a.teacher_id = :me))").param("org", org).param("id", target).param("me", me).query(Integer.class).single() > 0;
                if (!inClass && !me.equals(lessonTeacher)) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            } else if (who.role() == Role.ADMINISTRATOR) {
                Set<UUID> reach = who.units() == null ? Set.of() : who.units();
                if (!reach.contains(unit)) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            }
            if (target == null) return List.of();
            return jdbc.sql("select distinct s.id, s.name, s.name_key, s.photo from enrollment_modality m join student s on s.organization_id = m.organization_id "
                    + "and s.id = m.student_id where m.organization_id = :org and m.status = 'ACTIVE' and (m.primary_class_id = :class or exists "
                    + "(select 1 from enrollment_modality_class o where o.organization_id = m.organization_id and o.modality_row_id = m.id and o.class_id = :class)) "
                    + "order by s.name_key, s.name, s.id").param("org", org).param("class", target)
                    .query((rs, n) -> new Item(rs.getObject(1, UUID.class), rs.getString(2), students.photoUrl(rs.getString(4)), null, false)).list();
        });
    }
}
