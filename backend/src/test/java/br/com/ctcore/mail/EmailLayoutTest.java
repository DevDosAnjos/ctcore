package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailLayoutTest {
    @Test
    void buttonOnlyWithLink() {
        assertThat(EmailLayout.html("oi", "https://a/b", "Abrir")).contains("max-width:600px", "font-size:18px", "width:100%", "href=\"https://a/b\"");
        assertThat(EmailLayout.html("oi", null, "Abrir")).doesNotContain("<a ").contains("oi");
    }

    @Test
    @DisplayName("CA20: a cor da organização vai no botão, o rodapé traz feito com ctcore e sem cor vale #1a56db")
    void brandColorAndFooter() {
        assertThat(EmailLayout.html("oi", "https://a/b", "Abrir", "#0b6e4f")).contains("background:#0b6e4f").doesNotContain("#1a56db").contains("feito com ctcore");
        assertThat(EmailLayout.html("oi", "https://a/b", "Abrir")).contains("background:#1a56db").contains("feito com ctcore");
        assertThat(EmailLayout.html("oi", null, "Abrir", "#0b6e4f")).contains("feito com ctcore");
    }

    @Test
    @DisplayName("CA20: cor fora do formato não entra no estilo e o texto continua escapado pelo chamador")
    void unsafeColorIsIgnored() {
        assertThat(EmailLayout.html("&lt;b&gt;", "https://a/b", "Abrir", "red;\"onload=x")).contains("background:#1a56db").doesNotContain("onload").contains("&lt;b&gt;");
    }
}
