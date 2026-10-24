package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-COM-12 (#83): catálogo, validação de campos automáticos e preenchimento em uma passada, sem banco. */
class TextModelsTest {
    @Test
    @DisplayName("CA1: o catálogo tem 9 modelos e os textos da plataforma só usam campos que o próprio modelo aceita")
    void catalogAndPlatformTexts() throws Exception {
        assertThat(TextModel.values()).hasSize(9);
        var messages = new Properties();
        try (var in = new InputStreamReader(getClass().getResourceAsStream("/messages.properties"), StandardCharsets.UTF_8)) {
            messages.load(in);
        }
        for (TextModel m : TextModel.values()) {
            String subject = messages.getProperty("text." + m.name() + ".subject");
            String body = messages.getProperty("text." + m.name() + ".body");
            assertThat(subject).as(m.name()).isNotBlank();
            assertThat(body).as(m.name()).isNotNull();
            TextModels.validate(m, "subject", subject, 150);
            if (!body.isEmpty()) TextModels.validate(m, "body", body, 2000);
        }
    }

    @Test
    @DisplayName("CA4: campo desconhecido, campo de outro modelo, chave sem fecho, vazio e tamanho dão 400 no campo")
    void validation() {
        TextModels.validate(TextModel.REMINDER_DUE, "body", "Olá {nome}, {valor} vence {vencimento} ({unidade}) {descricao} {link_pagamento}", 2000);
        assertField(TextModel.REMINDER_DUE, "body", "Seu CPF {cpf}", 2000);
        assertField(TextModel.WELCOME, "body", "Valor {valor}", 2000);
        assertField(TextModel.WELCOME, "body", "Olá {nome", 2000);
        assertField(TextModel.WELCOME, "body", "Olá nome}", 2000);
        assertField(TextModel.WELCOME, "body", "Olá {{nome}}", 2000);
        assertField(TextModel.WELCOME, "subject", "", 150);
        assertField(TextModel.WELCOME, "subject", "x".repeat(151), 150);
        assertField(TextModel.WELCOME, "body", "x".repeat(2001), 2000);
        TextModels.validate(TextModel.WELCOME, "subject", "x".repeat(150), 150);
        TextModels.validate(TextModel.WELCOME, "body", "x".repeat(2000), 2000);
    }

    @Test
    @DisplayName("CA7: o preenchimento é em uma passada: o valor de um campo não é lido como modelo e campo sem valor fica como está")
    void fill() {
        assertThat(TextModels.fill("Olá, {nome}: {valor}. {link_pagamento}", Map.of("nome", "Maria {valor}", "valor", "R$ 150,00", "link_pagamento", "")))
                .isEqualTo("Olá, Maria {valor}: R$ 150,00. ");
        assertThat(TextModels.fill("{turma} $1 \\ {x}", Map.of("turma", "A$1\\"))).isEqualTo("A$1\\ $1 \\ {x}");
    }

    @Test
    @DisplayName("Só os lembretes de cobrança têm modelo; o aviso de bloqueio não")
    void reminderMapping() {
        assertThat(TextModel.reminder(EmailTemplate.REMINDER_BEFORE)).isEqualTo(TextModel.REMINDER_BEFORE);
        assertThat(TextModel.reminder(EmailTemplate.REMINDER_DUE)).isEqualTo(TextModel.REMINDER_DUE);
        assertThat(TextModel.reminder(EmailTemplate.REMINDER_OVERDUE)).isEqualTo(TextModel.REMINDER_OVERDUE);
        assertThat(TextModel.reminder(EmailTemplate.BLOCK_WARNING)).isNull();
        assertThat(TextModel.reminder(EmailTemplate.PASSWORD_RESET)).isNull();
    }

    private static void assertField(TextModel model, String field, String text, int max) {
        assertThatThrownBy(() -> TextModels.validate(model, field, text, max)).isInstanceOf(ApiValidationException.class).hasMessageContaining(field);
    }
}
