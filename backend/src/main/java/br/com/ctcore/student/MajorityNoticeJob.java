package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import br.com.ctcore.notification.NotificationType;
import br.com.ctcore.notification.Notifications;
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

    private record Majority(UUID id, UUID unitId, String studentName) {}

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final EmailService email;
    private final ScopedAudit audit;
    private final Clock clock;
    private final Notifications notifications;

    public MajorityNoticeJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, EmailService email, ScopedAudit audit, Clock clock,
            Notifications notifications) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.email = email;
        this.audit = audit;
        this.clock = clock;
        this.notifications = notifications;
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
                List<Majority> majors = new ArrayList<>();
                int done = transactions.provision(org, () -> notify(org, notices, majors));
                for (Notice n : notices) email.send(n.email(), EmailTemplate.STUDENT_MAJORITY, n.studentName());
                for (Majority m : majors) {
                    notifications.publish(org, m.unitId(), NotificationType.STUDENT_MAJORITY, "student", m.id(), Map.of("studentName", m.studentName()));
                }
                total += done;
            } catch (RuntimeException e) {
                LOG.error("student majority notice failed org={}", org, e);
            }
        }
        return total;
    }

    private int notify(UUID org, List<Notice> notices, List<Majority> majors) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Cadastrado menor: o cadastro é anterior ao 18º aniversário.
        // S8, R28: "hoje" e o dia do cadastro são os do fuso da unidade de cadastro.
        var due = jdbc.sql("select s.id, s.name, s.registered_unit_id from student s join unit u on u.id = s.registered_unit_id and u.organization_id = s.organization_id "
                + "where s.organization_id = :org and s.majority_notice_at is null "
                + "and (s.birth_date + interval '18 years')::date <= (cast(:now as timestamptz) at time zone u.timezone)::date "
                + "and (s.created_at at time zone u.timezone)::date < (s.birth_date + interval '18 years')::date "
                + "order by s.id").param("org", org).param("now", now)
                .query((rs, n) -> new Object[]{rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)}).list();
        for (Object[] s : due) {
            UUID id = (UUID) s[0];
            majors.add(new Majority(id, (UUID) s[2], (String) s[1]));
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
