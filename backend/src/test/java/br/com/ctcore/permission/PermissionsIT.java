package br.com.ctcore.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.Organizations;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

class PermissionsIT extends PostgresIT {
    @Autowired Permissions permissions;
    @Autowired Organizations organizations;

    record Org(UUID id, UUID unit) {}

    Org org() {
        UUID id = UUID.randomUUID();
        var input = new Organizations.InitialOrganization(id, "Synthetic", UUID.randomUUID(), "Unit", id.toString());
        organizations.initialize(input, Organizations.Defaults.standard());
        return new Org(id, input.unitId());
    }

    UUID extraUnit(Org o) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", id, o.id(), "u-" + id);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", o.id(), id);
        return id;
    }

    static AccountPrincipal who(Org o, Role role, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), o.id(), role, Set.of(units));
    }

    void flags(UUID unit, String value) {
        adminJdbc().update("update unit_settings set teacher_permissions = ?::boolean[] where unit_id = ?", value, unit);
    }

    @Test
    @DisplayName("CA2 / RN-ACE-05: Administrador só pode nas unidades do vínculo, mesmo na mesma organização")
    void administratorOnlyInOwnUnits() {
        Org o = org();
        UUID u2 = extraUnit(o), u3 = extraUnit(o);
        var admin = who(o, Role.ADMINISTRATOR, o.unit(), u2);
        assertThat(permissions.grant(admin, Permission.COLLECTIONS, o.unit())).isEqualTo(Access.FULL);
        assertThat(permissions.grant(admin, Permission.COLLECTIONS, u3)).isEqualTo(Access.NONE);
        assertThat(permissions.grant(admin, Permission.COLLECTIONS, null)).isEqualTo(Access.NONE);
        assertThat(permissions.grant(admin, Permission.ORGANIZATION_SETTINGS, null)).isEqualTo(Access.READ);
        assertThat(permissions.grant(admin, Permission.UNITS, o.unit())).isEqualTo(Access.NONE);
        assertThatThrownBy(() -> permissions.require(admin, Permission.COLLECTIONS, u3)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CA3 / RN-ACE-05: Coordenador pode em qualquer unidade da organização, e não em unidade de outra")
    void coordinatorAnyUnitOfOwnOrganization() {
        Org a = org(), b = org();
        UUID extra = extraUnit(a);
        var coordinator = who(a, Role.COORDINATOR);
        assertThat(permissions.grant(coordinator, Permission.COLLECTIONS, a.unit())).isEqualTo(Access.FULL);
        assertThat(permissions.grant(coordinator, Permission.COLLECTIONS, extra)).isEqualTo(Access.FULL);
        assertThat(permissions.grant(coordinator, Permission.COLLECTIONS, null)).isEqualTo(Access.FULL);
        assertThat(permissions.grant(coordinator, Permission.COLLECTIONS, b.unit())).isEqualTo(Access.NONE);
        assertThat(permissions.grant(coordinator, Permission.ALL_UNITS_CONSOLIDATED, null)).isEqualTo(Access.READ);
    }

    @Test
    @DisplayName("CA4: as cinco permissões do Professor são independentes e desligada vale NONE")
    void teacherFlagsAreIndependent() {
        Org o = org();
        var teacher = who(o, Role.TEACHER, o.unit());
        Permission[] star = {Permission.ANNOUNCEMENTS, Permission.AGENDA_EDIT, Permission.STUDENT_CONTACTS, Permission.TRIAL_CLASSES, Permission.STUDENT_RECORD};
        for (int on = 0; on < 5; on++) {
            StringBuilder v = new StringBuilder("{");
            for (int i = 0; i < 5; i++) v.append(i == 0 ? "" : ",").append(i == on);
            flags(o.unit(), v.append("}").toString());
            for (int i = 0; i < 5; i++) {
                assertThat(permissions.grant(teacher, star[i], o.unit())).isEqualTo(i == on ? PermissionTable.grant(Role.TEACHER, star[i]).access() : Access.NONE);
            }
        }
        flags(o.unit(), "{true,true,true,true,false}");
        assertThat(permissions.grant(teacher, Permission.STUDENT_CONTACTS, o.unit())).isEqualTo(Access.READ);
        assertThat(permissions.grant(teacher, Permission.COLLECTIONS, o.unit())).isEqualTo(Access.NONE);
    }

    @Test
    @DisplayName("CA5: Professor com U1 ligada e U2 desligada: require na U2 dá 403, na U1 passa")
    void teacherFlagPerUnit() {
        Org o = org();
        UUID u2 = extraUnit(o);
        flags(o.unit(), "{true,true,true,true,false}");
        flags(u2, "{true,true,false,true,false}");
        var teacher = who(o, Role.TEACHER, o.unit(), u2);
        permissions.require(teacher, Permission.STUDENT_CONTACTS, o.unit());
        assertThatThrownBy(() -> permissions.require(teacher, Permission.STUDENT_CONTACTS, u2)).isInstanceOf(AccessDeniedException.class);
        permissions.require(teacher, Permission.AGENDA_EDIT, u2);
    }

    @Test
    @DisplayName("CA8: efetivas do Professor: ligada em ao menos uma unidade vale; desligada em todas é NONE")
    void effectiveTeacher() {
        Org o = org();
        UUID u2 = extraUnit(o);
        flags(o.unit(), "{true,false,false,false,false}");
        flags(u2, "{false,false,true,false,false}");
        var eff = permissions.effective(who(o, Role.TEACHER, o.unit(), u2));
        assertThat(eff.get(Permission.ANNOUNCEMENTS)).isEqualTo(Access.FULL);
        assertThat(eff.get(Permission.STUDENT_CONTACTS)).isEqualTo(Access.READ);
        assertThat(eff.get(Permission.AGENDA_EDIT)).isEqualTo(Access.NONE);
        assertThat(eff.get(Permission.TRIAL_CLASSES)).isEqualTo(Access.NONE);
        assertThat(eff.get(Permission.REPORTS)).isEqualTo(Access.OWN);
        assertThat(eff.get(Permission.COLLECTIONS)).isEqualTo(Access.NONE);
        assertThat(eff).hasSize(Permission.values().length);
    }

    @Test
    @DisplayName("R6: o Professor não lê configuração de unidade de outra organização, mesmo com a unidade no vínculo")
    void otherOrganizationUnitIsInvisible() {
        Org a = org(), b = org();
        flags(b.unit(), "{true,true,true,true,false}");
        var forged = who(a, Role.TEACHER, b.unit());
        assertThat(permissions.grant(forged, Permission.STUDENT_CONTACTS, b.unit())).isEqualTo(Access.NONE);
        assertThat(permissions.effective(forged).get(Permission.STUDENT_CONTACTS)).isEqualTo(Access.NONE);
    }

    @Test
    @DisplayName("Portal, sem papel e sem vínculo ativo: tudo NONE")
    void portalAndNoLink() {
        Org o = org();
        for (AccountPrincipal p : new AccountPrincipal[] {who(o, Role.STUDENT), who(o, Role.GUARDIAN), who(o, null),
                new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), null, null, null, Set.of()), null}) {
            assertThat(permissions.effective(p).values()).containsOnly(Access.NONE);
            assertThat(permissions.grant(p, Permission.HOME, null)).isEqualTo(Access.NONE);
        }
    }
}
