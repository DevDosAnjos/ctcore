package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountEmailChanged;
import br.com.ctcore.identity.AccountTransactions;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-ALU-11, RN-ACE-15, D3: confirmada a troca de e-mail da conta, o e-mail de contato do cadastro acompanha, em toda organização em que a conta tem cadastro.
 * O aluno com a conta recebe o endereço novo; o do responsável é o da própria conta (não há coluna), então só o marcador de e-mail com problema sai. Falha numa
 * organização vai para o log, sem e-mail, e não desfaz a confirmação nem as demais.
 */
@Component
class ContactEmailSync {
    private static final Logger LOG = LoggerFactory.getLogger(ContactEmailSync.class);

    private record Link(UUID org, String role) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final AccountTransactions accounts;
    private final AuditService audit;

    ContactEmailSync(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, @Lazy AccountTransactions accounts, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.accounts = accounts;
        this.audit = audit;
    }

    /** A confirmação roda na sessão de uma organização, e o escopo de organização é por thread: o trabalho vai para uma thread limpa (que esta espera). */
    @EventListener
    void onEmailChanged(AccountEmailChanged event) {
        Thread worker = new Thread(() -> syncAll(event), "contact-email-sync");
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void syncAll(AccountEmailChanged event) {
        List<Link> links;
        try {
            links = accounts.forAccount(event.accountId(), () -> jdbc.sql("select organization_id, min(role) from account_link where account_id = :a "
                    + "and status = 'ACTIVE' and role in ('STUDENT', 'GUARDIAN') group by organization_id").param("a", event.accountId())
                    .query((rs, n) -> new Link(rs.getObject(1, UUID.class), rs.getString(2))).list());
        } catch (RuntimeException e) {
            LOG.warn("Could not list the registrations of an account after an e-mail change: {}", e.getClass().getSimpleName());
            return;
        }
        for (Link link : links) {
            try (var ignored = OrganizationContext.open(system(link.org()))) {
                transactions.execute(() -> sync(link, event));
            } catch (RuntimeException e) {
                LOG.warn("Contact e-mail not synchronized for organization {}: {}", link.org(), e.getClass().getSimpleName());
            }
        }
    }

    private Object sync(Link link, AccountEmailChanged event) {
        var actor = new AuditActor(AuditActor.Type.PORTAL, event.accountId(), link.role());
        var students = jdbc.sql("select id, registered_unit_id from student where organization_id = :org and account_id = :a")
                .param("org", link.org()).param("a", event.accountId()).query((rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}).list();
        for (UUID[] s : students) {
            jdbc.sql("update student set email = :e, email_problem_at = null where organization_id = :org and id = :id").param("e", event.newEmail())
                    .param("org", link.org()).param("id", s[0]).update();
            audit.record(new AuditEntry(actor, "student.updated", "student", s[0].toString(), s[1], Map.of(), Map.of("email", "alterado"), null, event.ip()));
        }
        var guardians = jdbc.sql("select id from guardian where organization_id = :org and account_id = :a").param("org", link.org()).param("a", event.accountId())
                .query((rs, n) -> rs.getObject(1, UUID.class)).list();
        for (UUID g : guardians) {
            jdbc.sql("update guardian set email_problem_at = null where organization_id = :org and id = :id").param("org", link.org()).param("id", g).update();
            audit.record(new AuditEntry(actor, "guardian.updated", "guardian", g.toString(), null, Map.of(), Map.of("email", "alterado"), null, event.ip()));
        }
        return null;
    }

    private static OrganizationPrincipal system(UUID organizationId) {
        return new OrganizationPrincipal() {
            @Override public UUID organizationId() { return organizationId; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        };
    }
}
