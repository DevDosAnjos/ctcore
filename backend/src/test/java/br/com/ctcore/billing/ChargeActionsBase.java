package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Apoio dos testes do painel, do cancelamento e da renegociação (RF-COB-02): contas, alunos, cobranças em aberto e chamadas. */
public abstract class ChargeActionsBase extends OrganizationBase {
    @Autowired protected ObjectMapper json;

    @AfterEach
    protected void cleanCharges() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from account where email like 'act-%@example.com'");
    }

    protected AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "act-" + p.accountId() + "@example.com");
        return p;
    }

    protected UUID student(Tenant t) {
        return BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno Sintético", "1990-01-01");
    }

    protected UUID charge(Tenant t, UUID unit, UUID student, LocalDate due, long amount, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'ONE_OFF', 'Kimono', ?, ?, ?, ?)", id, t.orgId(), unit, student, due, amount, amount, status);
        return id;
    }

    protected UUID open(Tenant t, UUID student, long amount) {
        return charge(t, t.unitId(), student, LocalDate.now().plusDays(5), amount, "OPEN");
    }

    protected MockHttpServletResponse send(HttpMethod method, String path, String body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.request(method, "/api/v1" + path);
        if (body != null) request.contentType("application/json").content(body);
        return call(request, who).getResponse();
    }

    protected JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    protected int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    protected int audits(Tenant t, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }

    protected String status(UUID charge) {
        return adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge);
    }

    protected static String renegotiation(Object ids, Object amount, String first, int installments, String reason) {
        return "{\"chargeIds\":" + ids + (amount == null ? "" : ",\"amountCents\":" + amount) + (first == null ? "" : ",\"firstDueOn\":\"" + first + "\"")
                + ",\"installments\":" + installments + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    protected static String ids(UUID... ids) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) b.append(i == 0 ? "" : ",").append('"').append(ids[i]).append('"');
        return b.append(']').toString();
    }
}
