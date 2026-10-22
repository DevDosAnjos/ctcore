package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-06: geração adiantada das próximas N mensalidades, tudo ou nada, com permissão, isolamento e auditoria. */
class AdvanceChargesIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired AuditService audit;
    @Autowired ScopedAudit scopedAudit;
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().execute("drop trigger if exists charge_forced_failure on charge");
        adminJdbc().execute("drop function if exists charge_forced_failure()");
        BillingFixtures.cleanAll(adminJdbc());
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "gen-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID row(Tenant t, long plan) {
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01"), 15, plan);
        adminJdbc().update("update enrollment_modality set next_charge_ref = date '2026-11-01', next_charge_due_on = date '2026-11-15' where id = ?", row);
        return row;
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private MockHttpServletResponse advance(UUID row, String body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/students/" + student(row) + "/modalities/" + row + "/charges/advance").contentType("application/json").content(body), who)
                .getResponse();
    }

    private long charges(UUID row) {
        return adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ?", Long.class, row);
    }

    @Test
    @DisplayName("CA17: count 3 cria nov, dez e jan, o ponteiro vai para fev e a tarefa diária não cria nenhuma delas; audita as cobranças")
    void advancesThree() throws Exception {
        var t = tenant();
        UUID row = row(t, 20000);
        var admin = account(t, Role.ADMINISTRATOR);

        var response = advance(row, "{\"count\":3}", admin);

        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body).hasSize(3);
        assertThat(List.of(body.get(0).get("referenceMonth").asText(), body.get(1).get("referenceMonth").asText(), body.get(2).get("referenceMonth").asText()))
                .containsExactly("2026-11-01", "2026-12-01", "2027-01-01");
        assertThat(body.get(2).get("dueOn").asText()).isEqualTo("2027-01-15");
        assertThat(body.get(0).get("amountCents").asLong()).isEqualTo(20000);
        assertThat(adminJdbc().queryForObject("select next_charge_ref from enrollment_modality where id = ?", java.time.LocalDate.class, row)).isEqualTo(java.time.LocalDate.parse("2027-02-01"));
        assertThat(adminJdbc().queryForObject("select count(*) from charge where origin = 'ADVANCE' and created_by_account_id = ?", Long.class, admin.accountId())).isEqualTo(3);

        var job = new ChargeGenerationJob(transactions, jdbc, new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit,
                Clock.fixed(Instant.parse("2027-01-05T12:00:00Z"), ZoneOffset.UTC)));
        job.runOnce();
        assertThat(charges(row)).isEqualTo(3);

        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'charge.advance_generated' and organization_id = ?", String.class, t.orgId());
        for (JsonNode c : body) assertThat(after).contains(c.get("id").asText());
    }

    @Test
    @DisplayName("CA17: o mês já gerado é pulado e a resposta traz só o que foi criado")
    void skipsExisting() throws Exception {
        var t = tenant();
        UUID row = row(t, 20000);
        BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student(row), row, java.time.LocalDate.parse("2026-11-01"), null, 20000, "CANCELED");
        var response = advance(row, "{\"count\":2}", account(t, Role.COORDINATOR));
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(json.readTree(response.getContentAsString())).hasSize(1);
        assertThat(charges(row)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA17: count 0, 13 ou ausente é 400; a falha na terceira desfaz as três; modalidade encerrada ou de valor zero é 422")
    void rules() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID row = row(t, 20000);
        for (String bad : new String[] {"{\"count\":0}", "{\"count\":13}", "{}"}) assertThat(advance(row, bad, admin).getStatus()).as(bad).isEqualTo(400);
        assertThat(charges(row)).isZero();

        adminJdbc().execute("create function charge_forced_failure() returns trigger language plpgsql as $$ begin "
                + "if new.reference_month = date '2027-01-01' then raise exception 'forced failure'; end if; return new; end $$");
        adminJdbc().execute("create trigger charge_forced_failure before insert on charge for each row execute function charge_forced_failure()");
        assertThat(advance(row, "{\"count\":3}", admin).getStatus()).isEqualTo(500);
        assertThat(charges(row)).isZero();
        assertThat(adminJdbc().queryForObject("select next_charge_ref from enrollment_modality where id = ?", java.time.LocalDate.class, row)).isEqualTo(java.time.LocalDate.parse("2026-11-01"));
        adminJdbc().execute("drop trigger charge_forced_failure on charge");

        UUID ended = row(t, 20000);
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id = ?", ended);
        assertThat(advance(ended, "{\"count\":1}", admin).getStatus()).isEqualTo(422);

        UUID free = row(t, 20000);
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(free), free, "PERCENT", 100, null, "ACTIVE");
        assertThat(advance(free, "{\"count\":1}", admin).getStatus()).isEqualTo(422);
        assertThat(charges(free) + charges(ended)).isZero();
    }

    @Test
    @DisplayName("CA17: Professor, Aluno e Responsável 403; sem sessão 401; outra organização ou unidade fora do alcance 404")
    void permissionsAndIsolation() throws Exception {
        var t = tenant();
        var other = tenant();
        UUID row = row(t, 20000);
        assertThat(advance(row, "{\"count\":1}", null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(advance(row, "{\"count\":1}", account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(advance(row, "{\"count\":1}", account(other, Role.ADMINISTRATOR)).getStatus()).isEqualTo(404);
        var elsewhere = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa', 'ACTIVE')", elsewhere.accountId(), "gen-" + elsewhere.accountId() + "@example.com");
        assertThat(advance(row, "{\"count\":1}", elsewhere).getStatus()).isEqualTo(404);
        assertThat(call(MockMvcRequestBuilders.post("/api/v1/students/" + student(row) + "/modalities/" + UUID.randomUUID() + "/charges/advance")
                .contentType("application/json").content("{\"count\":1}"), account(t, Role.ADMINISTRATOR)).getResponse().getStatus()).isEqualTo(404);
        assertThat(charges(row)).isZero();
    }
}
