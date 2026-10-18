package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PixKeyTest {
    @Test
    @DisplayName("CA11: CPF, CNPJ, e-mail, telefone e UUID válidos ficam normalizados; vazio vira nulo")
    void accepts() {
        assertThat(PixKey.normalize("529.982.247-25")).isEqualTo("52998224725");
        assertThat(PixKey.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(PixKey.normalize(" Ana@Exemplo.com ")).isEqualTo("ana@exemplo.com");
        assertThat(PixKey.normalize("+55 (21) 99999-8888")).isEqualTo("+5521999998888");
        assertThat(PixKey.normalize("123E4567-E89B-12D3-A456-426614174000")).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThat(PixKey.normalize("  ")).isNull();
        assertThat(PixKey.normalize(null)).isNull();
    }

    @Test
    @DisplayName("CA11: chave fora dos cinco tipos é recusada")
    void refuses() {
        for (String bad : new String[] {"529.982.247-26", "ruim@", "+5521", "21999998888x", "123e4567-e89b-12d3-a456-42661417400", "abc", "+4421999998888"}) {
            assertThatThrownBy(() -> PixKey.normalize(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
