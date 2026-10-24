package br.com.ctcore.billing;

import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-COB-17 (D1 a D4): lembretes automáticos por e-mail no dia exato {@code vencimento + dia} de {@code unit_settings.reminder_days}, depois da hora da
 * unidade, uma vez por cobrança e dia; e o aviso fixo 5 dias antes do bloqueio (só mensalidade, parcela e diferença de reajuste, em unidade com conta
 * conectada), que ainda manda SMS. Uma transação por cobrança, com a linha travada: pagamento confirmado antes impede o envio (RN-COB-19). Sem auditoria
 * própria (D9); o erro de uma cobrança vai ao log só com a classe da exceção e não impede as outras.
 * ponytail: dia perdido (sistema parado o dia inteiro) não é recuperado (D2); periodicidade fixa em 15 minutos.
 */
@Component
public class ReminderJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReminderJob.class);
    static final int BLOCK_WARNING_DAYS = 5;

    private record Candidate(UUID id, String kind, Integer offset, String zone) {}

    private static final String BASE = "from charge c join unit u on u.organization_id = c.organization_id and u.id = c.unit_id "
            + "join unit_settings us on us.organization_id = c.organization_id and us.unit_id = c.unit_id "
            + "join organization_settings os on os.organization_id = c.organization_id where c.organization_id = :org and c.status = 'OPEN' "
            + "and extract(hour from cast(:now as timestamptz) at time zone u.timezone) >= us.reminders_hour ";

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final ReminderSender sender;
    private final BlockWarningSms sms;
    private final Clock clock;

    public ReminderJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, ReminderSender sender, BlockWarningSms sms, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.sender = sender;
        this.sms = sms;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT1M")
    @SchedulerLock(name = "billing-reminders", lockAtMostFor = "PT10M")
    public void run() {
        runOnce();
    }

    /** Quantidade de e-mails enviados (SMS não conta). */
    public int runOnce() {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        int done = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            List<Candidate> due = transactions.provision(org, () -> candidates(org, now));
            for (Candidate c : due) {
                try {
                    done += transactions.provision(org, () -> remind(org, c, now));
                } catch (RuntimeException e) {
                    LOG.error("charge reminder failed org={} charge={} error={}", org, c.id(), e.getClass().getSimpleName());
                }
            }
        }
        return done;
    }

    private List<Candidate> candidates(UUID org, OffsetDateTime now) {
        String day = Charges.today("u") + " - c.due_on";
        List<Candidate> list = new ArrayList<>(jdbc.sql("select c.id, " + day + ", u.timezone " + BASE + "and us.reminders_enabled and (" + day
                + ") = any(us.reminder_days) order by c.id").param("org", org).param("now", now)
                .query((rs, n) -> new Candidate(rs.getObject(1, UUID.class), "SCHEDULED", rs.getInt(2), rs.getString(3))).list());
        // R3: o aviso de bloqueio é fixo; não depende de reminder_days nem de reminders_enabled.
        list.addAll(jdbc.sql("select c.id, u.timezone " + BASE
                + "and c.type in ('MONTHLY', 'INSTALLMENT', 'READJUSTMENT_DIFF') and us.payment_account_reference is not null "
                + "and c.due_on + os.deactivation_days - " + BLOCK_WARNING_DAYS + " = " + Charges.today("u") + " order by c.id").param("org", org).param("now", now)
                .query((rs, n) -> new Candidate(rs.getObject(1, UUID.class), "BLOCK_WARNING", null, rs.getString(2))).list());
        return list;
    }

    private int remind(UUID org, Candidate c, OffsetDateTime now) {
        // Trava a linha antes de enviar: pagamento confirmado (ou cancelamento) antes deste ponto impede o envio.
        LocalDate due = jdbc.sql("select due_on from charge where id = :id and organization_id = :org and status = 'OPEN' for update")
                .param("id", c.id()).param("org", org).query(LocalDate.class).optional().orElse(null);
        if (due == null) return 0;
        boolean block = c.offset() == null;
        EmailTemplate template = block ? EmailTemplate.BLOCK_WARNING : ReminderSender.template(due.plusDays(c.offset()), due);
        var result = sender.send(org, c.id(), c.kind(), c.offset(), template, null, block ? BLOCK_WARNING_DAYS : null);
        if (result == null) return 0;
        if (block) sms.send(org, c.id(), c.zone(), BLOCK_WARNING_DAYS);
        return result == ReminderSender.Result.SENT ? 1 : 0;
    }
}
