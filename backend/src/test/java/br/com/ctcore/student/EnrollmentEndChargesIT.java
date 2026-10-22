package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.billing.ChargeActionsBase;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/** RN-MAT-17 (CA13, CA14): a janela de encerrar modalidade lista as cobranças em aberto e decide manter ou cancelar cada uma, na mesma transação. */
class EnrollmentEndChargesIT extends ChargeActionsBase {
    private UUID charge(Tenant t, UUID student, UUID row, String month, String status) {
        return BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student, row, LocalDate.parse(month), null, 15000, status);
    }

    private String end(UUID s, UUID row, String charges, br.com.ctcore.identity.AccountPrincipal who, int status) throws Exception {
        String body = "{\"reason\":\"FINANCIAL\"" + (charges == null ? "" : ",\"charges\":" + charges) + "}";
        var r = send(HttpMethod.POST, "/students/" + s + "/modalities/" + row + "/end", body, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString();
    }

    private static String decision(UUID id, String action, String reason) {
        return "{\"chargeId\":\"" + id + "\",\"action\":\"" + action + "\"" + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    @Test
    @DisplayName("CA13: end-preview traz só as cobranças em aberto; encerrar cancelando uma e mantendo a outra deixa a modalidade ENDED, uma CANCELED com auditoria e a outra OPEN")
    void cancelsAndKeeps() throws Exception {
        Tenant t = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID s = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 15000);
        UUID one = charge(t, s, row, "2026-11-01", "OPEN"), two = charge(t, s, row, "2026-12-01", "OPEN");
        charge(t, s, row, "2026-10-01", "PAID");

        JsonNode preview = ok(send(HttpMethod.GET, "/students/" + s + "/modalities/" + row + "/end-preview", null, coordinator), 200);
        assertThat(preview.get("openCharges")).hasSize(2);
        assertThat(preview.get("openCharges").get(0).get("id").asText()).isEqualTo(one.toString());
        assertThat(preview.get("openCharges").get(0).get("amountCents").asLong()).isEqualTo(15000);
        assertThat(preview.get("openCharges").get(0).get("referenceMonth").asText()).isEqualTo("2026-11-01");
        assertThat(preview.get("openCharges").get(0).get("status").asText()).isNotBlank();

        end(s, row, "[" + decision(one, "CANCEL", "Saiu da academia") + "," + decision(two, "KEEP", null) + "]", coordinator, 200);
        assertThat(count("select count(*) from enrollment_modality where id = ? and status = 'ENDED'", row)).isEqualTo(1);
        assertThat(status(one)).isEqualTo("CANCELED");
        assertThat(status(two)).isEqualTo("OPEN");
        assertThat(audits(t, "charge.canceled")).isEqualTo(1);
        assertThat(audits(t, "student.modality_ended")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select reason from charge where id = ?", String.class, one)).isEqualTo("Saiu da academia");
    }

    @Test
    @DisplayName("CA13: CANCEL sem motivo, cobrança de outra modalidade, paga, repetida ou ação inválida dá 400 e a modalidade segue ativa")
    void rejects() throws Exception {
        Tenant t = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID s = student(t), other = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 15000);
        UUID otherRow = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), other, 10, 15000);
        UUID mine = charge(t, s, row, "2026-11-01", "OPEN"), paid = charge(t, s, row, "2026-10-01", "PAID"), foreign = charge(t, other, otherRow, "2026-11-01", "OPEN");
        for (String body : new String[] {"[" + decision(mine, "CANCEL", null) + "]", "[" + decision(mine, "CANCEL", " ") + "]", "[" + decision(foreign, "CANCEL", "x") + "]",
                "[" + decision(paid, "KEEP", null) + "]", "[" + decision(mine, "KEEP", null) + "," + decision(mine, "KEEP", null) + "]", "[" + decision(mine, "DROP", "x") + "]",
                "[null]", "[{\"action\":\"KEEP\"}]"}) {
            assertThat(end(s, row, body, coordinator, 400)).as(body).contains("\"fields\"");
        }
        assertThat(count("select count(*) from enrollment_modality where id = ? and status = 'ACTIVE'", row)).isEqualTo(1);
        assertThat(status(mine)).isEqualTo("OPEN");
        assertThat(audits(t, "student.modality_ended") + audits(t, "charge.canceled")).isZero();
    }

    @Test
    @DisplayName("Cobrança em conferência não é cobrança em aberto: 400 e a modalidade segue ativa")
    void inReview() throws Exception {
        Tenant t = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID s = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 15000);
        UUID mine = charge(t, s, row, "2026-11-01", "OPEN");
        adminJdbc().update("update charge set status = 'IN_REVIEW' where id = ?", mine);
        end(s, row, "[" + decision(mine, "CANCEL", "x") + "]", coordinator, 400);
        assertThat(count("select count(*) from enrollment_modality where id = ? and status = 'ACTIVE'", row)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA14: sem o campo charges (ou com lista vazia) as cobranças em aberto ficam mantidas e o encerramento é o da #69")
    void withoutCharges() throws Exception {
        Tenant t = tenant();
        var coordinator = account(t, Role.COORDINATOR);
        UUID s = student(t);
        UUID row = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s, 10, 15000);
        UUID mine = charge(t, s, row, "2026-11-01", "OPEN");
        end(s, row, null, coordinator, 200);
        assertThat(status(mine)).isEqualTo("OPEN");
        assertThat(count("select count(*) from enrollment_modality where id = ? and status = 'ENDED'", row)).isEqualTo(1);
        assertThat(audits(t, "charge.canceled")).isZero();

        UUID s2 = student(t);
        UUID row2 = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), s2, 10, 15000);
        UUID kept = charge(t, s2, row2, "2026-11-01", "OPEN");
        end(s2, row2, "[]", coordinator, 200);
        assertThat(status(kept)).isEqualTo("OPEN");
    }
}
