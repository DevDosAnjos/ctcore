package br.com.ctcore.teacher;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.identity.LinkService;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RF-PRO-04 / RN-PRO-05: inativar o professor. Muda a situação, encerra o vínculo de Professor (e as sessões que dependiam dele); a
 * conta e o {@code account_id} do cadastro ficam (D2). O Coordenador inativa qualquer um; o Administrador só quem tem todas as
 * unidades entre as dele (D3).
 */
@Service
public class TeacherDeactivation {
    public record Preview(List<Teachers.ClassRef> headClasses, boolean canDeactivate) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Teachers teachers;
    private final LinkService links;
    private final InviteService invites;
    private final AuditService audit;

    public TeacherDeactivation(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Teachers teachers, LinkService links,
            InviteService invites, AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.teachers = teachers;
        this.links = links;
        this.invites = invites;
        this.audit = audit;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    /** Coordenador alcança todos; Administrador só se todas as unidades do professor (ativas ou não) forem dele. */
    private boolean inReach(AccountPrincipal who, UUID id) {
        if (who.role() != Role.ADMINISTRATOR) return true;
        Set<UUID> reach = who.units() == null ? Set.of() : who.units();
        return reach.containsAll(jdbc.sql("select unit_id from teacher_unit where organization_id = :org and teacher_id = :id")
                .param("org", who.organizationId()).param("id", id).query(UUID.class).list());
    }

    public Preview preview(AccountPrincipal who, UUID id) {
        teachers.requireAccess(who);
        return transactions.execute(() -> {
            Teachers.Basic t = teachers.find(who, id);
            return new Preview(teachers.headClasses(id), t.status().equals("ACTIVE") && inReach(who, id));
        });
    }

    public Teachers.Detail deactivate(AccountPrincipal who, UUID id, String ip) {
        teachers.requireAccess(who);
        Inviter inviter = new Inviter(who.accountId(), who.role(), who.units());
        // O fim das sessões audita em outras organizações e por isso roda depois do commit desta.
        record Result(Teachers.Detail detail, LinkService.Before ended) {}
        Result r = transactions.execute(() -> {
            jdbc.sql("select id from organization where id = :org for update").param("org", who.organizationId()).query(UUID.class).single();
            Teachers.Basic t = teachers.find(who, id);
            if (!inReach(who, id)) throw new AccessDeniedException("Units out of reach");
            if (t.status().equals("INACTIVE")) throw new ApiStatusException(ErrorCode.CONFLICT);
            LinkService.Before ended = null;
            if (t.accountId() != null && !t.access().equals("NONE") && !t.access().equals("ENDED")) {
                UUID link = jdbc.sql("select id from account_link where organization_id = :org and account_id = :a and role = 'TEACHER'")
                        .param("org", who.organizationId()).param("a", t.accountId()).query(UUID.class).single();
                if (t.access().equals("PENDING")) invites.cancelTeacher(inviter, who.organizationId(), link);
                else ended = links.endInScope(actor(who), who.organizationId(), link);
            }
            jdbc.sql("update teacher set status = 'INACTIVE' where id = :id").param("id", id).update();
            audit.record(new AuditEntry(actor(who), "teacher.deactivated", "teacher", id.toString(), null,
                    Map.of("status", "ACTIVE"), Map.of("status", "INACTIVE", "classesWithoutHead", teachers.headClasses(id).size(),
                            "accessEnded", !t.access().equals("NONE") && !t.access().equals("ENDED")), null, ip));
            return new Result(teachers.detail(who, id), ended);
        });
        if (r.ended() != null) links.sessionsAfter(actor(who), r.ended());
        return r.detail();
    }
}
