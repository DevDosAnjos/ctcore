package br.com.ctcore.student;

import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RN-ALU-09: who receives the charge e-mails: the financial guardian of a minor, the student himself when adult. */
@Component
public class PayerEmail {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Clock clock;

    public PayerEmail(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    /** Empty when the payer has no address. */
    public Optional<String> forStudent(UUID organizationId, UUID studentId) {
        return transactions.provision(organizationId, () -> jdbc.sql(
                "select case when s.birth_date + interval '18 years' <= (cast(:now as timestamptz) at time zone u.timezone)::date then s.email else "
                + "(select a.email from student_guardian sg join guardian g on g.organization_id = sg.organization_id and g.id = sg.guardian_id "
                + "join account a on a.id = g.account_id where sg.organization_id = s.organization_id and sg.student_id = s.id and sg.financial limit 1) end "
                + "from student s join unit u on u.id = s.registered_unit_id where s.organization_id = :org and s.id = :id")
                .param("now", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).param("org", organizationId).param("id", studentId)
                .query(String.class).optional().filter(e -> !e.isBlank()));
    }
}
