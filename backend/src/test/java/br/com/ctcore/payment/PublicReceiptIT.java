package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.student.EnrollmentFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-16: conferência pública do recibo, sem sessão, só com os campos permitidos. */
class PublicReceiptIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.clean(adminJdbc());
        EnrollmentFixtures.clean(adminJdbc());
    }

    private MockHttpServletResponse open(String code) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/receipts/" + code).secure(true).with(csrf())).andReturn().getResponse();
    }

    private String paid(Tenant t, UUID charge) {
        UUID payment = UUID.randomUUID();
        adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) "
                + "select ?, organization_id, id, student_id, unit_id, amount_cents, 'PIX_KEY', 'MANAGEMENT', timestamptz '2026-11-05 10:30:00-03' from charge where id = ?", payment, charge);
        String code = ReceiptCodes.next();
        adminJdbc().update("insert into receipt (id, organization_id, payment_id, unit_id, number, code) select ?, ?, ?, ?, coalesce(max(number), 6) + 1, ? from receipt where unit_id = ?",
                UUID.randomUUID(), t.orgId(), payment, t.unitId(), code, t.unitId());
        return code;
    }

    @Test
    @DisplayName("CA6: mensalidade abre sem sessão com unidade, número, referência 'Jiu-Jitsu · nov/2026', valor, data e iniciais; nada além")
    void monthly() throws Exception {
        var t = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza Lima");
        var enrolled = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", 0, null);
        String modality = adminJdbc().queryForObject("select m.name from enrollment_modality em join modality m on m.id = em.modality_id where em.id = ?", String.class, enrolled.row());
        UUID charge = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student, enrolled.row(), LocalDate.of(2026, 11, 1), null, 15000, "PAID");
        String code = paid(t, charge);
        var response = open(code);
        assertThat(response.getStatus()).isEqualTo(200);
        JsonNode b = json.readTree(response.getContentAsString());
        assertThat(b.fieldNames()).toIterable().containsExactlyInAnyOrder("unit", "number", "reference", "amountCents", "paidAt", "payerInitials");
        assertThat(b.get("unit").asText()).isEqualTo("Unit");
        assertThat(b.get("number").asInt()).isEqualTo(7);
        assertThat(b.get("reference").asText()).isEqualTo(modality + " · nov/2026");
        assertThat(b.get("amountCents").asLong()).isEqualTo(15000);
        assertThat(b.get("paidAt").asText()).startsWith("2026-11-05T13:30");
        assertThat(b.get("payerInitials").asText()).isEqualTo("M. L.");
        assertThat(response.getContentAsString()).doesNotContain("Maria", "PIX_KEY", student.toString(), charge.toString());
    }

    @Test
    @DisplayName("CA6: taxa e avulsa mostram a descrição; responsável financeiro dá as iniciais dele; um nome só dá uma inicial")
    void descriptionAndPayer() throws Exception {
        var t = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Ana Pereira");
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 5000, "PAID");
        assertThat(json.readTree(open(paid(t, charge)).getContentAsString()).get("reference").asText()).isEqualTo("Kimono");
        assertThat(json.readTree(open(paid(t, BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 5000, "PAID"))).getContentAsString())
                .get("payerInitials").asText()).isEqualTo("A. P.");

        UUID guardian = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Carlos', 'ACTIVE')", guardian, "recibo-" + guardian + "@example.com");
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Carlos', '12345678901', '11999990000')", guardian, t.orgId(), guardian);
        UUID byGuardian = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 5000, "PAID");
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, byGuardian);
        assertThat(json.readTree(open(paid(t, byGuardian)).getContentAsString()).get("payerInitials").asText()).isEqualTo("C.");
        adminJdbc().update("delete from receipt");
        adminJdbc().update("delete from payment");
        adminJdbc().update("delete from charge");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from account where id = ?", guardian);
    }

    @Test
    @DisplayName("CA6: código inexistente ou malformado dá 404; o link continua valendo meses depois; CA8: a rota só devolve as colunas da função")
    void unknownAndForever() throws Exception {
        var t = tenant();
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Rui Alves"), 100, "PAID");
        String code = paid(t, charge);
        adminJdbc().update("update payment set created_at = now() - interval '400 days' where charge_id = ?", charge);
        assertThat(open(code).getStatus()).isEqualTo(200);
        for (String bad : List.of("AAAAAAAAAAAAAAAA", "x", code.toLowerCase(), code + "A", "1234567890123456")) {
            assertThat(open(bad).getStatus()).as(bad).isEqualTo(404);
        }
        assertThat(adminJdbc().queryForList("select unnest(proargnames) from pg_proc where proname = 'public_receipt' and pronamespace = current_schema()::regnamespace", String.class))
                .containsExactly("p_code", "unit_name", "number", "reference", "amount_cents", "paid_at", "payer_initials");
    }
}
