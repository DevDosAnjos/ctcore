package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BrandColorTest {
    @Test
    @DisplayName("CA7: razão de contraste com valores de referência (preto 21:1, branco 1:1)")
    void contrastReferences() {
        assertThat(BrandColor.contrastWithWhite("#000000")).isCloseTo(21.0, within(0.001));
        assertThat(BrandColor.contrastWithWhite("#ffffff")).isCloseTo(1.0, within(0.001));
        assertThat(BrandColor.contrastWithWhite("#767676")).isCloseTo(4.54, within(0.01)); // cinza AA mais claro conhecido
        assertThat(BrandColor.contrastWithWhite("#777777")).isLessThan(BrandColor.MIN_CONTRAST);
    }

    @Test
    @DisplayName("CA7: aceita #1a56db, normaliza para minúsculas e aceita vazio como nulo")
    void accepts() {
        assertThat(BrandColor.normalize("#1A56DB")).isEqualTo("#1a56db");
        assertThat(BrandColor.normalize("  ")).isNull();
        assertThat(BrandColor.normalize(null)).isNull();
    }

    @Test
    @DisplayName("CA7: recusa contraste baixo, formato inválido e falta do #")
    void refuses() {
        for (String bad : new String[] {"#ffeb3b", "azul", "#fff", "1a56db", "#1a56dg", "#1a56db0"}) {
            assertThatThrownBy(() -> BrandColor.normalize(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
