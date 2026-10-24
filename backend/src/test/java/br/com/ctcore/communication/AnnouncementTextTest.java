package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-COM-05, R10 (#82): validação do texto e forma simples do e-mail. */
class AnnouncementTextTest {
    @Test
    @DisplayName("CA10: vazio, acima de 2.000 e link que não é http(s) são recusados no campo body")
    void invalid() {
        assertThatThrownBy(() -> AnnouncementText.validate(" ")).isInstanceOf(ApiValidationException.class).hasMessageContaining("body");
        assertThatThrownBy(() -> AnnouncementText.validate("x".repeat(2001))).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> AnnouncementText.validate("[clique](javascript:alert(1))")).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> AnnouncementText.validate("[x](JAVASCRIPT:alert(1))")).isInstanceOf(ApiValidationException.class);
        assertThat(AnnouncementText.validate("x".repeat(2000))).hasSize(2000);
    }

    @Test
    @DisplayName("CA15: negrito, lista e link https passam como vieram; a forma simples não tem os sinais e mantém < e & para o escape do e-mail")
    void plain() {
        String body = "**Atenção** <b> & mais\n- item 1\n- item 2\nVeja [o site](https://exemplo.test/a?b=1&c=2).";
        assertThat(AnnouncementText.validate(body)).isEqualTo(body);
        assertThat(AnnouncementText.plain(body)).isEqualTo("Atenção <b> & mais\n- item 1\n- item 2\nVeja o site (https://exemplo.test/a?b=1&c=2).");
    }
}
