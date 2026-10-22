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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-02 (RF-COB-03): trocar o vencimento e cancelar a próxima mensalidade antes de a tarefa gerar. */
class NextChargeAdjustIT extends OrganizationBase {
    private static final LocalDate REF = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).plusMonths(2);

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

    private UUID modality(Tenant t) {
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01"), 15, 20000);
        adminJdbc().update("update enrollment_modality set next_charge_ref = ?, next_charge_due_on = ? where id = ?", REF, REF.withDayOfMonth(15), row);
        return row;
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private MockHttpServletResponse change(UUID row, String body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.patch("/api/v1/students/" + student(row) + "/modalities/" + row + "/next-charge").contentType("application/json").content(body), who)
                .getResponse();
    }

    private MockHttpServletResponse cancel(UUID row, String body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1/students/" + student(row) + "/modalities/" + row + "/next-charge/cancel").contentType("application/json").content(body), who)
                .getResponse();
    }

    private long audits(Tenant t, String action) {
        return adminJdbc().queryForObject("select count(*) from audit_log where action = ? and organization_id = ?", Long.class, action, t.orgId());
    }

    private LocalDate pointer(UUID row) {
        return adminJdbc().queryForObject("select next_charge_due_on from enrollment_modality where id = ?", LocalDate.class, row);
    }

    private long charges(UUID row) {
        return adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ?", Long.class, row);
    }

    private void runJobOn(Tenant t, LocalDate day) {
        var generation = new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, Clock.fixed(Instant.parse(day + "T12:00:00Z"), ZoneOffset.UTC));
        transactions.provision(t.orgId(), () -> generation.runUnit(t.orgId(), t.unitId()));
    }

    @Test
    @DisplayName("CA1, CA2, CA7, CA8: troca o vencimento só desta mensalidade, recusa data fora do mês, repetir a data não audita de novo")
    void changesTheDueDate() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID row = modality(t);

        var ok = change(row, "{\"dueOn\":\"" + REF.withDayOfMonth(28) + "\"}", admin);
        assertThat(ok.getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(ok.getContentAsString());
        assertThat(body.get("dueOn").asText()).isEqualTo(REF.withDayOfMonth(28).toString());
        assertThat(body.get("referenceMonth").asText()).isEqualTo(REF.toString());
        assertThat(audits(t, "charge.next_due_changed")).isEqualTo(1);
        assertThat(change(row, "{\"dueOn\":\"" + REF.withDayOfMonth(28) + "\"}", admin).getStatus()).isEqualTo(200);
        assertThat(audits(t, "charge.next_due_changed")).isEqualTo(1);
        String data = adminJdbc().queryForObject("select before_data::text || after_data::text from audit_log where action = 'charge.next_due_changed' and organization_id = ?", String.class, t.orgId());
        assertThat(data).contains(REF.withDayOfMonth(15).toString()).contains(REF.withDayOfMonth(28).toString());

        for (String bad : new String[] {"{\"dueOn\":\"" + LocalDate.now(ZoneOffset.UTC).minusDays(2) + "\"}", "{\"dueOn\":\"" + REF.plusMonths(1) + "\"}", "{\"dueOn\":\"x\"}", "{}"}) {
            assertThat(change(row, bad, admin).getStatus()).as(bad).isEqualTo(400);
        }
        assertThat(pointer(row)).isEqualTo(REF.withDayOfMonth(28));
        assertThat(audits(t, "charge.next_due_changed")).isEqualTo(1);

        runJobOn(t, REF.withDayOfMonth(20));
        assertThat(adminJdbc().queryForObject("select due_on from charge where enrollment_modality_id = ?", LocalDate.class, row)).isEqualTo(REF.withDayOfMonth(28));
        assertThat(pointer(row)).isEqualTo(REF.plusMonths(1).withDayOfMonth(15));
    }

    @Test
    @DisplayName("Ponteiro nulo (linha antiga): a data vem do dia de vencimento e a troca grava o ponteiro")
    void changesWithoutPointer() throws Exception {
        var t = tenant();
        UUID row = modality(t);
        adminJdbc().update("update enrollment_modality set next_charge_ref = null, next_charge_due_on = null, due_day = 1 where id = ?", row);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate next = today.getDayOfMonth() == 1 ? today : today.withDayOfMonth(1).plusMonths(1);
        var ok = change(row, "{\"dueOn\":\"" + next.withDayOfMonth(20) + "\"}", account(t, Role.COORDINATOR));
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select next_charge_ref from enrollment_modality where id = ?", LocalDate.class, row)).isEqualTo(next);
        assertThat(pointer(row)).isEqualTo(next.withDayOfMonth(20));
    }

    @Test
    @DisplayName("CA3, CA4, CA8: cancela com motivo, avança o ponteiro e a geração não recria; motivo inválido é 400")
    void cancelsTheNext() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID row = modality(t);
        for (String bad : new String[] {"{\"reason\":\"\"}", "{\"reason\":\"   \"}", "{\"reason\":\"" + "a".repeat(201) + "\"}", "{}", "{\"reason\":\"x\",\"referenceMonth\":\"zz\"}"}) {
            assertThat(cancel(row, bad, admin).getStatus()).as(bad).isEqualTo(400);
        }
        assertThat(charges(row)).isZero();
        assertThat(audits(t, "charge.canceled")).isZero();

        var ok = cancel(row, "{\"reason\":\" Aluno viajando \"}", admin);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getContentAsString()).get("referenceMonth").asText()).isEqualTo(REF.toString());
        var charge = adminJdbc().queryForMap("select status, reason, reference_month, canceled_by_account_id from charge where enrollment_modality_id = ?", row);
        assertThat(charge.get("status")).isEqualTo("CANCELED");
        assertThat(charge.get("reason")).isEqualTo("Aluno viajando");
        assertThat(charge.get("canceled_by_account_id")).isEqualTo(admin.accountId());
        assertThat(pointer(row)).isEqualTo(REF.plusMonths(1).withDayOfMonth(15));
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
        assertThat(audits(t, "charge.generation_adjusted")).isEqualTo(1);

        runJobOn(t, REF.withDayOfMonth(20));
        runJobOn(t, REF.withDayOfMonth(20));
        assertThat(adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ? and reference_month = ?", Long.class, row, REF)).isEqualTo(1);
        assertThat(audits(t, "charge.next_due_changed")).isZero();
    }

    @Test
    @DisplayName("CA7: duas chamadas simultâneas de cancelar o mesmo mês terminam com uma cobrança cancelada e 409 na outra")
    void concurrentCancel() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID row = modality(t);
        String body = "{\"reason\":\"Aluno viajando\",\"referenceMonth\":\"" + REF + "\"}";
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(() -> cancel(row, body, admin).getStatus(), () -> cancel(row, body, admin).getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) statuses.add(f.get());
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdown();
        }
        assertThat(charges(row)).isEqualTo(1);
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
        assertThat(audits(t, "charge.generation_adjusted")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA5: bolsa de 100% ou modalidade encerrada dá 422; modalidade de outro aluno dá 404; cobrança já existente do mês dá 409; nada muda")
    void notGenerating() throws Exception {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        String due = "{\"dueOn\":\"" + REF.withDayOfMonth(28) + "\"}", why = "{\"reason\":\"x\"}";
        UUID free = modality(t);
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(free), free, "PERCENT", 100, null, "ACTIVE");
        UUID ended = modality(t);
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id = ?", ended);
        for (UUID r : new UUID[] {free, ended}) {
            assertThat(change(r, due, admin).getStatus()).isEqualTo(422);
            assertThat(cancel(r, why, admin).getStatus()).isEqualTo(422);
        }
        UUID mine = modality(t), theirs = modality(t);
        assertThat(call(MockMvcRequestBuilders.patch("/api/v1/students/" + student(theirs) + "/modalities/" + mine + "/next-charge").contentType("application/json").content(due), admin)
                .getResponse().getStatus()).isEqualTo(404);
        BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student(mine), mine, REF, null, 20000, "OPEN");
        assertThat(cancel(mine, why, admin).getStatus()).isEqualTo(409);
        assertThat(charges(free) + charges(ended)).isZero();
        assertThat(charges(mine)).isEqualTo(1);
        assertThat(pointer(mine)).isEqualTo(REF.withDayOfMonth(15));
        assertThat(audits(t, "charge.canceled") + audits(t, "charge.generation_adjusted") + audits(t, "charge.next_due_changed")).isZero();
    }

    @Test
    @DisplayName("CA6: Professor, Aluno e Responsável 403; sem sessão 401; unidade fora do alcance e outra organização 404")
    void permissionsAndIsolation() throws Exception {
        var t = tenant();
        var other = tenant();
        UUID row = modality(t);
        String due = "{\"dueOn\":\"" + REF.withDayOfMonth(28) + "\"}", why = "{\"reason\":\"x\"}";
        assertThat(change(row, due, null).getStatus()).isEqualTo(401);
        assertThat(cancel(row, why, null).getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(change(row, due, account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(cancel(row, why, account(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        var elsewhere = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(UUID.randomUUID()));
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa', 'ACTIVE')", elsewhere.accountId(), "gen-" + elsewhere.accountId() + "@example.com");
        for (AccountPrincipal who : new AccountPrincipal[] {account(other, Role.ADMINISTRATOR), elsewhere}) {
            assertThat(change(row, due, who).getStatus()).isEqualTo(404);
            assertThat(cancel(row, why, who).getStatus()).isEqualTo(404);
        }
        assertThat(charges(row)).isZero();
        assertThat(pointer(row)).isEqualTo(REF.withDayOfMonth(15));
    }
}
