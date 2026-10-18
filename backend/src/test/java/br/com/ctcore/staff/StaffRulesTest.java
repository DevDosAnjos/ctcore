package br.com.ctcore.staff;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaffRulesTest {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3), D = new UUID(0, 4);

    @Test
    @DisplayName("CA2 / CA16: quem gere quem e canManage só com todas as unidades do alvo no alcance")
    void manage() {
        assertThat(StaffRules.managedRole(Role.COORDINATOR)).isEqualTo(Role.ADMINISTRATOR);
        assertThat(StaffRules.managedRole(Role.ADMINISTRATOR)).isEqualTo(Role.TEACHER);
        assertThat(StaffRules.managedRole(Role.TEACHER)).isNull();
        assertThat(StaffRules.managedRole(Role.STUDENT)).isNull();
        assertThat(StaffRules.reaches(Role.COORDINATOR, Set.of(), Role.TEACHER, Set.of(A))).isFalse();
        assertThat(StaffRules.reaches(Role.COORDINATOR, Set.of(), Role.ADMINISTRATOR, Set.of())).isTrue();
        assertThat(StaffRules.reaches(Role.ADMINISTRATOR, Set.of(A), Role.ADMINISTRATOR, Set.of(A))).isFalse();
        assertThat(StaffRules.reaches(Role.ADMINISTRATOR, Set.of(A), Role.TEACHER, Set.of(A, B))).isTrue();
        assertThat(StaffRules.reaches(Role.ADMINISTRATOR, Set.of(A), Role.TEACHER, Set.of(C))).isFalse();
        assertThat(StaffRules.canManage(Role.ADMINISTRATOR, Set.of(A), Role.TEACHER, Set.of(A))).isTrue();
        assertThat(StaffRules.canManage(Role.ADMINISTRATOR, Set.of(A), Role.TEACHER, Set.of(A, B))).isFalse();
        assertThat(StaffRules.canManage(Role.COORDINATOR, Set.of(), Role.ADMINISTRATOR, Set.of(A, B))).isTrue();
    }

    @Test
    @DisplayName("CA11: o Administrador preserva as unidades do Professor fora do alcance dele")
    void merge() {
        Set<UUID> mine = Set.of(A, D);
        assertThat(StaffRules.mergeUnits(Role.ADMINISTRATOR, mine, Set.of(A, B), Set.of(A, D))).containsExactlyInAnyOrder(A, B, D);
        assertThat(StaffRules.mergeUnits(Role.ADMINISTRATOR, mine, Set.of(A, B), Set.of(D))).containsExactlyInAnyOrder(B, D);
        assertThat(StaffRules.mergeUnits(Role.ADMINISTRATOR, mine, Set.of(A), Set.of())).isEmpty();
        assertThat(StaffRules.mergeUnits(Role.COORDINATOR, Set.of(), Set.of(A, B), Set.of(A, C))).containsExactlyInAnyOrder(A, C);
    }

    @Test
    @DisplayName("CA12: unidade sem outro Administrador ativo fica sem Administrador")
    void lastAdministrator() {
        assertThat(StaffRules.leftWithoutAdministrator(Set.of(A, B), Map.of(B, 1))).containsExactly(A);
        assertThat(StaffRules.leftWithoutAdministrator(Set.of(A), Map.of(A, 2))).isEmpty();
        assertThat(StaffRules.removed(Set.of(A, B), Set.of(B, C))).containsExactly(A);
    }
}
