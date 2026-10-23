package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-08, RN-COB-12: os recebimentos do dia de uma organização não aparecem para a outra, pelo código e pelo banco. */
class DailyReceiptsIsolationIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like 'day-%@example.com'");
    }

    private AccountPrincipal admin(Tenant t) {
        AccountPrincipal p = who(t, Role.ADMINISTRATOR);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "day-" + p.accountId() + "@example.com");
        return p;
    }

    @Test
    @DisplayName("CA7: a organização B não vê o dia da unidade da A, e o papel de execução da B não lê pagamento da A mesmo sem filtro")
    void otherOrganizationSeesNothing() throws Exception {
        var a = tenant();
        var b = tenant();
        var adminA = admin(a);
        var adminB = admin(b);
        DailyReceiptsIT.pay(adminJdbc(), a, "Aluno A", 9000, "CASH", "MANAGEMENT", Instant.now().toString(), adminA.accountId(), "CONFIRMED");
        String url = "/api/v1/billing/daily-receipts?unitId=" + a.unitId();

        assertThat(json.readTree(call(MockMvcRequestBuilders.get(url), adminA).getResponse().getContentAsString()).get("count").asInt()).isEqualTo(1);
        var foreign = json.readTree(call(MockMvcRequestBuilders.get(url), adminB).getResponse().getContentAsString());
        assertThat(foreign.get("count").asInt()).isZero();
        assertThat(foreign.get("payments")).isEmpty();

        try (var app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); var sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + b.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(*) from payment")) {
                rows.next();
                assertThat(rows.getInt(1)).isZero();
            }
            app.rollback();
        }
    }
}
