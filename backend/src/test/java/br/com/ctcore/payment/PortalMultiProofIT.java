package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.student.PortalRequestBaseAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-POR-05 (RN-COB-13): pagar várias cobranças da mesma unidade com um só comprovante, tudo ou nada, com banco e armazenamento reais. */
class PortalMultiProofIT extends PortalRequestBaseAccess {
    @Autowired ObjectMapper mapper;

    @AfterEach
    void cleanProofs() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from notification");
    }

    private static byte[] png(int size) {
        byte[] b = new byte[size];
        b[0] = (byte) 0x89;
        b[1] = 'P';
        b[2] = 'N';
        b[3] = 'G';
        return b;
    }

    private record Fx(Tenant t, UUID account, AccountPrincipal who, List<UUID> charges) {}

    /** Responsável financeiro de dois filhos (um deles com 2 cobranças), todas abertas na unidade do tenant, com chave Pix. */
    private Fx family(String email) {
        Tenant t = tenant();
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        UUID acc = account(email);
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null);
        guardian(t, acc, "52998224725", true, a, b);
        accepted(t, a, acc);
        accepted(t, b, acc);
        List<UUID> charges = List.of(BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), a, 10000, "OPEN"), BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), a, 5000, "OPEN"),
                BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), b, 2500, "OPEN"));
        return new Fx(t, acc, portal(t, acc, Role.GUARDIAN), charges);
    }

    private UUID otherUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Segunda', ?)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, pix_key) values (?, ?, '+5511900000000')", t.orgId(), id);
        return id;
    }

    private MockHttpServletResponse send(AccountPrincipal who, List<UUID> ids, byte[] content) throws Exception {
        MockMultipartHttpServletRequestBuilder req = MockMvcRequestBuilders.multipart("/api/v1/portal/charges/proof");
        if (!ids.isEmpty()) req.param("chargeIds", ids.stream().map(UUID::toString).toArray(String[]::new));
        if (content != null) req.file(new MockMultipartFile("file", "comprovante.png", "image/png", content));
        return call(req, who).getResponse();
    }

    private MockHttpServletResponse instructions(AccountPrincipal who, String ids) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/portal/charges/pix-instructions" + (ids == null ? "" : "?ids=" + ids)), who).getResponse();
    }

    private static String join(List<UUID> ids) {
        return String.join(",", ids.stream().map(UUID::toString).toList());
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private void assertUntouched(List<UUID> charges) {
        for (UUID c : charges) assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, c)).isIn("OPEN", "IN_REVIEW");
        assertThat(count("select count(*) from payment_proof")).isZero();
    }

    @Test
    @DisplayName("CA5: instruções somam o total das 3; um envio põe as 3 em IN_REVIEW, grava 3 comprovantes PENDING com o mesmo arquivo e 3 auditorias sem a chave")
    void sendsTogether() throws Exception {
        Fx f = family("resp@portal.example");
        JsonNode info = mapper.readTree(instructions(f.who(), join(f.charges())).getContentAsString());
        assertThat(info.get("pixKey").asText()).isEqualTo("+5511987654321");
        assertThat(info.get("totalCents").asLong()).isEqualTo(17500);
        assertThat(info.get("charges")).hasSize(3);
        assertThat(info.get("unit").asText()).isEqualTo("Unit");

        manager(f.t(), Role.ADMINISTRATOR, f.t().unitId());
        var response = send(f.who(), f.charges(), png(50_000));
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
        JsonNode sent = mapper.readTree(response.getContentAsString());
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0).get("status").asText()).isEqualTo("PENDING");
        for (UUID c : f.charges()) assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, c)).isEqualTo("IN_REVIEW");
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(3);
        assertThat(count("select count(distinct file_key) from payment_proof")).isEqualTo(1);
        assertThat(count("select count(*) from audit_log where action = 'payment_proof.sent' and organization_id = ?", f.t().orgId())).isEqualTo(3);
        String key = adminJdbc().queryForObject("select file_key from payment_proof limit 1", String.class);
        assertThat(adminJdbc().queryForList("select after_data::text from audit_log where action = 'payment_proof.sent' and organization_id = ?", String.class, f.t().orgId())).noneMatch(a -> a.contains(key));
        assertThat(response.getContentAsString()).doesNotContain(key);
        assertThat(count("select count(*) from notification where organization_id = ?", f.t().orgId())).isEqualTo(3);
    }

    @Test
    @DisplayName("CA5: unidades diferentes, uma em conferência, id repetido, mais de 20, lista vazia, aluno de outra conta, arquivo inválido ou grande: recusa e nenhuma muda")
    void refusals() throws Exception {
        Fx f = family("resp@portal.example");
        UUID second = otherUnit(f.t());
        UUID student = adminJdbc().queryForObject("select student_id from charge where id = ?", UUID.class, f.charges().get(0));
        UUID elsewhere = BillingFixtures.oneOff(adminJdbc(), f.t().orgId(), second, student, 700, "OPEN");
        List<UUID> all = new ArrayList<>(f.charges());
        all.add(elsewhere);

        var units = send(f.who(), List.of(f.charges().get(0), elsewhere), png(100));
        assertThat(units.getStatus()).isEqualTo(422);
        assertThat(units.getContentAsString()).contains("chargeIds");
        assertThat(instructions(f.who(), f.charges().get(0) + "," + elsewhere).getStatus()).isEqualTo(422);
        assertUntouched(all);

        assertThat(send(f.who(), List.of(f.charges().get(0), f.charges().get(0)), png(100)).getStatus()).isEqualTo(422);
        assertThat(send(f.who(), List.of(), png(100)).getStatus()).isEqualTo(422);
        assertThat(instructions(f.who(), null).getStatus()).isEqualTo(422);
        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 21; i++) many.add(UUID.randomUUID());
        assertThat(send(f.who(), many, png(100)).getStatus()).isEqualTo(422);
        assertThat(send(f.who(), f.charges(), "GIF89a............".getBytes()).getStatus()).isEqualTo(422);
        assertThat(send(f.who(), f.charges(), png(5 * 1024 * 1024 + 1)).getStatus()).isEqualTo(422);
        assertThat(send(f.who(), f.charges(), null).getStatus()).isEqualTo(422);
        assertUntouched(all);

        adminJdbc().update("update charge set status = 'IN_REVIEW' where id = ?", f.charges().get(2));
        assertThat(send(f.who(), f.charges(), png(100)).getStatus()).isEqualTo(409);
        assertThat(instructions(f.who(), join(f.charges())).getStatus()).isEqualTo(409);
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, f.charges().get(0))).isEqualTo("OPEN");
        assertThat(count("select count(*) from payment_proof")).isZero();

        UUID stranger = account("estranho@portal.example");
        UUID s = enrolledAdult(f.t(), "Outro Aluno", stranger);
        accepted(f.t(), s, stranger);
        assertThat(send(portal(f.t(), stranger, Role.STUDENT), List.of(f.charges().get(0)), png(100)).getStatus()).isEqualTo(404);
        assertThat(send(portal(f.t(), stranger, Role.STUDENT), f.charges().subList(0, 2), png(100)).getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from payment_proof")).isZero();

        assertThat(send(portal(f.t(), UUID.randomUUID(), Role.ADMINISTRATOR), f.charges(), png(100)).getStatus()).isEqualTo(403);
        assertThat(send(null, f.charges(), png(100)).getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("CA5: não financeiro e outra organização não pagam; uma falha no meio da lista não deixa nenhuma em conferência")
    void allOrNothing() throws Exception {
        Fx f = family("resp@portal.example");
        UUID notFinancial = account("naofinanceiro@portal.example");
        UUID kid = student(f.t(), "Filho Z", "2014-01-01", null);
        guardian(f.t(), notFinancial, "11144477735", false, kid);
        accepted(f.t(), kid, notFinancial);
        UUID theirs = BillingFixtures.oneOff(adminJdbc(), f.t().orgId(), f.t().unitId(), kid, 900, "OPEN");
        assertThat(send(portal(f.t(), notFinancial, Role.GUARDIAN), List.of(theirs), png(100)).getStatus()).isEqualTo(403);

        // a última da lista (em ordem de id) é de aluno que a conta não paga: as outras não podem ficar em conferência
        List<UUID> mixed = new ArrayList<>(f.charges());
        mixed.add(theirs);
        assertThat(send(f.who(), mixed, png(100)).getStatus()).isEqualTo(404);
        assertUntouched(mixed);

        Fx other = family("resp2@portal.example");
        assertThat(send(f.who(), other.charges(), png(100)).getStatus()).isEqualTo(404);
        assertUntouched(other.charges());
    }

    @Test
    @DisplayName("CA5: duas chamadas simultâneas com as mesmas cobranças deixam um comprovante pendente por cobrança")
    void concurrent() throws Exception {
        Fx f = family("resp@portal.example");
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(() -> send(f.who(), f.charges(), png(1000)).getStatus(), () -> send(f.who(), f.charges(), png(1000)).getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> r : pool.invokeAll(calls)) statuses.add(r.get());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(3);
        assertThat(count("select count(distinct charge_id) from payment_proof")).isEqualTo(3);
    }
}
