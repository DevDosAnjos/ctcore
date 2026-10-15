package br.com.ctcore.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SensitiveDataMaskerTest {

    @Test
    @DisplayName("CA2 / RNF-11: senha, token, Bearer, CPF e cartão são trocados por [REDACTED]")
    void masksSensitive() {
        String[] secrets = {"Senha123", "abc.def-ghi", "JwtValor.x.y", "123.456.789-09", "12345678909",
            "4111 1111 1111 1111", "4111-1111-1111-1111", "4111111111111111"};
        String in = "login password=Senha123 token=abc.def-ghi header Bearer JwtValor.x.y "
                + "cpf 123.456.789-09 e 12345678909 cartao 4111 1111 1111 1111 / 4111-1111-1111-1111 / 4111111111111111"
                + " json {\"password\":\"Senha123\"}";
        String out = SensitiveDataMasker.mask(in);
        for (String s : secrets) {
            assertThat(out).doesNotContain(s);
        }
        assertThat(out).contains("password=[REDACTED]", "token=[REDACTED]", "Bearer [REDACTED]");
    }

    @Test
    @DisplayName("CA3: id curto, UUID e centavos saem intactos")
    void keepsLegit() {
        String in = "id=12345 ref=550e8400-e29b-41d4-a716-446655440000 valor=15000 centavos org 7";
        assertThat(SensitiveDataMasker.mask(in)).isEqualTo(in);
    }
}
