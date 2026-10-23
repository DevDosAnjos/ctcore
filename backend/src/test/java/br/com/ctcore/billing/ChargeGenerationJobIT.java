package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-COB-02, RN-COB-05, RNF-12, RNF-13: a tarefa diária grava tudo ou nada por unidade e não repete com execuções simultâneas. */
class ChargeGenerationJobIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired AuditService audit;
    @Autowired ScopedAudit scopedAudit;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
    }

    private ChargeGenerationJob job() {
        var clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
        return new ChargeGenerationJob(transactions, jdbc, new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, clock));
    }

    private UUID row(Tenant t, String birth) {
        return BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", birth), 15, 20000);
    }

    private long charges(UUID org) {
        return adminJdbc().queryForObject("select count(*) from charge where organization_id = ?", Long.class, org);
    }

    private long runs(UUID org) {
        return adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'charge.generation_run' and actor_type = 'SYSTEM'", Long.class, org);
    }

    @Test
    @DisplayName("CA15: a unidade com erro não grava nada, a outra grava e a execução informa as contagens; cada unidade que gravou tem sua auditoria")
    void unitIsAllOrNothing() {
        var good = tenant();
        var bad = tenant();
        row(good, "1990-01-01");
        UUID first = row(bad, "1990-01-01");
        UUID last = row(bad, "2015-03-03");
        // A modalidade válida vem antes da que falha, para provar que o que já tinha sido gravado é desfeito.
        adminJdbc().update("update enrollment_modality set id = ? where id = ?", new UUID(1L, 1L), first);
        adminJdbc().update("update enrollment_modality set id = ? where id = ?", new UUID(1L << 60, 1L), last);

        var result = job().runOnce();

        assertThat(result.failedUnits()).isGreaterThanOrEqualTo(1);
        assertThat(result.created()).isGreaterThanOrEqualTo(1);
        assertThat(charges(good.orgId())).isEqualTo(1);
        assertThat(runs(good.orgId())).isEqualTo(1);
        assertThat(charges(bad.orgId())).isZero();
        assertThat(runs(bad.orgId())).isZero();
        assertThat(adminJdbc().queryForObject("select next_charge_ref from enrollment_modality where id = ?", Object.class, new UUID(1L, 1L))).isNull();
    }

    @Test
    @DisplayName("CA11: duas execuções simultâneas criam uma mensalidade por modalidade e nenhuma falha; a agendada também roda")
    void concurrentRuns() throws Exception {
        var t = tenant();
        for (int i = 0; i < 12; i++) row(t, "1990-01-01");
        var job = job();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<MonthlyGeneration.Run>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<MonthlyGeneration.Run> call = () -> {
                    start.await();
                    return job.runOnce();
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            for (var f : futures) f.get();
        } finally {
            pool.shutdownNow();
        }
        assertThat(charges(t.orgId())).isEqualTo(12);
        assertThat(runs(t.orgId())).isEqualTo(2);
        assertThat(adminJdbc().queryForObject("select count(distinct (enrollment_modality_id, reference_month)) from charge where organization_id = ?", Long.class, t.orgId())).isEqualTo(12);

        job.run();
        assertThat(charges(t.orgId())).isEqualTo(12);
    }
}
