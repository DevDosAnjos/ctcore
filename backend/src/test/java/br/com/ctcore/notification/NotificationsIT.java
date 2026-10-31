package br.com.ctcore.notification;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-NOT-01: destinatários, rotas, perfis, isolamento com RLS, auditoria e e-mail urgente, com banco real. */
class NotificationsIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired Notifications notifications;
    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired br.com.ctcore.isolation.OrganizationTransactions transactions;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from notification");
        adminJdbc().update("delete from notification_preference");
        adminJdbc().update("delete from email_message where to_email like 'notif-%@example.com'");
        adminJdbc().update("delete from account_link_unit where link_id in (select l.id from account_link l join account a on a.id = l.account_id where a.email like 'notif-%@example.com')");
        adminJdbc().update("delete from account_link where account_id in (select id from account where email like 'notif-%@example.com')");
        adminJdbc().update("delete from account where email like 'notif-%@example.com'");
    }

    /** Pessoa com conta e vínculo reais (e unidades); o principal leva o vínculo na sessão. */
    private AccountPrincipal person(Tenant t, Role role, String status, UUID... units) {
        UUID account = UUID.randomUUID(), link = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Pessoa Sintética', 'ACTIVE')", account, "notif-" + account + "@example.com");
        adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, ?)", link, account, t.orgId(), role.name(), status);
        adminJdbc().update("insert into account_two_factor (account_id, secret_encrypted, enabled_at) values (?, 'x', now())", account);
        for (UUID unit : units) {
            adminJdbc().update("insert into account_link_unit (link_id, organization_id, unit_id) values (?, ?, ?)", link, t.orgId(), unit);
        }
        return new AccountPrincipal(account, UUID.randomUUID(), link, t.orgId(), role, Set.of(units));
    }

    private AccountPrincipal person(Tenant t, Role role, UUID... units) {
        return person(t, role, "ACTIVE", units);
    }

    private UUID secondUnit(Tenant t) {
        UUID unit = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Unidade B', ?)", unit, t.orgId(), "b-" + unit);
        return unit;
    }

    private int count(AccountPrincipal who, String type) {
        return adminJdbc().queryForObject("select count(*) from notification where link_id = ? and type = ?", Integer.class, who.activeLinkId(), type);
    }

    private void insert(AccountPrincipal who, Tenant t, String type, String age, boolean read) {
        adminJdbc().update("insert into notification (id, organization_id, link_id, unit_id, type, ref_type, ref_id, params, created_at, read_at) "
                + "values (?, ?, ?, ?, ?, 'charge', ?, cast(? as jsonb), now() - cast(? as interval), " + (read ? "now()" : "null") + ")",
                UUID.randomUUID(), t.orgId(), who.activeLinkId(), t.unitId(), type, UUID.randomUUID(), "{\"studentName\":\"Aluno Sintético\"}", age);
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, AccountPrincipal who) throws Exception {
        return call(request, who).getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private static MockHttpServletRequestBuilder put(String body) {
        return MockMvcRequestBuilders.put("/api/v1/notifications/preferences").contentType("application/json").content(body);
    }

    @Test
    @DisplayName("CA1, CA2, CA8: tipo de unidade vai ao Administrador da unidade; Coordenador só com receber tudo; do Coordenador não vai ao Administrador; inativo e Professor não; sem repetir")
    void recipients() throws Exception {
        var t = tenant();
        UUID other = secondUnit(t);
        var adminA = person(t, Role.ADMINISTRATOR, t.unitId());
        var adminB = person(t, Role.ADMINISTRATOR, other);
        var ended = person(t, Role.ADMINISTRATOR, "ENDED", t.unitId());
        var teacher = person(t, Role.TEACHER, t.unitId());
        var coordinator = person(t, Role.COORDINATOR);
        UUID charge = UUID.randomUUID();

        notifications.publish(t.orgId(), t.unitId(), NotificationType.PAYMENT_RECEIVED, "charge", charge, Map.of("amountCents", 15000));
        notifications.publish(t.orgId(), t.unitId(), NotificationType.PAYMENT_RECEIVED, "charge", charge, Map.of("amountCents", 15000));
        assertThat(count(adminA, "PAYMENT_RECEIVED")).isEqualTo(1);
        for (var no : List.of(adminB, ended, teacher, coordinator)) assertThat(count(no, "PAYMENT_RECEIVED")).isZero();

        assertThat(send(put("{\"receiveAll\":true}"), coordinator).getStatus()).isEqualTo(200);
        notifications.publish(t.orgId(), t.unitId(), NotificationType.PAYMENT_RECEIVED, "charge", UUID.randomUUID(), Map.of());
        assertThat(count(coordinator, "PAYMENT_RECEIVED")).isEqualTo(1);
        assertThat(count(adminA, "PAYMENT_RECEIVED")).isEqualTo(2);

        notifications.publish(t.orgId(), t.unitId(), NotificationType.HIGH_BENEFIT, "benefit", UUID.randomUUID(), Map.of());
        notifications.publish(t.orgId(), null, NotificationType.INVITE_PENDING, "invite", UUID.randomUUID(), Map.of());
        assertThat(count(coordinator, "HIGH_BENEFIT")).isEqualTo(1);
        assertThat(count(coordinator, "INVITE_PENDING")).isEqualTo(1);
        for (var no : List.of(adminA, adminB, ended, teacher)) {
            assertThat(count(no, "HIGH_BENEFIT") + count(no, "INVITE_PENDING")).isZero();
        }
    }

    @Test
    @DisplayName("CA10, CA11, CA12: janela de 7 dias, filtro de não lidas, contador de qualquer data, read-all com auditoria e só nas do vínculo")
    void listCountReadAll() throws Exception {
        var t = tenant();
        var admin = person(t, Role.ADMINISTRATOR, t.unitId());
        var mate = person(t, Role.ADMINISTRATOR, t.unitId());
        for (int i = 0; i < 3; i++) insert(admin, t, "PAYMENT_RECEIVED", (i + 1) + " seconds", i == 0);
        insert(admin, t, "EMAIL_PROBLEM", "3 days", false);
        insert(admin, t, "EMAIL_PROBLEM", "3 days 1 hour", false);
        insert(admin, t, "STUDENT_MAJORITY", "9 days", false);
        insert(mate, t, "PAYMENT_RECEIVED", "0 seconds", false);

        var all = body(send(MockMvcRequestBuilders.get("/api/v1/notifications"), admin));
        assertThat(all.get("items")).hasSize(5);
        assertThat(all.get("items").findValuesAsText("group")).containsExactly("TODAY", "TODAY", "TODAY", "WEEK", "WEEK");
        assertThat(all.get("unreadCount").asInt()).isEqualTo(5);
        assertThat(all.get("items").get(0).get("params").get("studentName").asText()).isEqualTo("Aluno Sintético");
        assertThat(all.get("items").get(0).get("refType").asText()).isEqualTo("charge");
        var unread = body(send(MockMvcRequestBuilders.get("/api/v1/notifications?filter=unread"), admin));
        assertThat(unread.get("items")).hasSize(4);
        assertThat(send(MockMvcRequestBuilders.get("/api/v1/notifications?filter=outro"), admin).getStatus()).isEqualTo(400);

        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), admin)).get("count").asInt()).isEqualTo(5);
        var read = send(MockMvcRequestBuilders.post("/api/v1/notifications/read-all"), admin);
        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(body(read).get("changed").asInt()).isEqualTo(5);
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), admin)).get("count").asInt()).isZero();
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications"), admin)).get("items").get(0).get("readAt").isNull()).isFalse();
        assertThat(body(send(MockMvcRequestBuilders.post("/api/v1/notifications/read-all"), admin)).get("changed").asInt()).isZero();
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), mate)).get("count").asInt()).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'notification.read_all'", Integer.class, t.orgId())).isEqualTo(2);
    }

    @Test
    @DisplayName("CA4: a não lida de 31 dias ainda gravada não entra no contador nem no unreadCount; a de 29 entra; só do próprio vínculo")
    void expiredNotCounted() throws Exception {
        var t = tenant();
        var other = tenant();
        var admin = person(t, Role.ADMINISTRATOR, t.unitId());
        var mate = person(t, Role.ADMINISTRATOR, t.unitId());
        var outsider = person(other, Role.ADMINISTRATOR, other.unitId());
        insert(admin, t, "PAYMENT_RECEIVED", "31 days", false);
        insert(admin, t, "EMAIL_PROBLEM", "30 days", false);
        insert(admin, t, "STUDENT_MAJORITY", "29 days", false);
        insert(mate, t, "PAYMENT_RECEIVED", "1 hour", false);
        insert(outsider, other, "PAYMENT_RECEIVED", "1 hour", false);

        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), admin)).get("count").asInt()).isEqualTo(1);
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications?filter=unread"), admin)).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications"), admin)).get("items")).isEmpty();
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), mate)).get("count").asInt()).isEqualTo(1);
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), outsider)).get("count").asInt()).isEqualTo(1);
        new NotificationRetentionJob(transactions, jdbc, java.time.Clock.systemUTC()).runOnce();
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/unread-count"), admin)).get("count").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA13: o Coordenador liga e desliga receber tudo com auditoria; Administrador 403 no PUT e canReceiveAll falso; valor que não é booleano 422")
    void preferences() throws Exception {
        var t = tenant();
        var coordinator = person(t, Role.COORDINATOR);
        var admin = person(t, Role.ADMINISTRATOR, t.unitId());
        var r0 = send(MockMvcRequestBuilders.get("/api/v1/notifications/preferences"), coordinator);
        var initial = body(r0);
        assertThat(initial.get("receiveAll").asBoolean()).isFalse();
        assertThat(initial.get("canReceiveAll").asBoolean()).isTrue();
        var on = send(put("{\"receiveAll\":true}"), coordinator);
        assertThat(on.getStatus()).isEqualTo(200);
        assertThat(body(on).get("receiveAll").asBoolean()).isTrue();
        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications/preferences"), coordinator)).get("receiveAll").asBoolean()).isTrue();
        assertThat(send(put("{\"receiveAll\":false}"), coordinator).getStatus()).isEqualTo(200);
        assertThat(send(put("{\"receiveAll\":\"sim\"}"), coordinator).getStatus()).isEqualTo(422);
        assertThat(send(put("{}"), coordinator).getStatus()).isEqualTo(422);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'notification.preferences_updated'", Integer.class, t.orgId())).isEqualTo(2);

        assertThat(send(put("{\"receiveAll\":true}"), admin).getStatus()).isEqualTo(403);
        var adminView = body(send(MockMvcRequestBuilders.get("/api/v1/notifications/preferences"), admin));
        assertThat(adminView.get("canReceiveAll").asBoolean()).isFalse();
        assertThat(adminView.get("receiveAll").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA14: sem sessão 401, portal 403, Professor com lista vazia, e outra organização não vê nem marca nada")
    void permissionsAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var adminA = person(a, Role.ADMINISTRATOR, a.unitId());
        var adminB = person(b, Role.ADMINISTRATOR, b.unitId());
        var teacher = person(a, Role.TEACHER, a.unitId());
        insert(adminA, a, "PAYMENT_RECEIVED", "0 seconds", false);
        String[] routes = {"/api/v1/notifications", "/api/v1/notifications/unread-count", "/api/v1/notifications/preferences"};
        for (String route : routes) {
            assertThat(send(MockMvcRequestBuilders.get(route), null).getStatus()).as(route).isEqualTo(401);
            for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN}) {
                assertThat(send(MockMvcRequestBuilders.get(route), person(a, role)).getStatus()).as(route + role).isEqualTo(403);
            }
        }
        assertThat(send(MockMvcRequestBuilders.post("/api/v1/notifications/read-all"), null).getStatus()).isEqualTo(401);
        assertThat(send(MockMvcRequestBuilders.post("/api/v1/notifications/read-all"), person(a, Role.STUDENT)).getStatus()).isEqualTo(403);
        assertThat(send(put("{\"receiveAll\":true}"), person(a, Role.GUARDIAN)).getStatus()).isEqualTo(403);

        var empty = send(MockMvcRequestBuilders.get("/api/v1/notifications"), teacher);
        assertThat(empty.getStatus()).isEqualTo(200);
        assertThat(body(empty).get("items")).isEmpty();

        assertThat(body(send(MockMvcRequestBuilders.get("/api/v1/notifications"), adminB)).get("items")).isEmpty();
        assertThat(body(send(MockMvcRequestBuilders.post("/api/v1/notifications/read-all"), adminB)).get("changed").asInt()).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from notification where read_at is null and organization_id = ?", Integer.class, a.orgId())).isEqualTo(1);

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            try (var rows = sql.executeQuery("select 1 from notification")) {
                assertThat(rows.next()).isFalse();
            }
            assertThatThrownBy(() -> sql.executeUpdate("insert into notification (id, organization_id, link_id, type) values (gen_random_uuid(), '" + a.orgId()
                    + "', '" + adminA.activeLinkId() + "', 'PAYMENT_RECEIVED')")).hasMessageContaining("row-level security");
            app.rollback();
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            assertThatThrownBy(() -> sql.executeUpdate("delete from notification")).hasMessageContaining("permission denied");
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA9: tipo urgente manda e-mail com assunto próprio ao Coordenador, sem nome de aluno; pagamento recebido não manda")
    void urgentEmail() {
        var t = tenant();
        var coordinator = person(t, Role.COORDINATOR);
        var admin = person(t, Role.ADMINISTRATOR, t.unitId());
        notifications.publish(t.orgId(), null, NotificationType.SUBSCRIPTION_OVERDUE, "subscription", UUID.randomUUID(), Map.of("studentName", "Aluno Sintético"));
        notifications.publish(t.orgId(), t.unitId(), NotificationType.PAYMENT_RECEIVED, "charge", UUID.randomUUID(), Map.of("studentName", "Aluno Sintético"));
        assertThat(count(coordinator, "SUBSCRIPTION_OVERDUE")).isEqualTo(1);
        assertThat(count(admin, "SUBSCRIPTION_OVERDUE")).isZero();
        var mails = adminJdbc().queryForList("select to_email, subject, status from email_message where organization_id = ? and template = 'NOTIFICATION_URGENT'", t.orgId());
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0).get("to_email")).isEqualTo("notif-" + coordinator.accountId() + "@example.com");
        assertThat((String) mails.get(0).get("subject")).isEqualTo("A assinatura do CTCore está em atraso");
        assertThat(mails.toString()).doesNotContain("Aluno Sintético");
        // repetir o mesmo fato não repete a notificação nem o e-mail
        UUID again = UUID.randomUUID();
        notifications.publish(t.orgId(), null, NotificationType.MP_DISCONNECTED, "organization", again, Map.of());
        notifications.publish(t.orgId(), null, NotificationType.MP_DISCONNECTED, "organization", again, Map.of());
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where organization_id = ? and template = 'NOTIFICATION_URGENT'", Integer.class, t.orgId())).isEqualTo(2);
    }
}
