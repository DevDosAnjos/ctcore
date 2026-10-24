package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COM-03 (RN-COM-12, #83): catálogo, gravação, validação, volta ao padrão, perfis, isolamento com RLS e auditoria. */
class TextModelsIT extends OrganizationBase {
    private static final String BASE = "/api/v1/organization/texts";
    private static final String SUBJECT = "Sua cobrança, {nome}";
    private static final String BODY = "Olá, {nome}: {descricao} de {valor} vence em {vencimento} ({unidade}). {link_pagamento}";

    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from text_model");
        adminJdbc().update("delete from account where email like 'texts-%@example.com'");
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "texts-" + p.accountId() + "@example.com");
        return p;
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        return call(request, who).getResponse();
    }

    private MockHttpServletResponse put(String key, String subject, String body, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.put(BASE + "/" + key).contentType("application/json").content(json.writeValueAsString(Map.of("subject", subject, "body", body))), who);
    }

    private MockHttpServletResponse delete(String key, AccountPrincipal who) throws Exception {
        return send(MockMvcRequestBuilders.delete(BASE + "/" + key), who);
    }

    private JsonNode view(AccountPrincipal who) throws Exception {
        return json.readTree(send(MockMvcRequestBuilders.get(BASE), who).getContentAsString());
    }

    private JsonNode model(AccountPrincipal who, String key) throws Exception {
        for (JsonNode m : view(who).get("models")) if (m.get("key").asText().equals(key)) return m;
        throw new AssertionError(key);
    }

    private int count(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from text_model where organization_id = ?", Integer.class, t.orgId());
    }

    @Test
    @DisplayName("CA1: sem texto próprio, 9 modelos com texto da plataforma, custom nulo e campos; nada de e-mail de segurança")
    void catalog() throws Exception {
        var c = account(tenant(), Role.COORDINATOR);
        var v = view(c);
        assertThat(v.get("canManage").asBoolean()).isTrue();
        assertThat(v.get("models")).hasSize(9);
        assertThat(v.get("models").findValuesAsText("key")).containsExactly("REMINDER_BEFORE", "REMINDER_DUE", "REMINDER_OVERDUE", "WELCOME",
                "ANNOUNCEMENT_CANCELED_CLASS", "ANNOUNCEMENT_TEACHER_CHANGE", "ANNOUNCEMENT_SCHEDULE_CHANGE", "ANNOUNCEMENT_NO_CLASS_DAY", "ANNOUNCEMENT_GENERAL");
        for (JsonNode m : v.get("models")) {
            assertThat(m.get("platform").get("subject").asText()).isNotBlank();
            assertThat(m.get("custom").isNull()).isTrue();
            assertThat(m.get("fields")).isNotEmpty();
        }
        assertThat(model(c, "REMINDER_DUE").get("group").asText()).isEqualTo("REMINDERS");
        assertThat(model(c, "REMINDER_DUE").get("fields").findValuesAsText("")).isEmpty();
        assertThat(model(c, "REMINDER_DUE").get("platform").get("body").asText()).contains("{descricao}", "{valor}", "{vencimento}");
        assertThat(model(c, "WELCOME").get("fields")).hasSize(2);
    }

    @Test
    @DisplayName("CA2, CA6: grava o texto próprio, o segundo PUT substitui, o GET traz; DELETE volta ao padrão e é idempotente")
    void saveAndReset() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        var r = put("REMINDER_DUE", SUBJECT, BODY, c);
        assertThat(r.getStatus()).isEqualTo(200);
        var saved = json.readTree(r.getContentAsString());
        assertThat(saved.get("custom").get("subject").asText()).isEqualTo(SUBJECT);
        assertThat(saved.get("updatedBy").asText()).isEqualTo("Gestora Sintética");
        assertThat(saved.get("updatedAt").isNull()).isFalse();
        assertThat(model(c, "REMINDER_DUE").get("custom").get("body").asText()).isEqualTo(BODY);
        assertThat(model(c, "REMINDER_BEFORE").get("custom").isNull()).isTrue();
        assertThat(put("REMINDER_DUE", "Outro assunto", "Outro texto de {nome}", c).getStatus()).isEqualTo(200);
        assertThat(count(t)).isEqualTo(1);
        assertThat(model(c, "REMINDER_DUE").get("custom").get("subject").asText()).isEqualTo("Outro assunto");
        assertThat(delete("REMINDER_DUE", c).getStatus()).isEqualTo(204);
        assertThat(model(c, "REMINDER_DUE").get("custom").isNull()).isTrue();
        assertThat(delete("REMINDER_DUE", c).getStatus()).isEqualTo(204);
        assertThat(count(t)).isZero();
    }

    @Test
    @DisplayName("CA3: chave fora do catálogo dá 404 em PUT e DELETE, sem gravar")
    void unknownKey() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        for (String key : new String[] {"PASSWORD_RESET", "NOPE", "reminder_due"}) {
            assertThat(put(key, "Assunto", "Texto", c).getStatus()).as(key).isEqualTo(404);
            assertThat(delete(key, c).getStatus()).as(key).isEqualTo(404);
        }
        assertThat(count(t)).isZero();
    }

    @Test
    @DisplayName("CA4, CA5: entradas inválidas dão 400 no campo certo sem gravar; texto igual ao da plataforma e link javascript: também")
    void validation() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        String[][] bad = {{"REMINDER_DUE", "Assunto", "Seu CPF {cpf}", "body"}, {"WELCOME", "Assunto", "Valor {valor}", "body"}, {"WELCOME", "Assunto", "Olá {nome", "body"},
                {"WELCOME", "", "Olá {nome}", "subject"}, {"WELCOME", "x".repeat(151), "Olá {nome}", "subject"}, {"WELCOME", "Assunto {cpf}", "Olá", "subject"},
                {"WELCOME", "Assunto", "", "body"}, {"WELCOME", "Assunto", "x".repeat(2001), "body"},
                {"ANNOUNCEMENT_GENERAL", "Aviso", "Veja [aqui](javascript:alert(1))", "body"}, {"ANNOUNCEMENT_GENERAL", "x".repeat(121), "Texto", "subject"}};
        for (String[] b : bad) {
            var r = put(b[0], b[1], b[2], c);
            assertThat(r.getStatus()).as(b[0] + " " + b[3]).isEqualTo(400);
            assertThat(r.getContentAsString()).contains("VALIDATION_ERROR").contains("\"" + b[3] + "\"");
        }
        var platform = model(c, "WELCOME").get("platform");
        var same = put("WELCOME", platform.get("subject").asText(), platform.get("body").asText(), c);
        assertThat(same.getStatus()).isEqualTo(400);
        assertThat(same.getContentAsString()).contains("\"body\"");
        assertThat(put("WELCOME", "x".repeat(150), "x".repeat(2000), c).getStatus()).isEqualTo(200);
        assertThat(put("ANNOUNCEMENT_GENERAL", "Aviso", "Veja [aqui](https://exemplo.test)", c).getStatus()).isEqualTo(200);
        assertThat(count(t)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA11: Administrador lê sem alterar; Professor, Aluno e Responsável 403; anônimo 401")
    void permissions() throws Exception {
        var t = tenant();
        assertThat(send(MockMvcRequestBuilders.get(BASE), null).getStatus()).isEqualTo(401);
        assertThat(put("WELCOME", "Assunto", "Texto", null).getStatus()).isEqualTo(401);
        var admin = account(t, Role.ADMINISTRATOR);
        assertThat(view(admin).get("canManage").asBoolean()).isFalse();
        assertThat(put("WELCOME", "Assunto", "Texto", admin).getStatus()).isEqualTo(403);
        assertThat(delete("WELCOME", admin).getStatus()).isEqualTo(403);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            var who = account(t, role);
            assertThat(send(MockMvcRequestBuilders.get(BASE), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(put("WELCOME", "Assunto", "Texto", who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(delete("WELCOME", who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(count(t)).isZero();
    }

    @Test
    @DisplayName("CA12: organizações separadas e RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var ca = account(a, Role.COORDINATOR);
        var cb = account(b, Role.COORDINATOR);
        assertThat(put("WELCOME", "Assunto de B", "Texto de B {nome}", cb).getStatus()).isEqualTo(200);
        assertThat(model(ca, "WELCOME").get("custom").isNull()).isTrue();
        assertThat(delete("WELCOME", ca).getStatus()).isEqualTo(204);
        assertThat(count(b)).isEqualTo(1);
        assertThat(put("WELCOME", "Assunto de A", "Texto de A {nome}", ca).getStatus()).isEqualTo(200);
        assertThat(model(cb, "WELCOME").get("custom").get("subject").asText()).isEqualTo("Assunto de B");

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select 1 from text_model")) {
                assertThat(rows.next()).isFalse();
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*), count(distinct organization_id) from text_model")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getInt(2)).isEqualTo(1);
            }
            assertThatThrownBy(() -> sql.executeUpdate("insert into text_model (id, organization_id, key, subject, body, updated_by_account_id) values (gen_random_uuid(), '"
                    + b.orgId() + "', 'WELCOME', 'x', 'y', '" + ca.accountId() + "')")).hasMessageContaining("row-level security");
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA13: salvar e voltar ao padrão auditam chave e antes/depois, sem dado de pessoa; recusas e DELETE sem efeito não auditam")
    void audit() throws Exception {
        var t = tenant();
        var c = account(t, Role.COORDINATOR);
        put("WELCOME", "Primeiro assunto", "Primeiro texto {nome}", c);
        put("WELCOME", "Segundo assunto", "Segundo texto {nome}", c);
        put("WELCOME", "Assunto", "Texto {cpf}", c);
        assertThat(delete("WELCOME", c).getStatus()).isEqualTo(204);
        assertThat(delete("WELCOME", c).getStatus()).isEqualTo(204);
        var updated = adminJdbc().queryForList("select actor_profile, entity_id, before_data::text as before, after_data::text as after from audit_log "
                + "where organization_id = ? and action = 'texts.updated' order by occurred_at", t.orgId());
        assertThat(updated).hasSize(2);
        assertThat(updated.get(0).get("entity_id")).isEqualTo("WELCOME");
        assertThat(updated.get(0).get("actor_profile")).isEqualTo("COORDINATOR");
        assertThat((String) updated.get(0).get("after")).contains("Primeiro assunto");
        assertThat((String) updated.get(1).get("before")).contains("Primeiro assunto");
        assertThat((String) updated.get(1).get("after")).contains("Segundo assunto");
        var reset = adminJdbc().queryForList("select before_data::text as before from audit_log where organization_id = ? and action = 'texts.reset'", t.orgId());
        assertThat(reset).hasSize(1);
        assertThat((String) reset.get(0).get("before")).contains("Segundo assunto");
        var all = adminJdbc().queryForList("select coalesce(before_data::text, '') || coalesce(after_data::text, '') from audit_log where organization_id = ? and action like 'texts.%'",
                String.class, t.orgId());
        assertThat(all).noneMatch(s -> s.contains("@example.com") || s.contains("Gestora"));
    }
}
