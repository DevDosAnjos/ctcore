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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-11, RNF-12: cobrança avulsa, com validação, pagador, permissão, idempotência por requestId e auditoria. */
class OneOffChargeIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired AuditService audit;
    @Autowired ScopedAudit scopedAudit;
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "gen-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID enrolled(Tenant t, String birth) {
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", birth);
        BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), student, 15, 20000);
        return student;
    }

    private static String due(int days) {
        return LocalDate.now().plusDays(days).toString();
    }

    private static String body(UUID requestId, UUID unit, String description, Object amount, Object dueOn) {
        return "{" + (requestId == null ? "" : "\"requestId\":\"" + requestId + "\",") + (unit == null ? "" : "\"unitId\":\"" + unit + "\",")
                + "\"description\":" + (description == null ? "null" : "\"" + description + "\"") + ",\"amountCents\":" + amount
                + (dueOn == null ? "" : ",\"dueOn\":\"" + dueOn + "\"") + "}";
    }

    private MockHttpServletResponse post(UUID student, String body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/students/" + student + "/charges").contentType("application/json").content(body), who).getResponse();
    }

    private long count() {
        return adminJdbc().queryForObject("select count(*) from charge", Long.class);
    }

    private long audits(Tenant t) {
        return adminJdbc().queryForObject("select count(*) from audit_log where action = 'charge.created' and organization_id = ?", Long.class, t.orgId());
    }

    @Test
    @DisplayName("CA1, CA6: adulto matriculado ganha ONE_OFF pendente, sem mês nem modalidade, listada por type=ONE_OFF e auditada")
    void createsForAdult() throws Exception {
        var t = tenant();
        UUID student = enrolled(t, "1990-01-01");
        var admin = account(t, Role.ADMINISTRATOR);

        var response = post(student, body(null, t.unitId(), "  Kimono A2  ", 25000, due(7)), admin);

        assertThat(response.getStatus()).isEqualTo(201);
        JsonNode row = json.readTree(response.getContentAsString());
        assertThat(row.get("type").asText()).isEqualTo("ONE_OFF");
        assertThat(row.get("status").asText()).isEqualTo("PENDING");
        assertThat(row.get("amountCents").asLong()).isEqualTo(25000);
        assertThat(row.get("description").asText()).isEqualTo("Kimono A2");
        assertThat(row.get("referenceMonth").isNull()).isTrue();
        assertThat(row.get("modality").isNull()).isTrue();
        var stored = adminJdbc().queryForMap("select origin, tolerance_days, payer_guardian_id, plan_id, benefit_id, status from charge where id = ?::uuid", row.get("id").asText());
        assertThat(stored.get("origin")).isEqualTo("MANUAL");
        assertThat(stored.get("tolerance_days")).isEqualTo(0);
        assertThat(stored.get("payer_guardian_id")).isNull();
        assertThat(stored.get("plan_id")).isNull();
        assertThat(stored.get("status")).isEqualTo("OPEN");

        var list = call(MockMvcRequestBuilders.get("/api/v1/charges?type=ONE_OFF"), admin).getResponse();
        assertThat(json.readTree(list.getContentAsString()).get("items")).hasSize(1);

        String after = adminJdbc().queryForObject("select after_data::text from audit_log where action = 'charge.created' and organization_id = ?", String.class, t.orgId());
        assertThat(after).contains("ONE_OFF", "MANUAL", "25000", due(7), "Kimono A2", student.toString());
        assertThat(adminJdbc().queryForObject("select unit_id from audit_log where action = 'charge.created' and organization_id = ?", UUID.class, t.orgId())).isEqualTo(t.unitId());
    }

    @Test
    @DisplayName("CA2: menor com responsável financeiro paga pelo responsável; menor sem ele e aluno sem matrícula na unidade são 422")
    void payer() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        String minorBirth = LocalDate.now().minusYears(10).toString();
        UUID minor = enrolled(t, minorBirth);
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);

        var response = post(minor, body(null, t.unitId(), "Exame", 5000, due(1)), admin);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(adminJdbc().queryForObject("select payer_guardian_id from charge where id = ?::uuid", UUID.class, json.readTree(response.getContentAsString()).get("id").asText()))
                .isEqualTo(guardian);

        UUID alone = enrolled(t, minorBirth);
        BillingFixtures.guardian(adminJdbc(), t.orgId(), alone, false);
        UUID none = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Sem matrícula", "1990-01-01");
        long before = count();
        assertThat(post(alone, body(null, t.unitId(), "Exame", 5000, due(1)), admin).getStatus()).isEqualTo(422);
        assertThat(post(none, body(null, t.unitId(), "Exame", 5000, due(1)), admin).getStatus()).isEqualTo(422);
        assertThat(count()).isEqualTo(before);
        assertThat(audits(t)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA3: descrição vazia, de espaços ou com 81, valor 0, negativo ou alto, vencimento ontem, malformado ou ausente e unitId ausente são 400 sem gravar")
    void validation() throws Exception {
        var t = tenant();
        UUID student = enrolled(t, "1990-01-01");
        var admin = account(t, Role.ADMINISTRATOR);
        String[] bad = {body(null, t.unitId(), "", 100, due(1)), body(null, t.unitId(), "   ", 100, due(1)), body(null, t.unitId(), "x".repeat(81), 100, due(1)),
                body(null, t.unitId(), null, 100, due(1)), body(null, t.unitId(), "Ok", 0, due(1)), body(null, t.unitId(), "Ok", -5, due(1)),
                body(null, t.unitId(), "Ok", 10_000_001, due(1)), body(null, t.unitId(), "Ok", null, due(1)), body(null, t.unitId(), "Ok", 100, due(-2)),
                body(null, t.unitId(), "Ok", 100, "31/12"), body(null, t.unitId(), "Ok", 100, null), body(null, null, "Ok", 100, due(1))};
        for (String b : bad) assertThat(post(student, b, admin).getStatus()).as(b).isEqualTo(400);
        assertThat(post(student, body(null, t.unitId(), "x".repeat(80), 10_000_000, due(0)), admin).getStatus()).isEqualTo(201);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA4: Professor, Aluno e Responsável 403; sem sessão 401; outra unidade, aluno inexistente e outra organização 404; Coordenador na sua unidade 201")
    void permissions() throws Exception {
        var t = tenant();
        var other = tenant();
        UUID student = enrolled(t, "1990-01-01");
        String ok = body(null, t.unitId(), "Kimono", 100, due(1));
        assertThat(post(student, ok, null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) assertThat(post(student, ok, account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        assertThat(post(student, ok, account(other, Role.ADMINISTRATOR)).getStatus()).isEqualTo(404);
        var elsewhere = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa', 'ACTIVE')", elsewhere.accountId(), "gen-" + elsewhere.accountId() + "@example.com");
        assertThat(post(student, ok, elsewhere).getStatus()).isEqualTo(404);
        var admin = account(t, Role.ADMINISTRATOR);
        assertThat(post(UUID.randomUUID(), ok, admin).getStatus()).isEqualTo(404);
        assertThat(post(student, body(null, UUID.randomUUID(), "Kimono", 100, due(1)), admin).getStatus()).isEqualTo(404);
        assertThat(count()).isZero();
        assertThat(audits(t)).isZero();
        assertThat(post(student, ok, account(t, Role.COORDINATOR)).getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA5: mesmo requestId repetido ou em paralelo cria uma cobrança só e uma auditoria; dado diferente ou id de outra organização é 409")
    void idempotent() throws Exception {
        var t = tenant();
        UUID student = enrolled(t, "1990-01-01");
        var admin = account(t, Role.ADMINISTRATOR);
        UUID request = UUID.randomUUID();
        String same = body(request, t.unitId(), "Kimono", 25000, due(3));

        var first = post(student, same, admin);
        var second = post(student, same, admin);
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(second.getStatus()).isEqualTo(200);
        assertThat(json.readTree(second.getContentAsString()).get("id").asText()).isEqualTo(request.toString());
        assertThat(post(student, body(request, t.unitId(), "Kimono", 25001, due(3)), admin).getStatus()).isEqualTo(409);
        assertThat(post(student, body(request, t.unitId(), "Outro", 25000, due(3)), admin).getStatus()).isEqualTo(409);
        assertThat(post(student, body(request, t.unitId(), "Kimono", 25000, due(4)), admin).getStatus()).isEqualTo(409);

        var other = tenant();
        UUID otherStudent = enrolled(other, "1990-01-01");
        assertThat(post(otherStudent, body(request, other.unitId(), "Kimono", 25000, due(3)), account(other, Role.ADMINISTRATOR)).getStatus()).isEqualTo(409);
        assertThat(count()).isEqualTo(1);
        assertThat(audits(t)).isEqualTo(1);

        UUID parallel = UUID.randomUUID();
        String body = body(parallel, t.unitId(), "Seminário", 9000, due(5));
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> call = () -> post(student, body, admin).getStatus();
            var a = pool.submit(call);
            var b = pool.submit(call);
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 201);
        } finally {
            pool.shutdownNow();
        }
        assertThat(adminJdbc().queryForObject("select count(*) from charge where id = ?", Long.class, parallel)).isEqualTo(1);
        assertThat(audits(t)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA7: a avulsa não toca o ponteiro nem as mensalidades e a geração diária a ignora")
    void leavesMonthlyAlone() throws Exception {
        var t = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01");
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), student, 15, 20000);
        adminJdbc().update("update enrollment_modality set next_charge_ref = date '2026-11-01', next_charge_due_on = date '2026-11-15' where id = ?", row);
        assertThat(post(student, body(null, t.unitId(), "Kimono", 100, due(1)), account(t, Role.ADMINISTRATOR)).getStatus()).isEqualTo(201);
        assertThat(adminJdbc().queryForObject("select next_charge_ref from enrollment_modality where id = ?", LocalDate.class, row)).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(adminJdbc().queryForObject("select next_charge_due_on from enrollment_modality where id = ?", LocalDate.class, row)).isEqualTo(LocalDate.parse("2026-11-15"));

        var job = new ChargeGenerationJob(transactions, jdbc, new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit,
                Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC)));
        job.runOnce();
        long monthly = adminJdbc().queryForObject("select count(*) from charge where type = 'MONTHLY'", Long.class);
        job.runOnce();
        assertThat(monthly).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select count(*) from charge where type = 'MONTHLY'", Long.class)).isEqualTo(monthly);
        assertThat(adminJdbc().queryForObject("select count(*) from charge where type = 'ONE_OFF'", Long.class)).isEqualTo(1);
    }
}
