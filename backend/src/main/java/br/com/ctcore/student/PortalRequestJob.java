package br.com.ctcore.student;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RN-MAT-16, RN-MAT-18 (D7): tarefa diária dos pedidos do portal. Expira o pedido de troca de turma, nova modalidade ou troca de plano sem resposta quando a
 * data local da unidade chega a 30 dias depois da data do pedido; e encerra a modalidade do pedido de encerramento sem ação da gestão no 8º dia (data do pedido
 * mais 8 dias), com {@code ended_at} igual ao instante do pedido. Audita como sistema e avisa o aluno por e-mail depois do commit. Cada pedido tem a sua transação.
 */
@Component
public class PortalRequestJob {
    private static final Logger LOG = LoggerFactory.getLogger(PortalRequestJob.class);
    private static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);
    static final int EXPIRE_DAYS = 30;
    static final int CLOSE_DAYS = 8;

    private record Due(UUID id, String kind, UUID studentId, UUID rowId, UUID unitId, String reason, OffsetDateTime createdAt) {}

    private final OrganizationTransactions transactions;
    private final JdbcClient jdbc;
    private final PortalRequests requests;
    private final Enrollments enrollments;
    private final Clock clock;

    public PortalRequestJob(@Lazy OrganizationTransactions transactions, @Lazy JdbcClient jdbc, PortalRequests requests, Enrollments enrollments, Clock clock) {
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.requests = requests;
        this.enrollments = enrollments;
        this.clock = clock;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "UTC")
    @SchedulerLock(name = "student-portal-request", lockAtMostFor = "PT30M")
    public void run() {
        runOnce();
    }

    /** Devolve quantos pedidos foram expirados ou encerrados; uma organização ou um pedido com falha não impede os demais. */
    public int runOnce() {
        int total = 0;
        for (UUID org : jdbc.sql("select active_organization_ids()").query(UUID.class).list()) {
            try (var ignored = OrganizationContext.open(system(org))) {
                total += runOrganization(org);
            } catch (RuntimeException e) {
                LOG.error("portal request job failed org={}", org, e);
            }
        }
        return total;
    }

    private int runOrganization(UUID org) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<Due> due = transactions.execute(() -> jdbc.sql("select r.id, r.kind, r.student_id, r.enrollment_modality_id, r.unit_id, r.reason, r.created_at "
                + "from portal_request r join unit u on u.organization_id = r.organization_id and u.id = r.unit_id where r.organization_id = :org and r.status = 'OPEN' "
                + "and (cast(:now as timestamptz) at time zone u.timezone)::date >= (r.created_at at time zone u.timezone)::date "
                + "+ case when r.kind = 'CLOSURE' then " + CLOSE_DAYS + " else " + EXPIRE_DAYS + " end order by r.created_at, r.id")
                .param("org", org).param("now", now).query((rs, n) -> new Due(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
                        rs.getObject(4, UUID.class), rs.getObject(5, UUID.class), rs.getString(6), rs.getObject(7, OffsetDateTime.class))).list());
        int done = 0;
        for (Due d : due) {
            try {
                PortalRequests.Saved saved = transactions.execute(() -> "CLOSURE".equals(d.kind()) ? close(org, d) : expire(org, d));
                if (saved == null) continue;
                done++;
                requests.mail(org, saved);
            } catch (RuntimeException e) {
                LOG.error("portal request job failed org={} request={}", org, d.id(), e);
            }
        }
        return done;
    }

    private boolean stillOpen(UUID org, UUID id) {
        return "OPEN".equals(jdbc.sql("select status from portal_request where organization_id = :org and id = :id for update").param("org", org).param("id", id)
                .query(String.class).optional().orElse(null));
    }

    private PortalRequests.Saved expire(UUID org, Due d) {
        if (!stillOpen(org, d.id())) return null;
        return requests.finish(org, d.id(), "EXPIRED", SYSTEM, "portal_request.expired", null);
    }

    private PortalRequests.Saved close(UUID org, Due d) {
        jdbc.sql("select id from student where organization_id = :org and id = :id for update").param("org", org).param("id", d.studentId()).query(UUID.class).single();
        if (!stillOpen(org, d.id())) return null;
        return enrollments.endLocked(org, SYSTEM, d.studentId(), d.rowId(), d.unitId(), d.reason(), null, Map.of(), d.createdAt(), null);
    }

    private static OrganizationPrincipal system(UUID organizationId) {
        return new OrganizationPrincipal() {
            @Override public UUID organizationId() { return organizationId; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        };
    }
}
