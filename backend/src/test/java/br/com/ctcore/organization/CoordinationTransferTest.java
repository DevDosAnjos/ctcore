package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoordinationTransferTest {
    @Test
    @DisplayName("RNF-26 / CA4: aceita TRANSFERIR sem diferenciar maiúsculas nem espaços nas pontas; recusa o resto")
    void confirmation() {
        assertThatCode(() -> CoordinationTransfer.requireConfirmation("  transferir ")).doesNotThrowAnyException();
        for (String bad : new String[] {null, "", "  ", "transfere", "transferir já"}) {
            assertThatThrownBy(() -> CoordinationTransfer.requireConfirmation(bad)).isInstanceOf(ApiValidationException.class)
                    .satisfies(e -> assertThat(((ApiValidationException) e).fields.get(0).field()).isEqualTo("confirmation"));
        }
    }
}
