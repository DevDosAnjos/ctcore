package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailLayoutTest {
    @Test
    void buttonOnlyWithLink() {
        assertThat(EmailLayout.html("oi", "https://a/b", "Abrir")).contains("max-width:600px", "font-size:18px", "width:100%", "href=\"https://a/b\"");
        assertThat(EmailLayout.html("oi", null, "Abrir")).doesNotContain("<a ").contains("oi");
    }
}
