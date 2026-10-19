package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LessonWriteTest {
    @Test
    @DisplayName("CA7, RN-AGE-06: com experimental marcada a decisão é obrigatória (422); sem experimental ou com decisão, passa")
    void trialDecision() {
        assertThatThrownBy(() -> LessonWrite.requireTrialDecision(2, null))
                .isInstanceOfSatisfying(ApiStatusException.class, e -> assertThat(e.code).isEqualTo(ErrorCode.UNPROCESSABLE));
        assertThatCode(() -> LessonWrite.requireTrialDecision(2, "REBOOK")).doesNotThrowAnyException();
        assertThatCode(() -> LessonWrite.requireTrialDecision(2, "CANCEL_TRIALS")).doesNotThrowAnyException();
        assertThatCode(() -> LessonWrite.requireTrialDecision(0, null)).doesNotThrowAnyException();
    }
}
