package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShortNameTest {
    @Test
    @DisplayName("CA7: gera do nome sem acento, em minúsculas, com hífen; colisão ganha sufixo numérico")
    void generate() {
        assertThat(ShortName.generate("Academia Leão — Tijuca", s -> false)).isEqualTo("academia-leao-tijuca");
        assertThat(ShortName.generate("Academia Leão — Tijuca", Set.of("academia-leao-tijuca")::contains)).isEqualTo("academia-leao-tijuca-2");
        assertThat(ShortName.generate("Academia Leão — Tijuca", Set.of("academia-leao-tijuca", "academia-leao-tijuca-2")::contains))
                .isEqualTo("academia-leao-tijuca-3");
        assertThat(ShortName.generate("  !!! ", s -> false)).isEqualTo("unidade");
        assertThat(ShortName.generate("x".repeat(200), s -> false)).hasSizeLessThanOrEqualTo(ShortName.MAX - 5);
        assertThat(ShortName.validate(ShortName.generate("Ção & Cia. 2ª Unidade", s -> false))).isEqualTo("cao-cia-2-unidade");
    }

    @Test
    @DisplayName("CA10: formato inválido (vazio, maiúscula, espaço, acento, hífen nas pontas, mais de 60) é recusado")
    void validate() {
        assertThat(ShortName.validate("tijuca-2")).isEqualTo("tijuca-2");
        assertThat(ShortName.validate("a".repeat(60))).hasSize(60);
        for (String bad : new String[] {null, "", "Tijuca", "a b", "ação", "-a", "a-", "a--b", "a_b", "a".repeat(61)}) {
            assertThatThrownBy(() -> ShortName.validate(bad)).as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
