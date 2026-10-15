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
    @DisplayName("CA2 / RNF-11: CPF no fim da frase, Authorization de qualquer esquema, cookie e CVV são trocados")
    void masksHeadersAndEdges() {
        String[][] cases = {
            {"Aluno com CPF 123.456.789-09.", "123.456.789-09"},
            {"cpf=12345678909.", "12345678909"},
            {"Authorization: Basic dXNlcjpwYXNzd29yZA==", "dXNlcjpwYXNzd29yZA=="},
            {"Proxy-Authorization: Basic YWJjOmRlZg==", "YWJjOmRlZg=="},
            {"{\"Authorization\":\"Basic YWJjOmRlZg==\"}", "YWJjOmRlZg=="},
            {"Cookie: SESSION=CookieSegredo; XSRF-TOKEN=XsrfSegredo", "CookieSegredo", "XsrfSegredo"},
            {"Set-Cookie: SESSION=CookieSegredo; Path=/; HttpOnly", "CookieSegredo"},
            {"SESSION=CookieSegredo; XSRF-TOKEN=XsrfSegredo", "CookieSegredo", "XsrfSegredo"},
            {"/api/v1/x;jsessionid=CookieSegredo", "CookieSegredo"},
            {"cartao cvv=123 cvc: 4567", "123", "4567"},
        };
        for (String[] c : cases) {
            String out = SensitiveDataMasker.mask(c[0]);
            for (int i = 1; i < c.length; i++) {
                assertThat(out).as(c[0]).doesNotContain(c[i]);
            }
            assertThat(out).as(c[0]).contains(SensitiveDataMasker.MASK);
        }
        assertThat(SensitiveDataMasker.mask("Authorization: Basic dXNl")).isEqualTo("Authorization: [REDACTED]");
        assertThat(SensitiveDataMasker.mask("CPF 123.456.789-09.")).isEqualTo("CPF [REDACTED].");
    }

    @Test
    @DisplayName("CA3: id curto, UUID, centavos, versão, data e número maior que CPF saem intactos")
    void keepsLegit() {
        for (String in : new String[] {
            "id=12345 ref=550e8400-e29b-41d4-a716-446655440000 valor=15000 centavos org 7",
            "versão 1.2.3. total R$ 1.250,00 em 2026-10-08 com cookies=2 e tokens=5",
            "lote 123.456.789-09.5 inválido"}) {
            assertThat(SensitiveDataMasker.mask(in)).isEqualTo(in);
        }
    }
}
