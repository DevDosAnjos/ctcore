package br.com.ctcore.student;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-MAT-13 (D5): confirmação curta, por quem aceita o termo, de cada modalidade ativa criada depois do primeiro aceite do aluno. Grava plano e valor mostrados e
 * nunca altera matrícula, plano nem cobrança. Não bloqueia o portal, mas só funciona com o termo em dia (portão de {@link PortalTerms#requireAccepted}).
 */
@Service
public class ModalityConfirmations {
    public record Item(UUID modalityRowId, UUID studentId, String studentName, String modality, String unit, UUID planId, String plan, long planValueCents) {}

    public record Confirmed(UUID modalityRowId, UUID studentId, UUID planId, long planValueCents) {}

    private static final String PENDING = "select em.id, em.student_id, s.name, md.name, u.name, p.id, p.name, p.value_cents from enrollment_modality em "
            + "join student s on s.organization_id = em.organization_id and s.id = em.student_id join modality md on md.id = em.modality_id "
            + "join unit u on u.organization_id = em.organization_id and u.id = em.unit_id join plan p on p.organization_id = em.organization_id and p.id = em.plan_id "
            + "where em.organization_id = :org and em.status = 'ACTIVE' "
            + "and not exists (select 1 from modality_confirmation mc where mc.organization_id = em.organization_id and mc.enrollment_modality_id = em.id) "
            + "and em.created_at > (select min(a.accepted_at) from term_acceptance a where a.organization_id = em.organization_id and a.student_id = em.student_id "
            + "and a.kind = 'ENROLLMENT' and a.event = 'ACCEPTED') ";

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PortalTerms terms;
    private final AuditService audit;

    public ModalityConfirmations(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PortalTerms terms, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.terms = terms;
        this.audit = audit;
    }

    /** Modalidades a confirmar, dos alunos que a conta aceita. */
    public List<Item> pending(AccountPrincipal who) {
        terms.requireAccepted(who);
        return transactions.execute(() -> {
            List<UUID> ids = terms.candidates(who, null, false).stream().filter(c -> terms.canAccept(c) && c.enrolled()).map(PortalTerms.Linked::id).toList();
            if (ids.isEmpty()) return List.<Item>of();
            return jdbc.sql(PENDING + "and em.student_id in (:ids) order by s.name, md.name, em.id").param("org", who.organizationId()).param("ids", ids)
                    .query((rs, n) -> item(rs)).list();
        });
    }

    /** 201 (created) grava a confirmação; repetir devolve 200 com a mesma, sem nova linha nem auditoria. */
    public PortalTerms.Outcome<Confirmed> confirm(AccountPrincipal who, UUID rowId, String ip) {
        terms.requireAccepted(who);
        return transactions.execute(() -> {
            UUID org = who.organizationId();
            UUID student = jdbc.sql("select student_id from enrollment_modality where organization_id = :org and id = :id").param("org", org).param("id", rowId)
                    .query(UUID.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            terms.acceptable(who, student, true);
            Optional<Confirmed> done = existing(org, rowId);
            if (done.isPresent()) return new PortalTerms.Outcome<>(false, done.get());
            Item item = jdbc.sql(PENDING + "and em.id = :row").param("org", org).param("row", rowId).query((rs, n) -> item(rs)).optional()
                    .orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            int inserted = jdbc.sql("insert into modality_confirmation (id, organization_id, enrollment_modality_id, student_id, account_id, plan_id, plan_value_cents, ip) "
                    + "values (:id, :org, :row, :student, :acc, :plan, :value, :ip) on conflict (enrollment_modality_id) do nothing")
                    .param("id", UUID.randomUUID()).param("org", org).param("row", rowId).param("student", student).param("acc", who.accountId())
                    .param("plan", item.planId()).param("value", item.planValueCents()).param("ip", ip).update();
            if (inserted == 0) return new PortalTerms.Outcome<>(false, existing(org, rowId).orElseThrow());
            var after = new LinkedHashMap<String, Object>();
            after.put("studentId", student);
            after.put("modalityRowId", rowId);
            after.put("planId", item.planId());
            after.put("planValueCents", item.planValueCents());
            audit.record(new AuditEntry(new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name()), "modality.confirmed", "modality_confirmation",
                    student.toString(), null, Map.of(), after, null, ip));
            return new PortalTerms.Outcome<>(true, new Confirmed(rowId, student, item.planId(), item.planValueCents()));
        });
    }

    private Optional<Confirmed> existing(UUID org, UUID rowId) {
        return jdbc.sql("select enrollment_modality_id, student_id, plan_id, plan_value_cents from modality_confirmation where organization_id = :org and enrollment_modality_id = :row")
                .param("org", org).param("row", rowId)
                .query((rs, n) -> new Confirmed(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getLong(4))).optional();
    }

    private static Item item(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Item(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID.class),
                rs.getString(7), rs.getLong(8));
    }
}
