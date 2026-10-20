package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-RSP-07, RN-ACE-06: tarefa diária que avisa quando o aluno cadastrado menor completa 18 anos. Marca {@code majority_notice_at} (uma só vez;
 * é o que o sino e o "Precisa de atenção" vão ler), manda e-mail aos responsáveis e audita como sistema. Não muda vínculo nem financeiro.
 * Aluno cadastrado já adulto (cadastro depois do 18º aniversário) nunca é avisado. Cada organização roda no seu escopo.
 */
@Component
public class MajorityNoticeJob {
    private static final Logger LOG = LoggerFactory.getLogger(MajorityNoticeJob.class);
    private static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);

    private record Notice(String email, String studentName) {}

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final EmailService email;
    private final ScopedAudit audit;
    private final Clock clock;

    public MajorityNoticeJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, EmailService email, ScopedAudit audit, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @SchedulerLock(name = "student-majority-notice", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    /** Devolve quantos alunos foram avisados; uma organização com falha não impede as demais. */
    public int runOnce() {
        int total = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            try {
                List<Notice> notices = new ArrayList<>();
                int done = transactions.provision(org, () -> notify(org, notices));
                for (Notice n : notices) email.send(n.email(), EmailTemplate.STUDENT_MAJORITY, n.studentName());
                total += done;
            } catch (RuntimeException e) {
                LOG.error("student majority notice failed org={}", org, e);
            }
        }
        return total;
    }

    private int notify(UUID org, List<Notice> notices) {
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Cadastrado menor: o cadastro é anterior ao 18º aniversário.
        var due = jdbc.sql("select id, name from student where organization_id = :org and majority_notice_at is null "
                + "and (birth_date + interval '18 years')::date <= :today and (created_at at time zone 'UTC')::date < (birth_date + interval '18 years')::date "
                + "order by id").param("org", org).param("today", today)
                .query((rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2)}).list();
        for (Object[] s : due) {
            UUID id = (UUID) s[0];
            jdbc.sql("update student set majority_notice_at = :now where id = :id").param("now", now).param("id", id).update();
            for (String to : jdbc.sql("select a.email from student_guardian sg join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id "
                    + "join account a on a.id = g.account_id where sg.organization_id = :org and sg.student_id = :id and g.email_problem_at is null "
                    + "order by sg.financial desc, g.id").param("org", org).param("id", id).query(String.class).list()) {
                notices.add(new Notice(to, (String) s[1]));
            }
            audit.record(org, new AuditEntry(SYSTEM, "student.majority_notice", "student", id.toString(), null, null,
                    Map.of("majorityNotice", true), null, null));
        }
        return due.size();
    }
}
