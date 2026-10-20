package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
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

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final ScopedAudit audit;
    private final Clock clock;

    public EmailProblems(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, ScopedAudit audit, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.audit = audit;
        this.clock = clock;
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
        return transactions.provision(organizationId, () -> {
            OffsetDateTime now = problem ? OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC) : null;
            String action = problem ? "student.email_problem_marked" : "student.email_problem_cleared";
            String from = problem ? "email_problem_at is null" : "email_problem_at is not null";
            List<UUID> students = jdbc.sql("update student set email_problem_at = :now where organization_id = :org and lower(email) = lower(:e) and "
                    + from + " returning id").param("now", now, java.sql.Types.TIMESTAMP_WITH_TIMEZONE).param("org", organizationId)
                    .param("e", email.strip()).query(UUID.class).list();
            List<UUID> guardians = jdbc.sql("update guardian set email_problem_at = :now where organization_id = :org and " + from
                    + " and account_id in (select id from account where lower(email) = lower(:e)) returning id")
                    .param("now", now, java.sql.Types.TIMESTAMP_WITH_TIMEZONE).param("org", organizationId).param("e", email.strip())
                    .query(UUID.class).list();
            for (UUID id : students) {
                audit.record(organizationId, new AuditEntry(SYSTEM, action, "student", id.toString(), null, null, Map.of("emailProblem", problem), null, null));
            }
            for (UUID id : guardians) {
                audit.record(organizationId, new AuditEntry(SYSTEM, action, "guardian", id.toString(), null, null, Map.of("emailProblem", problem), null, null));
            }
            return students.size() + guardians.size();
        });
    }
}
