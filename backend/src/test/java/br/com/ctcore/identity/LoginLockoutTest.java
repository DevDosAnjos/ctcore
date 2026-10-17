package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.LoginAttempts.Verdict;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoginLockoutTest {
    static final Instant T0 = Instant.parse("2026-10-17T12:00:00Z");
    static final Duration WINDOW = Duration.ofMinutes(15);

    static Verdict fail(long minutes) { return new Verdict(T0.plus(Duration.ofMinutes(minutes)), true); }

    @Test
    @DisplayName("RN-ACE-10: CA9: 5 falhas seguidas travam por 15 minutos e destravam depois")
    void locksThenUnlocks() {
        List<Verdict> five = List.of(fail(4), fail(3), fail(2), fail(1), fail(0));
        assertThat(LoginAttempts.emailLocked(five, 5, WINDOW, T0.plus(Duration.ofMinutes(4)))).isTrue();
        assertThat(LoginAttempts.emailLocked(five, 5, WINDOW, T0.plus(Duration.ofMinutes(19)).minusSeconds(1))).isTrue();
        assertThat(LoginAttempts.emailLocked(five, 5, WINDOW, T0.plus(Duration.ofMinutes(19)))).isFalse();
    }

    @Test
    @DisplayName("RN-ACE-10: CA9: 4 falhas, ou um acerto no meio, não travam")
    void notLocked() {
        assertThat(LoginAttempts.emailLocked(List.of(fail(3), fail(2), fail(1), fail(0)), 5, WINDOW, T0)).isFalse();
        List<Verdict> withSuccess = List.of(fail(4), fail(3), new Verdict(T0.plusSeconds(60), false), fail(1), fail(0));
        assertThat(LoginAttempts.emailLocked(withSuccess, 5, WINDOW, T0.plusSeconds(300))).isFalse();
    }

    @Test
    @DisplayName("RN-ACE-10: CA9: falhas espalhadas por mais de 15 minutos, como a 1ª depois do destravamento, não travam")
    void spreadFailuresDoNotLock() {
        List<Verdict> spread = List.of(fail(20), fail(4), fail(3), fail(2), fail(1));
        assertThat(LoginAttempts.emailLocked(spread, 5, WINDOW, T0.plus(Duration.ofMinutes(20)))).isFalse();
    }
}
