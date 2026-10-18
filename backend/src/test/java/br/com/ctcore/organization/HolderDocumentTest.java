package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HolderDocumentTest {
    @Test
    @DisplayName("CA6: CPF e CNPJ válidos, com ou sem máscara, ficam só com dígitos; vazio vira nulo")
    void accepts() {
        assertThat(HolderDocument.normalize("529.982.247-25")).isEqualTo("52998224725");
        assertThat(HolderDocument.normalize("52998224725")).isEqualTo("52998224725");
        assertThat(HolderDocument.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(HolderDocument.normalize("11222333000181")).isEqualTo("11222333000181");
        assertThat(HolderDocument.normalize(" ")).isNull();
        assertThat(HolderDocument.normalize(null)).isNull();
    }

    @Test
    @DisplayName("CA6: dígito verificador errado, tamanho errado, repetidos e letras são recusados")
    void refuses() {
        for (String bad : new String[] {"529.982.247-26", "11.222.333/0001-82", "123", "111.111.111-11", "11111111111111",
                "5299822472a", "529982247250", "abc"}) {
            assertThatThrownBy(() -> HolderDocument.normalize(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
