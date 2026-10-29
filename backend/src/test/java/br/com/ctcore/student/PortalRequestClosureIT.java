package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditService;
import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.billing.ChargeGenerationJob;
import br.com.ctcore.billing.MonthlyGeneration;
import br.com.ctcore.identity.Role;
import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.permission.Permissions;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-MAT-18 (#86): pedido de encerramento aberto mostra a modalidade como solicitada, para de gerar cobrança e, ao encerrar, cancela o que vence depois do pedido. */
class PortalRequestClosureIT extends PortalRequestBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired Permissions permissions;
    @Autowired AuditService audit;
    @Autowired ScopedAudit scopedAudit;

    @AfterEach
    void cleanCharges() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from benefit");
        adminJdbc().update("delete from portal_request");
        adminJdbc().update("delete from account where email like 'act-%@example.com'");
    }

    private UUID charge(Tenant t, UUID student, UUID row, String month, String dueOn) {
        UUID id = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student, row, LocalDate.parse(month), null, 15000, "OPEN");
        adminJdbc().update("update charge set due_on = ?::date where id = ?", dueOn, id);
        return id;
    }

    private com.fasterxml.jackson.databind.JsonNode ok(org.springframework.mock.web.MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private br.com.ctcore.identity.AccountPrincipal coordinator(Tenant t) {
        var p = who(t, Role.COORDINATOR);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora Sintética', 'ACTIVE')", p.accountId(), "act-" + p.accountId() + "@example.com");
        return p;
    }

    private String status(UUID charge) {
        return adminJdbc().queryForObject("select status from charge where id = ?", String.class, charge);
    }

    @Test
    @DisplayName("CA11, CA10: com pedido de encerramento aberto a matrícula traz closureRequestedAt e o aluno segue Ativo; a geração mensal e a adiantada pulam a modalidade e geram as outras")
    void requestedAndNoCharges() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup closing = setup(t, s, 0);
        Setup other = setup(t, s, 1);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);
        var coordinator = coordinator(t);

        var before = ok(call(MockMvcRequestBuilders.get("/api/v1/students/" + s + "/enrollment"), coordinator).getResponse());
        assertThat(before.get("modalities").findValuesAsText("closureRequestedAt")).containsOnly("null");

        assertThat(request(who, s, closure(closing, "FINANCIAL")).getStatus()).isEqualTo(201);

        var enrollment = ok(call(MockMvcRequestBuilders.get("/api/v1/students/" + s + "/enrollment"), coordinator).getResponse());
        assertThat(enrollment.get("status").asText()).isEqualTo("ACTIVE");
        for (var m : enrollment.get("modalities")) {
            boolean mine = m.get("id").asText().equals(closing.row().toString());
            assertThat(m.get("status").asText()).isEqualTo("ACTIVE");
            assertThat(m.get("closureRequestedAt").isNull()).isEqualTo(!mine);
        }

        var generation = new MonthlyGeneration(jdbc, transactions, permissions, audit, scopedAudit, Clock.fixed(Instant.parse("2026-11-01T12:00:00Z"), ZoneOffset.UTC));
        new ChargeGenerationJob(transactions, jdbc, generation).runOnce();
        assertThat(adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ?", Integer.class, closing.row())).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ?", Integer.class, other.row())).isEqualTo(1);

        var advance = call(MockMvcRequestBuilders.post("/api/v1/students/" + s + "/modalities/" + closing.row() + "/charges/advance").contentType("application/json")
                .content("{\"count\":1}"), coordinator).getResponse();
        assertThat(advance.getStatus()).isEqualTo(422);
        assertThat(adminJdbc().queryForObject("select count(*) from charge where enrollment_modality_id = ?", Integer.class, closing.row())).isZero();
    }

    @Test
    @DisplayName("CA13: a gestão encerra pelo fluxo existente; o pedido vira Encerrado, o que vence depois do pedido é cancelado e o anterior segue a decisão; end-preview marca afterRequest")
    void managerEnds() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup m = setup(t, s, 0);
        accepted(t, s, acc);
        String requestId = body(request(portal(t, acc, Role.STUDENT), s, closure(m, "FINANCIAL"))).get("id").asText();
        String today = adminJdbc().queryForObject("select (now() at time zone 'America/Sao_Paulo')::date::text", String.class);
        UUID earlier = charge(t, s, m.row(), "2026-09-01", LocalDate.parse(today).minusDays(3).toString());
        UUID later = charge(t, s, m.row(), "2026-10-01", LocalDate.parse(today).plusDays(4).toString());
        var coordinator = coordinator(t);

        var preview = ok(call(MockMvcRequestBuilders.get("/api/v1/students/" + s + "/modalities/" + m.row() + "/end-preview"), coordinator).getResponse());
        for (var c : preview.get("openCharges")) assertThat(c.get("afterRequest").asBoolean()).isEqualTo(c.get("id").asText().equals(later.toString()));

        String decisions = "[{\"chargeId\":\"" + earlier + "\",\"action\":\"KEEP\"},{\"chargeId\":\"" + later + "\",\"action\":\"KEEP\"}]";
        var ended = call(MockMvcRequestBuilders.post("/api/v1/students/" + s + "/modalities/" + m.row() + "/end").contentType("application/json")
                .content("{\"reason\":\"FINANCIAL\",\"charges\":" + decisions + "}"), coordinator).getResponse();
        assertThat(ended.getStatus()).as(ended.getContentAsString()).isEqualTo(200);

        assertThat(status(later)).isEqualTo("CANCELED");
        assertThat(adminJdbc().queryForObject("select reason from charge where id = ?", String.class, later)).isEqualTo(Enrollments.AFTER_REQUEST_REASON);
        assertThat(status(earlier)).isEqualTo("OPEN");
        assertThat(adminJdbc().queryForObject("select status from portal_request where id = ?::uuid", String.class, requestId)).isEqualTo("CLOSED");
        assertThat(adminJdbc().queryForObject("select decided_by_account_id from portal_request where id = ?::uuid", UUID.class, requestId)).isEqualTo(coordinator.accountId());
        assertThat(auditsOf("portal_request.closed", requestId)).isEqualTo(1);
    }
}
