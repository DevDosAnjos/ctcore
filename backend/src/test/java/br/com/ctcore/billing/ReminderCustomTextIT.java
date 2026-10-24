package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.integration.Outbox;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-COM-12 (#83): o lembrete usa o texto próprio da organização, preenchido e escapado; sem texto próprio sai como antes; uma organização não usa o texto da outra. */
class ReminderCustomTextIT extends ReminderBase {
    @Autowired Outbox outbox;

    @BeforeEach
    void clearOutbox() {
        outbox.clear();
    }

    @AfterEach
    void cleanTexts() {
        adminJdbc().update("delete from text_model");
        adminJdbc().update("delete from account where email like 'texts-rem-%'");
    }

    private void custom(Tenant t, String key, String subject, String body) {
        UUID by = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", by, "texts-rem-" + by + "@example.com");
        adminJdbc().update("insert into text_model (id, organization_id, key, subject, body, updated_by_account_id) values (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), t.orgId(), key, subject, body, by);
    }

    private Outbox.Message sent(String to) {
        return outbox.list().stream().filter(m -> m.type() == Outbox.Type.EMAIL && m.recipient().equals(to)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("CA7: o texto próprio sai com nome, descrição, valor, data e unidade; {link_pagamento} sai vazio; o nome com <b> sai escapado")
    void customText() {
        var t = tenant();
        custom(t, "REMINDER_BEFORE", "Olá, {nome}", "Olá, {nome}: {descricao} de {valor} vence em {vencimento} ({unidade}). {link_pagamento}");
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria <b>Souza");
        adminJdbc().update("update student set email = 'rem-custom-a@example.com' where id = ?", s);
        charge(t, s, "2026-10-08", "OPEN");
        assertThat(job().runOnce()).isEqualTo(1);
        var m = sent("rem-custom-a@example.com");
        assertThat(m.subject()).isEqualTo("Olá, Maria <b>Souza");
        assertThat(m.content()).contains("Olá, Maria &lt;b&gt;Souza: ", "150,00", "vence em 08/10/2026 (Unit).").doesNotContain("<b>").doesNotContain("{");
        assertThat(m.content()).doesNotContain("http");
        assertThat(mails("rem-custom-a%")).singleElement().satisfies(r -> assertThat(r).containsEntry("template", "REMINDER_BEFORE").containsEntry("status", "SENT"));
    }

    @Test
    @DisplayName("CA8, CA9: sem texto próprio o e-mail sai com o texto de antes, mesmo que outra organização tenha o dela")
    void platformText() {
        var a = tenant();
        var b = tenant();
        custom(a, "REMINDER_BEFORE", "Assunto de A", "Texto de A {nome}");
        UUID s = adult(b, "rem-custom-b@example.com");
        charge(b, s, "2026-10-08", "OPEN");
        assertThat(job().runOnce()).isEqualTo(1);
        var m = sent("rem-custom-b@example.com");
        assertThat(m.subject()).isEqualTo("Lembrete: sua cobrança vence em breve");
        assertThat(m.content()).contains("Lembrete de cobrança: ", ", no valor de ", ", com vencimento em 08/10/2026.").doesNotContain("Texto de A");
    }
}
