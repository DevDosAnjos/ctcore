package br.com.ctcore.staff;

import br.com.ctcore.identity.Role;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** RF-ACE-06 and RN-ORG-07: who manages whom, unit reach and the last-administrator rule; pure, no database. */
final class StaffRules {
    private StaffRules() {}

    /** D2: the Coordinator manages Administrators, the Administrator manages Teachers; nobody else manages. */
    static Role managedRole(Role caller) {
        return switch (caller) {
            case COORDINATOR -> Role.ADMINISTRATOR;
            case ADMINISTRATOR -> Role.TEACHER;
            default -> null;
        };
    }

    /** The target is visible to the caller: right profile and, for the Administrator, at least one shared unit. */
    static boolean reaches(Role caller, Set<UUID> callerUnits, Role targetRole, Set<UUID> targetUnits) {
        if (managedRole(caller) != targetRole) return false;
        return caller == Role.COORDINATOR || targetUnits.stream().anyMatch(callerUnits::contains);
    }

    /** D5: full management (resend, deactivate) needs every unit of the target inside the caller's reach. */
    static boolean canManage(Role caller, Set<UUID> callerUnits, Role targetRole, Set<UUID> targetUnits) {
        return reaches(caller, callerUnits, targetRole, targetUnits) && (caller == Role.COORDINATOR || callerUnits.containsAll(targetUnits));
    }

    /** D5: the Coordinator sets the whole list; the Administrator only touches his own units and the rest stay. */
    static Set<UUID> mergeUnits(Role caller, Set<UUID> callerUnits, Set<UUID> current, Set<UUID> desired) {
        if (caller == Role.COORDINATOR) return new LinkedHashSet<>(desired);
        Set<UUID> result = new LinkedHashSet<>(current);
        result.removeAll(callerUnits);
        result.addAll(desired);
        return result;
    }

    /** RN-ORG-07: the units, among those losing the administrator, where nobody else active remains. */
    static Set<UUID> leftWithoutAdministrator(Set<UUID> losing, Map<UUID, Integer> otherActiveAdministrators) {
        Set<UUID> left = new TreeSet<>();
        for (UUID unit : losing) {
            if (otherActiveAdministrators.getOrDefault(unit, 0) == 0) left.add(unit);
        }
        return left;
    }

    static Set<UUID> removed(Set<UUID> before, Set<UUID> after) {
        Set<UUID> result = new HashSet<>(before);
        result.removeAll(after);
        return result;
    }
}
