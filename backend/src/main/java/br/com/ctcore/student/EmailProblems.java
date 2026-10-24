package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.notification.NotificationType;
import br.com.ctcore.notification.Notifications;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-ALU-10: e-mail de aluno ou de responsável "com problema". Aqui só o estado e o bloqueio dos envios (aviso dos 18 anos e e-mail
 * neutro de desligamento); a devolução real (bounce) chega com o e-mail real, pela porta (Sprint 22). {@link #clear} é de #61.
 */
@Service
public class EmailProblems {
    private static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);

    private record Found(String kind, UUID id, UUID unitId) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final ScopedAudit audit;
    private final Clock clock;
    private final Notifications notifications;

    public EmailProblems(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, ScopedAudit audit, Clock clock,
            Notifications notifications) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
        this.notifications = notifications;
    }

    /** Devolve quantos cadastros (alunos e responsáveis) mudaram de estado. */
    public int mark(UUID organizationId, String email) {
        return set(organizationId, email, true);
    }

    public int clear(UUID organizationId, String email) {
        return set(organizationId, email, false);
    }

    private int set(UUID organizationId, String email, boolean problem) {
        if (email == null || email.isBlank()) return 0;
        List<Found> hits = new java.util.ArrayList<>();
        int changed = transactions.provision(organizationId, () -> {
            OffsetDateTime now = problem ? OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC) : null;
            String action = problem ? "student.email_problem_marked" : "student.email_problem_cleared";
            String from = problem ? "email_problem_at is null" : "email_problem_at is not null";
            List<Found> students = jdbc.sql("update student set email_problem_at = :now where organization_id = :org and lower(email) = lower(:e) and "
                    + from + " returning id, registered_unit_id").param("now", now, java.sql.Types.TIMESTAMP_WITH_TIMEZONE).param("org", organizationId)
                    .param("e", email.strip()).query((rs, n) -> found("student", rs)).list();
            List<Found> guardians = jdbc.sql("update guardian set email_problem_at = :now where organization_id = :org and " + from
                    + " and account_id in (select id from account where lower(email) = lower(:e)) returning id, (select s.registered_unit_id from student_guardian sg "
                    + "join student s on s.organization_id = sg.organization_id and s.id = sg.student_id where sg.organization_id = guardian.organization_id "
                    + "and sg.guardian_id = guardian.id order by s.id limit 1)")
                    .param("now", now, java.sql.Types.TIMESTAMP_WITH_TIMEZONE).param("org", organizationId).param("e", email.strip())
                    .query((rs, n) -> found("guardian", rs)).list();
            for (Found h : students) {
                String id = h.id().toString();
                audit.record(organizationId, new AuditEntry(SYSTEM, action, "student", id, null, null, Map.of("emailProblem", problem), null, null));
            }
            for (Found h : guardians) {
                String id = h.id().toString();
                audit.record(organizationId, new AuditEntry(SYSTEM, action, "guardian", id, null, null, Map.of("emailProblem", problem), null, null));
            }
            hits.addAll(students);
            hits.addAll(guardians);
            return students.size() + guardians.size();
        });
        // RN-NOT-04: depois do commit; a notificação nunca desfaz a marca.
        if (problem) {
            for (Found h : hits) {
                if (h.unitId() != null) {
                    notifications.publish(organizationId, h.unitId(), NotificationType.EMAIL_PROBLEM, h.kind(), h.id(), Map.of());
                }
            }
        }
        return changed;
    }

    private static Found found(String kind, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Found(kind, rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
    }
}
