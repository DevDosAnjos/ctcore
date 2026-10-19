package br.com.ctcore.identity;

import br.com.ctcore.audit.AuditActor;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** RN-ACE-05 (cascade) and RN-ACE-06 (adulthood); pure rules. */
public final class InviteRules {
    private InviteRules() {}

    /** Role null means the system (enrollment), the only one that invites the portal. */
    public record Inviter(UUID accountId, Role role, Set<UUID> unitIds) {
        public static Inviter system() { return new Inviter(null, null, Set.of()); }

        public AuditActor actor() {
            return role == null ? new AuditActor(AuditActor.Type.SYSTEM, null, null)
                    : new AuditActor(AuditActor.Type.ACCOUNT, accountId, role.name());
        }
    }

    public static boolean allowed(Inviter by, Role target, Set<UUID> units) {
        if (by.role() == null) {
            return !target.management();
        }
        return switch (by.role()) {
            case COORDINATOR -> target == Role.ADMINISTRATOR;
            case ADMINISTRATOR -> target == Role.TEACHER && !units.isEmpty() && by.unitIds().containsAll(units);
            default -> false;
        };
    }

    /** RN-PRO-02: dar acesso a um Professor pelo painel do professor; o Coordenador alcança qualquer unidade, o Administrador só as dele. */
    public static boolean teacherAccess(Inviter by, Set<UUID> units) {
        if (units.isEmpty()) return false;
        return by.role() == Role.COORDINATOR || allowed(by, Role.TEACHER, units);
    }

    public static boolean adult(LocalDate birth, LocalDate today) {
        return birth != null && !birth.plusYears(18).isAfter(today);
    }
}
