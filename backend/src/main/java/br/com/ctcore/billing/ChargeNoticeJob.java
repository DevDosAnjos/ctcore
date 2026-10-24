package br.com.ctcore.billing;

import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-COM-11 (D3, D4): avisa o pagador, uma vez, de cada cobrança em aberto que vence em até 10 dias. Uma transação por cobrança: o
 * {@code notified_at} e a linha de e-mail saem juntos, e o erro de uma cobrança vai ao log sem dado pessoal e não impede as outras.
 * Sem auditoria: é efeito de sistema (D9). ponytail: janela e periodicidade fixas aqui; viram configuração se a especificação os definir.
 */
@Component
public class ChargeNoticeJob {
    private static final Logger LOG = LoggerFactory.getLogger(ChargeNoticeJob.class);
    private static final int WINDOW_DAYS = 10;
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final EmailService email;
    private final Clock clock;

    public ChargeNoticeJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, EmailService email, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.email = email;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @SchedulerLock(name = "billing-charge-notices", lockAtMostFor = "PT10M")
    public void run() {
        runOnce();
    }

    /** Number of charges marked as notified. */
    public int runOnce() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int done = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            List<UUID> due = transactions.provision(org, () -> jdbc.sql("select c.id from charge c join unit u on u.id = c.unit_id "
                    + "where c.organization_id = :org and c.notified_at is null and c.status = 'OPEN' "
                    + "and c.due_on <= (cast(:now as timestamptz) at time zone u.timezone)::date + " + WINDOW_DAYS + " order by c.due_on, c.id")
                    .param("org", org).param("now", now).query(UUID.class).list());
            for (UUID id : due) {
                try {
                    done += transactions.provision(org, () -> notice(org, id, now));
                } catch (RuntimeException e) {
                    LOG.error("charge notice failed org={} charge={}", org, id, e);
                }
            }
        }
        return done;
    }

    private int notice(UUID org, UUID id, OffsetDateTime now) {
        // Reivindica a linha antes de enviar: outro nó ou a mesma execução não repete.
        int claimed = jdbc.sql("update charge set notified_at = :now where id = :id and organization_id = :org and notified_at is null and status = 'OPEN'")
                .param("now", now).param("id", id).param("org", org).update();
        if (claimed == 0) return 0;
        var row = jdbc.sql("select coalesce(ga.email, s.email) as email, c.type, c.amount_cents, c.due_on, c.reference_month, c.description "
                + "from charge c join student s on s.organization_id = c.organization_id and s.id = c.student_id "
                + "left join guardian g on g.organization_id = c.organization_id and g.id = c.payer_guardian_id left join account ga on ga.id = g.account_id "
                + "where c.id = :id and c.organization_id = :org").param("id", id).param("org", org).query().singleRow();
        String to = (String) row.get("email");
        if (to == null || !to.contains("@")) return 1;
        boolean diff = "READJUSTMENT_DIFF".equals(row.get("type"));
        String description = row.get("description") != null ? (String) row.get("description")
                : row.get("reference_month") != null ? "Mensalidade " + ((java.sql.Date) row.get("reference_month")).toLocalDate().format(DateTimeFormatter.ofPattern("MM/yyyy"))
                : "Cobrança";
        email.send(org, to.strip(), diff ? EmailTemplate.READJUSTMENT_DIFF : EmailTemplate.CHARGE_CREATED, description,
                NumberFormat.getCurrencyInstance(PT_BR).format(((Number) row.get("amount_cents")).longValue() / 100.0),
                ((java.sql.Date) row.get("due_on")).toLocalDate().format(DATE));
        return 1;
    }
}
