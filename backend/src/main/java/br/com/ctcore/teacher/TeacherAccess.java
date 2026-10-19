package br.com.ctcore.teacher;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.IdentityException;
import br.com.ctcore.identity.IdentityException.Code;
import br.com.ctcore.identity.InviteRules.Inviter;
import br.com.ctcore.identity.InviteService;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.Organizations;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * RN-PRO-02: dar acesso ao sistema a um professor e reenviar o convite. Usa a cadeia de convite de #37 (mesmo serviço e mesmo
 * limite de usuários de gestão); o cadastro continua separado da conta e só ganha o {@code account_id}. Sem benefício (RN-PRO-08, D5).
 */
@Service
public class TeacherAccess {
    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final Teachers teachers;
    private final InviteService invites;
    private final AuditService audit;

    public TeacherAccess(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, Teachers teachers, InviteService invites,
            AuditService audit) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.teachers = teachers;
        this.invites = invites;
        this.audit = audit;
    }

    private static AuditActor actor(AccountPrincipal who) {
        return new AuditActor(AuditActor.Type.ACCOUNT, who.accountId(), who.role().name());
    }

    private static Inviter inviter(AccountPrincipal who) {
        return new Inviter(who.accountId(), who.role(), who.units());
    }

    private static List<String> sorted(Set<UUID> ids) {
        return new TreeSet<>(ids).stream().map(UUID::toString).toList();
    }

    public Teachers.Detail grant(AccountPrincipal who, UUID id, List<UUID> unitIds, String ip) {
        teachers.requireAccess(who);
        Set<UUID> asked = new LinkedHashSet<>(unitIds == null ? List.of() : unitIds);
        asked.remove(null);
        boolean admin = who.role() == Role.ADMINISTRATOR;
        if (admin && !who.units().containsAll(asked)) throw new AccessDeniedException("Unit out of reach");
        return transactions.execute(() -> {
            jdbc.sql("select id from organization where id = :org for update").param("org", who.organizationId()).query(UUID.class).single();
            Teachers.Basic t = teachers.find(who, id);
            if (t.email() == null || t.email().isBlank()) throw new ApiValidationException("email", "o professor não tem e-mail cadastrado");
            if (t.status().equals("INACTIVE") || t.access().equals("PENDING") || t.access().equals("ACTIVE")) {
                throw new ApiStatusException(ErrorCode.CONFLICT);
            }
            Set<UUID> units = units(who, id, asked, admin);
            // Conta do e-mail já ligada como Professor (por #37) ou a outro cadastro: não se liga sozinha (D4).
            UUID known = jdbc.sql("select id from account where lower(btrim(email)) = lower(btrim(:e))").param("e", t.email())
                    .query(UUID.class).optional().orElse(null);
            boolean other = t.accountId() != null && !t.accountId().equals(known);
            boolean taken = known != null && jdbc.sql("select count(*) from teacher where organization_id = :org and account_id = :a and id <> :id")
                    .param("org", who.organizationId()).param("a", known).param("id", id).query(Integer.class).single() > 0;
            boolean linked = known != null && jdbc.sql("select count(*) from account_link where organization_id = :org and account_id = :a "
                    + "and role = 'TEACHER' and status in ('PENDING', 'ACTIVE')").param("org", who.organizationId()).param("a", known)
                    .query(Integer.class).single() > 0;
            if (other || taken || linked) throw new IdentityException(Code.ALREADY_LINKED, null);
            int staff = jdbc.sql("select count(*) from account_link where organization_id = :org and status in ('PENDING', 'ACTIVE') "
                    + "and role in ('COORDINATOR', 'ADMINISTRATOR', 'TEACHER')").param("org", who.organizationId()).query(Integer.class).single();
            try {
                Organizations.requireManagementUserCapacity(staff, false);
            } catch (IllegalStateException e) {
                throw new ApiStatusException(ErrorCode.STAFF_LIMIT_REACHED);
            }
            var issued = invites.inviteTeacher(inviter(who), who.organizationId(), t.email().strip(), t.name(), units);
            jdbc.sql("update teacher set account_id = :a where id = :id").param("a", issued.accountId()).param("id", id).update();
            audit.record(new AuditEntry(actor(who), "teacher.access_granted", "teacher", id.toString(), null, null,
                    Map.of("unitIds", sorted(units)), null, ip));
            return teachers.detail(who, id);
        });
    }

    /** Unidades pedidas, ou as do professor que quem chama alcança; sempre do professor e ativas. */
    private Set<UUID> units(AccountPrincipal who, UUID id, Set<UUID> asked, boolean admin) {
        Set<UUID> mine = new LinkedHashSet<>(jdbc.sql("select tu.unit_id from teacher_unit tu join unit u on u.id = tu.unit_id "
                + "where tu.organization_id = :org and tu.teacher_id = :id and u.active").param("org", who.organizationId())
                .param("id", id).query(UUID.class).list());
        Set<UUID> units;
        if (asked.isEmpty()) {
            units = mine;
            if (admin) units.retainAll(who.units());
        } else {
            units = asked;
            if (!mine.containsAll(units)) throw new ApiValidationException("unitIds", "escolha unidades ativas do professor");
        }
        if (units.isEmpty()) throw new ApiValidationException("unitIds", "escolha ao menos uma unidade");
        return units;
    }

    public void resend(AccountPrincipal who, UUID id, String ip) {
        teachers.requireAccess(who);
        UUID link = transactions.execute(() -> {
            Teachers.Basic t = teachers.find(who, id);
            if (!t.access().equals("PENDING")) throw new ApiStatusException(ErrorCode.CONFLICT);
            return jdbc.sql("select id from account_link where organization_id = :org and account_id = :a and role = 'TEACHER'")
                    .param("org", who.organizationId()).param("a", t.accountId()).query(UUID.class).single();
        });
        invites.resendTeacher(inviter(who), who.organizationId(), link);
        transactions.execute(() -> {
            audit.record(new AuditEntry(actor(who), "teacher.invite_resent", "teacher", id.toString(), null, null, null, null, ip));
            return null;
        });
    }
}
