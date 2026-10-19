package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.InviteRules.Inviter;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InviteRulesTest {
    UUID mine = UUID.randomUUID(), other = UUID.randomUUID();
    Inviter coordinator = new Inviter(UUID.randomUUID(), Role.COORDINATOR, Set.of());
    Inviter admin = new Inviter(UUID.randomUUID(), Role.ADMINISTRATOR, Set.of(mine));
    Inviter teacher = new Inviter(UUID.randomUUID(), Role.TEACHER, Set.of(mine));

    @Test
    @DisplayName("RN-ACE-05: CA3: cascata Coordenador, Administrador, Professor e sistema")
    void cascade() {
        assertThat(InviteRules.allowed(coordinator, Role.ADMINISTRATOR, Set.of())).isTrue();
        assertThat(InviteRules.allowed(coordinator, Role.COORDINATOR, Set.of())).isFalse();
        assertThat(InviteRules.allowed(coordinator, Role.STUDENT, Set.of())).isFalse();
        assertThat(InviteRules.allowed(admin, Role.TEACHER, Set.of(mine))).isTrue();
        assertThat(InviteRules.allowed(admin, Role.TEACHER, Set.of(mine, other))).isFalse();
        assertThat(InviteRules.allowed(admin, Role.TEACHER, Set.of(other))).isFalse();
        assertThat(InviteRules.allowed(admin, Role.TEACHER, Set.of())).isFalse();
        assertThat(InviteRules.allowed(admin, Role.ADMINISTRATOR, Set.of(mine))).isFalse();
        assertThat(InviteRules.allowed(teacher, Role.TEACHER, Set.of(mine))).isFalse();
        // RN-PRO-02: dar acesso a professor pelo painel; o Coordenador também, mas só com unidade
        assertThat(InviteRules.teacherAccess(coordinator, Set.of(other))).isTrue();
        assertThat(InviteRules.teacherAccess(coordinator, Set.of())).isFalse();
        assertThat(InviteRules.teacherAccess(admin, Set.of(mine))).isTrue();
        assertThat(InviteRules.teacherAccess(admin, Set.of(mine, other))).isFalse();
        assertThat(InviteRules.teacherAccess(teacher, Set.of(mine))).isFalse();
        assertThat(InviteRules.teacherAccess(Inviter.system(), Set.of(mine))).isFalse();
        assertThat(InviteRules.allowed(Inviter.system(), Role.STUDENT, Set.of())).isTrue();
        assertThat(InviteRules.allowed(Inviter.system(), Role.GUARDIAN, Set.of())).isTrue();
        assertThat(InviteRules.allowed(Inviter.system(), Role.TEACHER, Set.of(mine))).isFalse();
    }

    @Test
    @DisplayName("RN-ACE-06: CA4: 17 anos e 364 dias é menor; 18 anos completos é maior")
    void adulthood() {
        LocalDate today = LocalDate.of(2026, 10, 17);
        assertThat(InviteRules.adult(LocalDate.of(2008, 10, 18), today)).isFalse();
        assertThat(InviteRules.adult(LocalDate.of(2008, 10, 17), today)).isTrue();
        assertThat(InviteRules.adult(null, today)).isFalse();
    }
}
