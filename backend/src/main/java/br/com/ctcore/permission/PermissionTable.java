package br.com.ctcore.permission;

import static br.com.ctcore.permission.Access.*;
import static br.com.ctcore.permission.Permission.*;

import br.com.ctcore.identity.Role;
import java.util.EnumMap;
import java.util.Map;

/**
 * Fonte única da tabela de permissões da gestão (RNF-05), célula a célula como na especificação.
 * Portal (Aluno e Responsável) não tem linha: é sempre {@link Grant#NONE}.
 */
public final class PermissionTable {
    private static final Grant ALL_FULL = new Grant(FULL, Scope.ALL), UNIT_FULL = new Grant(FULL, Scope.UNITS), CLASS_FULL = new Grant(FULL, Scope.CLASSES);
    private static final Grant ALL_READ = new Grant(READ, Scope.ALL), CLASS_READ = new Grant(READ, Scope.CLASSES);
    private static final Map<Role, Map<Permission, Grant>> TABLE = new EnumMap<>(Role.class);
    /** Índice da permissão do Professor em unit_settings.teacher_permissions (D3); as demais linhas não dependem dela. */
    private static final Map<Permission, Integer> TEACHER_FLAG = Map.of(
            ANNOUNCEMENTS, 0, AGENDA_EDIT, 1, STUDENT_CONTACTS, 2, TRIAL_CLASSES, 3, STUDENT_RECORD, 4);

    static {
        for (Role r : new Role[] {Role.COORDINATOR, Role.ADMINISTRATOR, Role.TEACHER}) {
            TABLE.put(r, new EnumMap<>(Permission.class));
        }
        row(HOME, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(ALL_UNITS_CONSOLIDATED, ALL_READ, Grant.NONE, Grant.NONE);
        row(STUDENT_VIEW, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(STUDENT_RECORD, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(STUDENT_CONTACTS, ALL_FULL, UNIT_FULL, CLASS_READ);
        row(STUDENT_MANAGE, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(BENEFITS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(PLANS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(COLLECTIONS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(CHARGE_ADJUSTMENTS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(CLASSES_MANAGE, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(AGENDA_VIEW, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(AGENDA_EDIT, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(NO_CLASS_DAYS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(GRADUATION, ALL_FULL, UNIT_FULL, CLASS_READ);
        row(ANNOUNCEMENTS, ALL_FULL, UNIT_FULL, CLASS_FULL);
        row(TRIAL_CLASSES, ALL_FULL, UNIT_FULL, CLASS_READ);
        row(TEACHERS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(STAFF_USERS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(UNIT_SETTINGS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(ORGANIZATION_SETTINGS, ALL_FULL, ALL_READ, Grant.NONE);
        row(UNITS, ALL_FULL, Grant.NONE, Grant.NONE);
        row(SUBSCRIPTION, ALL_FULL, new Grant(NOTICE, Scope.ALL), Grant.NONE);
        row(AUDIT, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(REPORTS, ALL_FULL, UNIT_FULL, new Grant(OWN, Scope.CLASSES));
        row(EXPORT_LISTS, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(EXPORT_FULL, ALL_FULL, Grant.NONE, Grant.NONE);
        row(IMPORT, ALL_FULL, UNIT_FULL, Grant.NONE);
        row(SUPPORT_ACCESS, ALL_FULL, Grant.NONE, Grant.NONE);
    }

    private PermissionTable() {}

    private static void row(Permission p, Grant coordinator, Grant administrator, Grant teacher) {
        TABLE.get(Role.COORDINATOR).put(p, coordinator);
        TABLE.get(Role.ADMINISTRATOR).put(p, administrator);
        TABLE.get(Role.TEACHER).put(p, teacher);
    }

    public static Grant grant(Role role, Permission permission) {
        Map<Permission, Grant> row = role == null ? null : TABLE.get(role);
        return row == null ? Grant.NONE : row.get(permission);
    }

    /** Índice em teacher_permissions quando a célula do Professor tem asterisco; -1 se não depende da unidade. */
    public static int teacherFlag(Permission permission) {
        return TEACHER_FLAG.getOrDefault(permission, -1);
    }
}
