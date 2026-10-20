package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-AGE-03 e RN-AGE-08, sem banco. */
class AgendaRulesTest {
    static final LocalDate DAY = LocalDate.of(2026, 3, 10);
    static final LocalDateTime START = DAY.atTime(19, 0);
    static final LocalDateTime END = DAY.atTime(20, 0);

    @Test
    @DisplayName("CA6: 18:59 agendada, 19:00 e 19:59 em andamento, 20:00 concluída; cancelada vale sempre")
    void status() {
        assertThat(Agenda.status(false, START, END, DAY.atTime(18, 59))).isEqualTo(Agenda.Status.SCHEDULED);
        assertThat(Agenda.status(false, START, END, DAY.atTime(19, 0))).isEqualTo(Agenda.Status.IN_PROGRESS);
        assertThat(Agenda.status(false, START, END, DAY.atTime(19, 59))).isEqualTo(Agenda.Status.IN_PROGRESS);
        assertThat(Agenda.status(false, START, END, DAY.atTime(20, 0))).isEqualTo(Agenda.Status.COMPLETED);
        for (LocalTime t : new LocalTime[] {LocalTime.of(10, 0), LocalTime.of(19, 30), LocalTime.of(23, 0)}) {
            assertThat(Agenda.status(true, START, END, DAY.atTime(t))).isEqualTo(Agenda.Status.CANCELLED);
        }
    }

    @Test
    @DisplayName("CA6: o mesmo instante dá situação diferente em fusos diferentes")
    void timezone() {
        var instant = java.time.Instant.parse("2026-03-10T22:00:00Z"); // 19:00 em São Paulo, 18:00 em Manaus... e 07:00 do dia seguinte em Tóquio
        var sp = LocalDateTime.ofInstant(instant, java.time.ZoneId.of("America/Sao_Paulo"));
        var tokyo = LocalDateTime.ofInstant(instant, java.time.ZoneId.of("Asia/Tokyo"));
        assertThat(Agenda.status(false, START, END, sp)).isEqualTo(Agenda.Status.IN_PROGRESS);
        assertThat(Agenda.status(false, START, END, tokyo)).isEqualTo(Agenda.Status.COMPLETED);
    }

    private static Agenda.Can can(Role role, boolean permission, boolean mine, Agenda.Status status, LocalDateTime now) {
        return Agenda.can(role, permission, mine, status, END, now);
    }

    @Test
    @DisplayName("CA8: concluída nunca cancela; editar concluída só Administrador e Coordenador até 30 dias; cancelada não edita")
    void managementRules() {
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR}) {
            assertThat(can(role, true, false, Agenda.Status.SCHEDULED, START.minusDays(1))).isEqualTo(new Agenda.Can(true, true));
            assertThat(can(role, true, false, Agenda.Status.IN_PROGRESS, START)).isEqualTo(new Agenda.Can(true, true));
            assertThat(can(role, true, false, Agenda.Status.COMPLETED, END.plusDays(5))).isEqualTo(new Agenda.Can(true, false));
            assertThat(can(role, true, false, Agenda.Status.COMPLETED, END.plusDays(30))).isEqualTo(new Agenda.Can(true, false));
            assertThat(can(role, true, false, Agenda.Status.COMPLETED, END.plusDays(30).plusSeconds(1))).isEqualTo(new Agenda.Can(false, false));
            assertThat(can(role, true, false, Agenda.Status.CANCELLED, START)).isEqualTo(new Agenda.Can(false, false));
        }
    }

    @Test
    @DisplayName("CA8: Professor precisa de AGENDA_EDIT e de aula própria; nunca edita concluída")
    void teacherRules() {
        assertThat(can(Role.TEACHER, true, true, Agenda.Status.SCHEDULED, START.minusDays(1))).isEqualTo(new Agenda.Can(true, true));
        assertThat(can(Role.TEACHER, false, true, Agenda.Status.SCHEDULED, START.minusDays(1))).isEqualTo(new Agenda.Can(false, false));
        assertThat(can(Role.TEACHER, true, false, Agenda.Status.SCHEDULED, START.minusDays(1))).isEqualTo(new Agenda.Can(false, false));
        assertThat(can(Role.TEACHER, true, true, Agenda.Status.COMPLETED, END.plusDays(1))).isEqualTo(new Agenda.Can(false, false));
        assertThat(can(Role.TEACHER, true, true, Agenda.Status.CANCELLED, START)).isEqualTo(new Agenda.Can(false, false));
    }

    @Test
    @DisplayName("RN-PRO-06: substituto só no dia da aula, no fuso da unidade")
    void substituteWindow() {
        var sp = java.time.ZoneId.of("America/Sao_Paulo");
        var at = (java.util.function.Function<String, java.time.Instant>) java.time.Instant::parse;
        assertThat(Agenda.substituteWindow(DAY, sp, at.apply("2026-03-10T02:59:59Z"))).isFalse(); // 23:59:59 da véspera
        assertThat(Agenda.substituteWindow(DAY, sp, at.apply("2026-03-10T03:00:00Z"))).isTrue(); // 00:00
        assertThat(Agenda.substituteWindow(DAY, sp, at.apply("2026-03-11T02:59:59Z"))).isTrue(); // 23:59:59
        assertThat(Agenda.substituteWindow(DAY, sp, at.apply("2026-03-11T03:00:00Z"))).isFalse(); // dia seguinte
        var tokyo = java.time.ZoneId.of("Asia/Tokyo");
        assertThat(Agenda.substituteWindow(DAY, tokyo, at.apply("2026-03-09T15:00:00Z"))).isTrue(); // 00:00 em Tóquio
        assertThat(Agenda.substituteWindow(DAY, sp, at.apply("2026-03-09T15:00:00Z"))).isFalse();
    }
}
