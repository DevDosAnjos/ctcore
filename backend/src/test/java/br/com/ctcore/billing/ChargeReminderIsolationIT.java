package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-COB-06 (#79, CA22, CA23): a lista de atrasados e o envio manual isolados entre perfis, unidades e organizações, no filtro e na RLS. */
class ChargeReminderIsolationIT extends ChargeActionsBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;

    @AfterEach
    void cleanMails() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from email_message where to_email like 'iso-%'");
    }

    private UUID secondUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?) on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID late(Tenant t, UUID unit, UUID student) {
        return charge(t, unit, student, today().minusDays(3), 1000, "OPEN");
    }

    private UUID student(Tenant t, String name) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), name, "1990-01-01");
        adminJdbc().update("update student set email = ? where id = ?", name + "@example.com", s);
        return s;
    }

    private int[] attempt(UUID charge, br.com.ctcore.identity.AccountPrincipal who) throws Exception {
        return new int[] {send(HttpMethod.GET, "/charges/overdue", null, who).getStatus(),
                send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(charge) + "}", who).getStatus()};
    }

    private int mails() {
        return count("select count(*) from email_message where to_email like 'iso-%'");
    }

    @Test
    @DisplayName("CA22: Professor, Aluno, Responsável e anônimo não leem nem enviam; Administrador da unidade A com cobrança da B no corpo recebe 404 para o pedido inteiro")
    void profiles() throws Exception {
        Tenant t = tenant();
        UUID b = secondUnit(t);
        UUID inA = late(t, t.unitId(), student(t, "iso-a")), inB = late(t, b, student(t, "iso-b"));
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(attempt(inA, account(t, role))).as(role.name()).containsExactly(403, 403);
        }
        assertThat(attempt(inA, null)).containsExactly(401, 401);
        var adminA = account(t, Role.ADMINISTRATOR);
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(inA, inB) + "}", adminA).getStatus()).isEqualTo(404);
        assertThat(mails()).isZero();
        assertThat(count("select count(*) from charge_reminder")).isZero();
        assertThat(audits(t, "charge.reminder_sent")).isZero();
        assertThat(send(HttpMethod.GET, "/charges/overdue?unitId=" + b, null, adminA).getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(inA) + "}", adminA).getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(inB) + "}", account(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        assertThat(mails()).isEqualTo(2);
    }

    @Test
    @DisplayName("CA23: ids e unidade de outra organização dão 404 e nada muda; a lista não traz linhas da outra; a RLS esconde e protege charge_reminder e sms_log")
    void organizations() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID sb = student(b, "iso-outra");
        UUID inB = late(b, b.unitId(), sb), inA = late(a, a.unitId(), student(a, "iso-propria"));
        var coordinatorA = account(a, Role.COORDINATOR);
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(inB) + "}", coordinatorA).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.POST, "/charges/reminders", "{\"chargeIds\":" + ids(inA, inB) + "}", coordinatorA).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/charges/overdue?unitId=" + b.unitId(), null, coordinatorA).getStatus()).isEqualTo(404);
        var list = ok(send(HttpMethod.GET, "/charges/overdue", null, coordinatorA), 200);
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(inA.toString());
        assertThat(mails()).isZero();
        assertThat(audits(b, "charge.reminder_sent") + audits(a, "charge.reminder_sent")).isZero();

        adminJdbc().update("insert into charge_reminder (id, organization_id, charge_id, kind, channel, status, created_at) values (?, ?, ?, 'MANUAL', 'EMAIL', 'SENT', now())",
                UUID.randomUUID(), b.orgId(), inB);
        adminJdbc().update("insert into sms_log (id, organization_id, student_id, kind, created_at) values (?, ?, ?, 'BLOCK_WARNING', now())", UUID.randomUUID(), b.orgId(), sb);
        try (var ignored = OrganizationContext.open(coordinatorA)) {
            for (String table : new String[] {"charge_reminder", "sms_log"}) {
                assertThat(transactions.execute(() -> jdbc.sql("select count(*) from " + table).query(Long.class).single())).as(table).isZero();
                org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                        () -> transactions.execute(() -> jdbc.sql("delete from " + table).update()), table);
                org.junit.jupiter.api.Assertions.assertThrows(DataAccessException.class,
                        () -> transactions.execute(() -> jdbc.sql("update " + table + " set created_at = now()").update()), table);
            }
        }
    }
}
