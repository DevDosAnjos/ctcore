package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RN-RSP-03, RN-RSP-04, RN-LGP-03: cartões do portal. Um por aluno ligado ao responsável da sessão (em qualquer unidade da organização) e,
 * se a conta também é a conta de um aluno, o cartão dele com {@code self}. Só id, nome e foto; o menor nunca aparece para outra conta.
 */
@Service
public class PortalStudents {
    public record Card(UUID id, String name, String photoUrl, boolean self) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Students students;

    public PortalStudents(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Students students) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.students = students;
    }

    public List<Card> cards(AccountPrincipal who) {
        if (who == null || (who.role() != Role.GUARDIAN && who.role() != Role.STUDENT)) throw new AccessDeniedException("Portal session required");
        return transactions.execute(() -> jdbc.sql("select s.id, s.name, s.photo, coalesce(s.account_id = :acc, false) as self from student s "
                + "where s.organization_id = :org and (s.account_id = :acc or exists (select 1 from student_guardian sg join guardian g "
                + "on g.organization_id = sg.organization_id and g.id = sg.guardian_id where sg.organization_id = s.organization_id "
                + "and sg.student_id = s.id and g.account_id = :acc)) order by self desc, s.name, s.id")
                .param("org", who.organizationId()).param("acc", who.accountId())
                .query((rs, n) -> new Card(rs.getObject(1, UUID.class), rs.getString(2), students.photoUrl(rs.getString(3)), rs.getBoolean(4))).list());
    }
}
