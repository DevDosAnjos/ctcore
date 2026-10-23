package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-COB-02 (CA3, CA4): painel, cancelar e renegociar isolados entre perfis, unidades e organizações, no filtro e na RLS. */
class ChargeActionsIsolationIT extends ChargeActionsBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired ChargeActions actions;

    private UUID secondUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private int[] attempt(UUID charge, AccountPrincipal who) throws Exception {
        String first = LocalDate.now().plusDays(5).toString();
        return new int[] {send(HttpMethod.GET, "/charges/" + charge, null, who).getStatus(),
                send(HttpMethod.POST, "/charges/" + charge + "/cancel", "{\"reason\":\"x\"}", who).getStatus(),
                send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(charge), null, first, 2, "x"), who).getStatus()};
    }

    @Test
    @DisplayName("CA3: Professor, Aluno, Responsável e anônimo não leem nem alteram; Administrador da outra unidade recebe 404; Coordenador atua nas duas unidades")
    void profiles() throws Exception {
        Tenant t = tenant();
        UUID b = secondUnit(t);
        UUID s = student(t);
        UUID inA = open(t, s, 1000), inB = charge(t, b, s, LocalDate.now().plusDays(5), 1000, "OPEN");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(attempt(inA, account(t, role))).as(role.name()).containsExactly(403, 403, 403);
        }
        assertThat(attempt(inA, null)).containsExactly(401, 401, 401);
        var adminA = account(t, Role.ADMINISTRATOR);
        assertThat(attempt(inB, adminA)).containsExactly(404, 404, 404);
        assertThat(status(inA) + status(inB)).isEqualTo("OPENOPEN");
        assertThat(count("select count(*) from charge")).isEqualTo(2);
        assertThat(audits(t, "charge.canceled") + audits(t, "charge.renegotiated")).isZero();

        var coordinator = account(t, Role.COORDINATOR);
        assertThat(send(HttpMethod.GET, "/charges/" + inB, null, coordinator).getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "/charges/" + inB + "/cancel", "{\"reason\":\"x\"}", coordinator).getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(inA), null, LocalDate.now().plusDays(5).toString(), 1, "x"), coordinator).getStatus())
                .isEqualTo(201);
        // Renegociar misturando uma cobrança fora do alcance do Administrador: 404, sem efeito nas outras.
        UUID again = open(t, s, 500), outside = charge(t, b, s, LocalDate.now().plusDays(5), 500, "OPEN");
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(again, outside), null, LocalDate.now().plusDays(5).toString(), 1, "x"), adminA).getStatus())
                .isEqualTo(404);
        assertThat(status(again)).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("CA4: cobrança de outra organização dá 404 no painel, no cancelar e no renegociar; a RLS esconde a linha mesmo sem filtro")
    void organizations() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID inB = open(b, student(b), 1000);
        var coordinatorA = account(a, Role.COORDINATOR);
        UUID inA = open(a, student(a), 1000);
        assertThat(attempt(inB, coordinatorA)).containsExactly(404, 404, 404);
        assertThat(send(HttpMethod.POST, "/charges/renegotiate", renegotiation(ids(inA, inB), null, LocalDate.now().plusDays(5).toString(), 1, "x"), coordinatorA).getStatus())
                .isEqualTo(404);
        assertThat(status(inB)).isEqualTo("OPEN");
        assertThat(status(inA)).isEqualTo("OPEN");
        assertThat(count("select count(*) from charge")).isEqualTo(2);
        assertThat(audits(b, "charge.canceled") + audits(b, "charge.renegotiated") + audits(a, "charge.renegotiated")).isZero();

        try (var ignored = OrganizationContext.open(coordinatorA)) {
            long rows = transactions.execute(() -> jdbc.sql("select count(*) from charge where id = :id").param("id", inB).query(Long.class).single());
            assertThat(rows).isZero();
            assertThat(transactions.execute(() -> jdbc.sql("update charge set status = 'CANCELED' where id = :id").param("id", inB).update())).isZero();
        }
        var principal = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), a.orgId(), Role.ADMINISTRATOR, Set.of(a.unitId()));
        try (var ignored = OrganizationContext.open(principal)) {
            org.junit.jupiter.api.Assertions.assertThrows(br.com.ctcore.api.error.ApiStatusException.class, () -> actions.detail(principal, inB));
        }
    }
}
