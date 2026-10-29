package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-POR-05, RN-POR-01, RN-POR-02, RN-POR-05, RN-POR-08, RN-COB-16, RN-INA-08: Financeiro do portal (abertas, família, histórico, recibo), com banco real. */
class PortalFinanceIT extends PortalTermBase {
    @AfterEach
    void cleanFinance() {
        BillingFixtures.clean(adminJdbc());
    }

    private LocalDate today() {
        return adminJdbc().queryForObject("select (now() at time zone 'America/Sao_Paulo')::date", LocalDate.class);
    }

    private UUID author() {
        return adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst().orElseGet(() -> account("autor@portal.example"));
    }

    private EnrollmentFixtures.Enrolled enroll(Tenant t, UUID student, String status, int kind) {
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, kind, null);
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, author());
        return e;
    }

    private UUID charge(Tenant t, UUID unit, UUID student, LocalDate due, long plan, long benefit, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, plan_cents, benefit_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'ONE_OFF', 'Kimono', ?, ?, ?, ?, ?)", id, t.orgId(), unit, student, due, plan, benefit, plan - benefit, status);
        return id;
    }

    private UUID charge(Tenant t, UUID student, int dueInDays, long cents, String status) {
        return charge(t, t.unitId(), student, today().plusDays(dueInDays), cents, 0, status);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?)", t.orgId(), id);
        return id;
    }

    /** Pagamento confirmado e recibo de uma cobrança (a cobrança passa a paga); devolve o código. */
    private String paid(Tenant t, UUID charge, String method, String paidAt) {
        UUID payment = UUID.randomUUID();
        adminJdbc().update("update charge set status = 'PAID' where id = ?", charge);
        adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) "
                + "select ?, organization_id, id, student_id, unit_id, amount_cents, ?, 'MANAGEMENT', ?::timestamptz from charge where id = ?", payment, method, paidAt, charge);
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        adminJdbc().update("insert into receipt (id, organization_id, payment_id, unit_id, number, code) select ?, ?, ?, unit_id, "
                + "coalesce((select max(number) from receipt where unit_id = c.unit_id), 0) + 1, ? from charge c where c.id = ?", UUID.randomUUID(), t.orgId(), payment, code, charge);
        return code;
    }

    private JsonNode finance(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(portal(t, account, role), "/finance" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    @Test
    @DisplayName("CA1, CA13: abertas e em conferência, vencida primeiro e o resto por vencimento, com a composição guardada; paga fica fora; referência da mensalidade; sem chave de arquivo")
    void open() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        UUID overdue = charge(t, t.unitId(), s, today().minusDays(5), 17000, 2000, "OPEN"), soon = charge(t, s, 2, 5000, "OPEN"), review = charge(t, s, 5, 7000, "IN_REVIEW");
        UUID far = charge(t, s, 20, 9000, "OPEN");
        UUID paid = charge(t, s, -2, 9000, "OPEN");
        paid(t, paid, "CASH", "2026-01-05T10:00:00Z");
        UUID monthly = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, enrollment_modality_id, reference_month, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'MONTHLY', ?, date '2026-11-01', ?, 8000, 8000, 'OPEN')", monthly, t.orgId(), t.unitId(), s, e.row(), today().plusDays(30));
        String modality = adminJdbc().queryForObject("select m.name from enrollment_modality em join modality m on m.id = em.modality_id where em.id = ?", String.class, e.row());

        var response = get(portal(t, acc, Role.STUDENT), "/finance");
        JsonNode view = body(response);
        JsonNode open = view.get("open");
        assertThat(ids(open)).containsExactly(overdue.toString(), soon.toString(), review.toString(), far.toString(), monthly.toString());
        assertThat(open.get(0).get("situation").asText()).isEqualTo("OVERDUE");
        assertThat(open.get(0).get("planCents").asLong()).isEqualTo(17000);
        assertThat(open.get(0).get("benefitCents").asLong()).isEqualTo(2000);
        assertThat(open.get(0).get("amountCents").asLong()).isEqualTo(15000);
        assertThat(open.get(0).get("referenceLabel").asText()).isEqualTo("Kimono");
        assertThat(open.get(1).get("situation").asText()).isEqualTo("DUE_SOON");
        assertThat(open.get(2).get("situation").asText()).isEqualTo("IN_REVIEW");
        assertThat(open.get(2).get("canPay").asBoolean()).isFalse();
        assertThat(open.get(3).get("situation").asText()).isEqualTo("PENDING");
        assertThat(open.get(3).get("canPay").asBoolean()).isTrue();
        assertThat(open.get(4).get("referenceLabel").asText()).isEqualTo(modality + " · nov/2026");
        assertThat(open.get(4).get("modality").get("name").asText()).isEqualTo(modality);
        assertThat(open.get(0).get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(view.get("scope").asText()).isEqualTo("STUDENT");
        assertThat(view.get("history")).hasSize(1);
        assertThat(response.getContentAsString()).doesNotContain("file_key", "fileKey", "payment-proof-");

        // a composição é a guardada: mudar o valor do plano depois não muda a cobrança
        adminJdbc().update("update plan set value_cents = 1 where id = ?", e.plan());
        assertThat(finance(t, acc, Role.STUDENT, "").get("open").get(0).get("planCents").asLong()).isEqualTo(17000);
    }

    @Test
    @DisplayName("CA2: scope=FAMILY traz as abertas dos filhos em unidades diferentes, com aluno e unidade, total da família do servidor; filho que não paga e histórico fora")
    void family() throws Exception {
        Tenant t = tenant();
        UUID second = unit(t, "Segunda");
        UUID acc = account("responsavel@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null), notPaid = student(t, "Filho C", "2015-01-01", null);
        guardian(t, acc, "52998224725", true, a, b);
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) select ?, ?, id, false from guardian where account_id = ?", t.orgId(), notPaid, acc);
        for (UUID s : new UUID[] {a, b, notPaid}) enroll(t, s, "ACTIVE", 0);
        UUID forA = charge(t, t.unitId(), a, today().minusDays(2), 10000, 0, "OPEN"), forB = charge(t, second, b, today().plusDays(1), 5000, 0, "OPEN");
        charge(t, notPaid, -1, 99900, "OPEN");
        paid(t, charge(t, a, -40, 3000, "OPEN"), "PIX_KEY", "2026-01-05T10:00:00Z");

        JsonNode view = finance(t, acc, Role.GUARDIAN, "?scope=FAMILY");
        assertThat(view.get("scope").asText()).isEqualTo("FAMILY");
        assertThat(ids(view.get("open"))).containsExactly(forA.toString(), forB.toString());
        assertThat(view.get("open").get(0).get("studentName").asText()).isEqualTo("Filho A");
        assertThat(view.get("open").get(1).get("studentName").asText()).isEqualTo("Filho B");
        assertThat(view.get("open").get(1).get("unit").get("id").asText()).isEqualTo(second.toString());
        assertThat(view.get("familyOpenCents").asLong()).isEqualTo(15000);
        assertThat(view.get("students")).hasSize(3);
        assertThat(view.get("history")).isEmpty();

        JsonNode one = finance(t, acc, Role.GUARDIAN, "?studentId=" + b);
        assertThat(ids(one.get("open"))).containsExactly(forB.toString());
        assertThat(one.get("selectedStudentId").asText()).isEqualTo(b.toString());
    }

    @Test
    @DisplayName("CA3: histórico por ano só com as situações finais, meio e data nas pagas, recibo na paga, years; ano inválido 400")
    void history() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        enroll(t, s, "ACTIVE", 0);
        int year = today().getYear();
        UUID paid25 = charge(t, t.unitId(), s, LocalDate.of(2025, 3, 10), 8000, 0, "OPEN");
        String code25 = paid(t, paid25, "PIX_KEY", "2025-03-09T15:00:00Z");
        UUID paidNow = charge(t, t.unitId(), s, LocalDate.of(year, 1, 10), 9000, 0, "OPEN");
        paid(t, paidNow, "CASH", year + "-01-12T15:00:00Z");
        UUID canceled = charge(t, t.unitId(), s, LocalDate.of(year, 2, 10), 9000, 0, "CANCELED");
        UUID renegotiated = charge(t, t.unitId(), s, LocalDate.of(year, 3, 10), 9000, 0, "RENEGOTIATED");
        UUID open = charge(t, t.unitId(), s, LocalDate.of(year, 4, 10), 9000, 0, "OPEN");

        JsonNode def = finance(t, acc, Role.STUDENT, "");
        assertThat(def.get("year").asInt()).isEqualTo(year);
        assertThat(ids(def.get("history"))).containsExactly(renegotiated.toString(), canceled.toString(), paidNow.toString());
        assertThat(ids(def.get("history"))).doesNotContain(open.toString());
        assertThat(def.get("years")).extracting(JsonNode::asInt).containsExactly(year, 2025);
        JsonNode paidRow = def.get("history").get(2);
        assertThat(paidRow.get("status").asText()).isEqualTo("PAID");
        assertThat(paidRow.get("method").asText()).isEqualTo("CASH");
        assertThat(paidRow.get("paidAt").asText()).startsWith(year + "-01-12T15:00");
        assertThat(paidRow.get("receipt").get("number").asInt()).isEqualTo(2);
        assertThat(def.get("history").get(1).get("method").isNull()).isTrue();
        assertThat(def.get("history").get(1).get("receipt").isNull()).isTrue();
        assertThat(def.get("open")).hasSize(1);

        JsonNode old = finance(t, acc, Role.STUDENT, "?year=2025");
        assertThat(old.get("year").asInt()).isEqualTo(2025);
        assertThat(ids(old.get("history"))).containsExactly(paid25.toString());
        assertThat(old.get("history").get(0).get("receipt").get("code").asText()).isEqualTo(code25);
        assertThat(old.get("history").get(0).get("method").asText()).isEqualTo("PIX_KEY");

        assertThat(get(portal(t, acc, Role.STUDENT), "/finance?year=1999").getStatus()).isEqualTo(400);
        assertThat(get(portal(t, acc, Role.STUDENT), "/finance?year=abc").getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA6: studentId de outra conta ou organização é 404; não financeiro vem com listas vazias; gestão 403; sem sessão 401; termo pendente 403; sem studentId, o primeiro cartão")
    void access() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x), sy = student(a, "Aluno Y", "1990-01-01", y), sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(a, sx, "ACTIVE", 0);
        enroll(a, sy, "ACTIVE", 0);
        enroll(b, sb, "ACTIVE", 0);
        charge(a, sy, -1, 100, "OPEN");
        charge(b, sb, -1, 100, "OPEN");
        assertThat(get(portal(a, x, Role.STUDENT), "/finance?studentId=" + sy).getStatus()).isEqualTo(404);
        assertThat(get(portal(a, x, Role.STUDENT), "/finance?studentId=" + sb).getStatus()).isEqualTo(404);
        JsonNode own = finance(a, x, Role.STUDENT, "");
        assertThat(own.get("selectedStudentId").asText()).isEqualTo(sx.toString());
        assertThat(own.get("open")).isEmpty();
        assertThat(own.get("familyOpenCents").asLong()).isZero();

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var r = get(portal(a, UUID.randomUUID(), role), "/finance");
            assertThat(r.getStatus()).isEqualTo(403);
            assertThat(body(r).get("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(get(null, "/finance").getStatus()).isEqualTo(401);

        UUID parent = account("pai@portal.example");
        UUID minor = student(a, "Menor", "2015-01-01", null);
        guardian(a, parent, "52998224725", false, minor);
        enroll(a, minor, "ACTIVE", 0);
        charge(a, minor, -1, 8000, "OPEN");
        paid(a, charge(a, minor, -50, 8000, "OPEN"), "CASH", "2026-01-05T10:00:00Z");
        JsonNode view = finance(a, parent, Role.GUARDIAN, "?studentId=" + minor);
        assertThat(view.get("open")).isEmpty();
        assertThat(view.get("history")).isEmpty();
        assertThat(view.get("years")).isEmpty();
        assertThat(view.get("students").get(0).get("openCents").asLong()).isZero();

        adminJdbc().update("delete from term_acceptance where student_id = ?", sx);
        publish(a, "ENROLLMENT", 1, true);
        var blocked = get(portal(a, x, Role.STUDENT), "/finance");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }

    @Test
    @DisplayName("CA7: portal reduzido traz as abertas e o histórico com reduced verdadeiro")
    void reduced() throws Exception {
        Tenant t = tenant();
        UUID acc = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", acc);
        enroll(t, s, "ENDED", 0);
        charge(t, s, -3, 12000, "OPEN");
        paid(t, charge(t, t.unitId(), s, today(), 5000, 0, "OPEN"), "CASH", "2026-01-05T10:00:00Z");
        JsonNode view = finance(t, acc, Role.STUDENT, "");
        assertThat(view.get("reduced").asBoolean()).isTrue();
        assertThat(view.get("open")).hasSize(1);
        assertThat(view.get("open").get(0).get("canPay").asBoolean()).isTrue();
        assertThat(view.get("history")).hasSize(1);
    }

    @Test
    @DisplayName("CA4, CA7: receiptPdf: o pagador baixa o PDF com CPF mascarado, também reduzido; outra conta, outra organização, código inexistente e não pagador dão o mesmo 404; gestão 403; sem sessão 401")
    void receiptPdf() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID acc = account("pagador@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        enroll(t, s, "ENDED", 0);
        String code = paid(t, charge(t, s, -3, 12000, "OPEN"), "PIX_KEY", "2026-01-05T10:00:00Z");

        var ok = get(portal(t, acc, Role.STUDENT), "/receipts/" + code + "/pdf");
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getContentType()).isEqualTo("application/pdf");
        assertThat(ok.getContentAsByteArray()).startsWith('%', 'P', 'D', 'F');
        assertThat(ok.getHeader("Cache-Control")).contains("no-store");

        UUID otherAcc = account("outro@portal.example");
        enroll(t, student(t, "Aluno Dois", "1990-01-01", otherAcc), "ACTIVE", 0);
        UUID outsider = account("fora@portal.example");
        enroll(other, student(other, "Aluno Fora", "1990-01-01", outsider), "ACTIVE", 0);
        UUID parent = account("pai@portal.example");
        UUID minor = student(t, "Menor", "2015-01-01", null);
        guardian(t, parent, "52998224725", false, minor);
        enroll(t, minor, "ACTIVE", 0);
        String minorCode = paid(t, charge(t, minor, -3, 5000, "OPEN"), "CASH", "2026-01-05T10:00:00Z");

        assertThat(get(portal(t, otherAcc, Role.STUDENT), "/receipts/" + code + "/pdf").getStatus()).isEqualTo(404);
        assertThat(get(portal(other, outsider, Role.STUDENT), "/receipts/" + code + "/pdf").getStatus()).isEqualTo(404);
        assertThat(get(portal(t, acc, Role.STUDENT), "/receipts/AAAAAAAAAAAAAAAA/pdf").getStatus()).isEqualTo(404);
        assertThat(get(portal(t, parent, Role.GUARDIAN), "/receipts/" + minorCode + "/pdf").getStatus()).isEqualTo(404);
        assertThat(get(portal(t, UUID.randomUUID(), Role.ADMINISTRATOR), "/receipts/" + code + "/pdf").getStatus()).isEqualTo(403);
        assertThat(get(null, "/receipts/" + code + "/pdf").getStatus()).isEqualTo(401);
    }
}
