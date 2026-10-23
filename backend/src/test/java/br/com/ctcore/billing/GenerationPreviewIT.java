package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-03, RN-COB-02: a prévia usa a mesma conta da tarefa, não grava nada e respeita permissão e alcance. */
class GenerationPreviewIT extends OrganizationBase {
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

    private MonthlyGeneration generation() {
        return new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    private MonthlyGeneration.Preview preview(AccountPrincipal who, UUID unit, Integer days) {
        try (var ignored = br.com.ctcore.isolation.OrganizationContext.open(who)) {
            return generation().preview(who, unit, days);
        }
    }

    private AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "gen-" + p.accountId() + "@example.com");
        return p;
    }

    private UUID row(Tenant t, int dueDay, long plan) {
        return BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", "1990-01-01"), dueDay, plan);
    }

    private UUID student(UUID row) {
        return adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, row);
    }

    @Test
    @DisplayName("CA16: os dias, quantidades, totais e linhas batem com o que a tarefa cria; a bolsa de 100% vem em skipped; o mês anterior soma as mensalidades; nada é gravado")
    void matchesTheJob() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID a = row(t, 8, 20000), b = row(t, 8, 15000), c = row(t, 10, 20000), d = row(t, 12, 30000), e = row(t, 15, 10000), full = row(t, 9, 20000), far = row(t, 20, 20000);
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(c), c, "PERCENT", 25, null, "ACTIVE");
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(full), full, "PERCENT", 100, null, "ACTIVE");
        BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student(a), a, LocalDate.parse("2026-09-01"), null, 10000, "OPEN");
        BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student(b), b, LocalDate.parse("2026-09-01"), null, 5000, "PAID");
        BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student(d), d, LocalDate.parse("2026-09-01"), null, 7000, "CANCELED");
        long before = adminJdbc().queryForObject("select count(*) from charge", Long.class);

        var preview = preview(admin, t.unitId(), 10);

        assertThat(adminJdbc().queryForObject("select count(*) from charge", Long.class)).isEqualTo(before);
        assertThat(adminJdbc().queryForObject("select count(*) from enrollment_modality where next_charge_ref is not null", Long.class)).isZero();
        assertThat(preview.until()).isEqualTo(LocalDate.parse("2026-10-15"));
        assertThat(preview.days()).extracting(MonthlyGeneration.Day::dueOn).extracting(LocalDate::toString).containsExactly("2026-10-08", "2026-10-10", "2026-10-12", "2026-10-15");
        assertThat(preview.days()).extracting(MonthlyGeneration.Day::count).containsExactly(2, 1, 1, 1);
        assertThat(preview.days()).extracting(MonthlyGeneration.Day::totalCents).containsExactly(35000L, 15000L, 30000L, 10000L);
        assertThat(preview.count()).isEqualTo(5);
        assertThat(preview.totalCents()).isEqualTo(90000);
        assertThat(preview.previousMonthTotalCents()).isEqualTo(15000);
        assertThat(preview.skipped()).hasSize(1);
        assertThat(preview.skipped().get(0).reason()).isEqualTo("FULL_BENEFIT");
        assertThat(preview.skipped().get(0).student()).isEqualTo("Aluno");
        var discounted = preview.days().get(1).lines().get(0);
        assertThat(discounted.discountCents()).isEqualTo(5000);
        assertThat(discounted.amountCents()).isEqualTo(15000);
        assertThat(discounted.plan()).isNotBlank();

        // O que a tarefa cria depois é exatamente o previsto.
        transactions.provision(t.orgId(), () -> generation().runUnit(t.orgId(), t.unitId()));
        List<String> created = new ArrayList<>();
        for (var m : adminJdbc().queryForList("select enrollment_modality_id, due_on, amount_cents from charge where reference_month = date '2026-10-01' order by due_on, amount_cents")) {
            created.add(m.get("due_on") + ":" + m.get("amount_cents"));
        }
        List<String> previewed = new ArrayList<>();
        for (var day : preview.days()) {
            day.lines().stream().sorted((x, y) -> Long.compare(x.amountCents(), y.amountCents())).forEach(l -> previewed.add(day.dueOn() + ":" + l.amountCents()));
        }
        assertThat(created).containsExactlyElementsOf(previewed);
        assertThat(far).isNotNull();
        assertThat(e).isNotNull();
    }

    @Test
    @DisplayName("CA16: days fora de 1 a 60 é 400; Professor, Aluno e Responsável 403; sem sessão 401; unidade fora do alcance vem vazia; padrão de 10 dias")
    void rules() throws Exception {
        var t = tenant();
        var other = tenant();
        row(t, 8, 20000);
        var admin = account(t, Role.ADMINISTRATOR);
        String url = "/api/v1/billing/generation-preview?unitId=" + t.unitId();

        assertThat(call(MockMvcRequestBuilders.get(url + "&days=0"), admin).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(MockMvcRequestBuilders.get(url + "&days=61"), admin).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(MockMvcRequestBuilders.get(url), null).getResponse().getStatus()).isEqualTo(401);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(call(MockMvcRequestBuilders.get(url), account(t, role)).getResponse().getStatus()).as(role.name()).isEqualTo(403);
        }
        var ok = call(MockMvcRequestBuilders.get(url + "&days=60"), admin).getResponse();
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(json.readTree(ok.getContentAsString()).get("count").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(call(MockMvcRequestBuilders.get(url), account(t, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(200);

        var foreign = call(MockMvcRequestBuilders.get("/api/v1/billing/generation-preview?unitId=" + other.unitId()), admin).getResponse();
        assertThat(foreign.getStatus()).isEqualTo(200);
        assertThat(json.readTree(foreign.getContentAsString()).get("count").asInt()).isZero();
        assertThat(preview(account(t, Role.COORDINATOR), other.unitId(), null).count()).isZero();
        assertThat(preview(admin, t.unitId(), null).until()).isEqualTo(LocalDate.parse("2026-10-15"));
    }

    @Test
    @DisplayName("CA9, CA11: só a primeira mensalidade de cada modalidade é ajustável; bolsa de 100% vem em skipped e a modalidade encerrada não aparece")
    void marksAdjustable() {
        var t = tenant();
        var admin = account(t, Role.ADMINISTRATOR);
        UUID a = row(t, 8, 20000), full = row(t, 9, 20000), ended = row(t, 10, 20000);
        BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), student(full), full, "PERCENT", 100, null, "ACTIVE");
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id = ?", ended);

        var preview = preview(admin, t.unitId(), 60);

        var lines = preview.days().stream().flatMap(d -> d.lines().stream()).toList();
        assertThat(lines).hasSize(2).allMatch(l -> l.modalityRowId().equals(a));
        assertThat(lines).extracting(MonthlyGeneration.Line::adjustable).containsExactly(true, false);
        assertThat(preview.skipped()).extracting(MonthlyGeneration.Skipped::reason).contains("FULL_BENEFIT");
        assertThat(preview.skipped()).noneMatch(s -> s.studentId().equals(student(ended)));
    }
}
