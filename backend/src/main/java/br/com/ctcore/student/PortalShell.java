package br.com.ctcore.student;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationProfile;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RF-POR-01, RN-POR-08, RN-INA-08, RN-INA-09: moldura do portal. Marca da organização, se o portal é reduzido e o contador do sino (avisos enviados e ainda
 * não lidos). Considera só os alunos ligados à conta da sessão; {@link #reduced} é o ponto que as rotas de conteúdo reutilizam.
 */
@Service
public class PortalShell {
    public record View(OrganizationProfile.Brand organization, boolean reduced, int unreadAnnouncements) {}

    /** Alunos ligados à conta (:acc) na organização (:org); alias {@code s}. Mesma ligação de {@link PortalStudents#cards}. */
    private static final String LINKED = "s.organization_id = :org and (s.account_id = :acc or exists (select 1 from student_guardian sg join guardian g "
            + "on g.organization_id = sg.organization_id and g.id = sg.guardian_id where sg.organization_id = s.organization_id "
            + "and sg.student_id = s.id and g.account_id = :acc))";

    private static final String ENROLLED = "exists (select 1 from student s join enrollment e on e.organization_id = s.organization_id and e.student_id = s.id where " + LINKED + ")";

    private static final String ACTIVE = "exists (select 1 from student s join enrollment_modality em on em.organization_id = s.organization_id and em.student_id = s.id "
            + "and em.status = 'ACTIVE' where " + LINKED + ")";

    /** Alcance do comunicado (alias {@code a}) sobre uma modalidade ativa (alias {@code em}): organização, unidade, modalidade, turma ou turmas. Reusada pelo Início. */
    static final String REACH = "(a.target_kind = 'ORGANIZATION' "
            + "or (a.target_kind = 'UNIT' and em.unit_id = a.unit_id) "
            + "or (a.target_kind = 'MODALITY' and em.modality_id = a.modality_id and (a.unit_id is null or em.unit_id = a.unit_id)) "
            + "or (a.target_kind = 'CLASS' and (em.primary_class_id = a.class_id or exists (select 1 from enrollment_modality_class x "
            + "where x.organization_id = em.organization_id and x.modality_row_id = em.id and x.class_id = a.class_id))) "
            + "or (a.target_kind = 'CLASSES' and exists (select 1 from announcement_class ac where ac.organization_id = a.organization_id "
            + "and ac.announcement_id = a.id and (ac.class_id = em.primary_class_id or exists (select 1 from enrollment_modality_class x "
            + "where x.organization_id = em.organization_id and x.modality_row_id = em.id and x.class_id = ac.class_id)))))";

    // Comunicados enviados que alcançam um aluno ligado e ativo que ainda não o leu (um aviso conta uma vez, mesmo com dois filhos).
    private static final String UNREAD = "select count(*) from announcement a where a.organization_id = :org and a.sent_at is not null and exists ("
            + "select 1 from student s join enrollment_modality em on em.organization_id = s.organization_id and em.student_id = s.id and em.status = 'ACTIVE' "
            + "where " + LINKED + " and " + REACH + " "
            + "and not exists (select 1 from announcement_read r where r.organization_id = a.organization_id and r.announcement_id = a.id and r.student_id = s.id))";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final OrganizationProfile organization;

    public PortalShell(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, OrganizationProfile organization) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.organization = organization;
    }

    /** Quem chama já conferiu o perfil de portal e o aceite do termo. */
    public View view(AccountPrincipal who) {
        OrganizationProfile.Brand brand = organization.brand(who);
        return transactions.execute(() -> {
            boolean reduced = isReduced(who);
            int unread = reduced ? 0 : jdbc.sql(UNREAD).param("org", who.organizationId()).param("acc", who.accountId()).query(Integer.class).single();
            return new View(brand, reduced, unread);
        });
    }

    /** RN-POR-08: a conta tem aluno matriculado e nenhum aluno ligado tem modalidade ativa. */
    public boolean reduced(AccountPrincipal who) {
        return transactions.execute(() -> isReduced(who));
    }

    // Dentro de uma transação da organização.
    private boolean isReduced(AccountPrincipal who) {
        return jdbc.sql("select " + ENROLLED + " and not " + ACTIVE).param("org", who.organizationId()).param("acc", who.accountId())
                .query(Boolean.class).single();
    }
}
