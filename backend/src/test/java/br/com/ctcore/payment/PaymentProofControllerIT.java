package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.student.PortalRequestBaseAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-COB-05: rotas do portal (instruções e envio) e da gestão (lista, leitura, Confirmar e Recusar), perfis, alcance e isolamento, com banco real. */
class PaymentProofControllerIT extends PortalRequestBaseAccess {
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

    private record Fx(Tenant t, UUID student, UUID charge, AccountPrincipal who) {}

    private Fx adult(Tenant t, String name, String email) {
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        UUID acc = account(email);
        UUID s = enrolledAdult(t, name, acc);
        accepted(t, s, acc);
        return new Fx(t, s, BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), s, 15000, "OPEN"), portal(t, acc, Role.STUDENT));
    }

    private JsonNode json(MockHttpServletResponse r) throws Exception {
        return mapper.readTree(r.getContentAsString());
    }

    private MockHttpServletResponse upload(AccountPrincipal who, UUID charge, byte[] content, String filename) throws Exception {
        return call(MockMvcRequestBuilders.multipart("/api/v1/portal/charges/" + charge + "/proof").file(new MockMultipartFile("file", filename, "image/png", content)), who)
                .getResponse();
    }

    private MockHttpServletResponse uploadNothing(AccountPrincipal who, UUID charge) throws Exception {
        return call(MockMvcRequestBuilders.multipart("/api/v1/portal/charges/" + charge + "/proof"), who).getResponse();
    }

    private MockHttpServletResponse instructions(AccountPrincipal who, UUID charge) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/portal/charges/" + charge + "/pix-instructions"), who).getResponse();
    }

    private MockHttpServletResponse proofs(String method, String path, AccountPrincipal who, String body) throws Exception {
        var builder = "POST".equals(method)
                ? MockMvcRequestBuilders.post("/api/v1/payment-proofs" + path).contentType("application/json").content(body == null ? "{}" : body)
                : MockMvcRequestBuilders.get("/api/v1/payment-proofs" + path);
        return call(builder, who).getResponse();
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("CA1, CA3, CA4, CA7, CA8, CA9: o aluno vê as instruções, envia (201), repetir dá 409, arquivo inválido dá 422; a gestão lista, lê, recusa e confirma pela rota")
    void flow() throws Exception {
        Tenant t = tenant();
        Fx f = adult(t, "Aluno Um", "um@portal.example");
        var admin = manager(t, Role.ADMINISTRATOR, t.unitId());

        var info = json(instructions(f.who(), f.charge()));
        assertThat(info.get("pixKey").asText()).isEqualTo("+5511987654321");
        assertThat(info.get("amountCents").asLong()).isEqualTo(15000);

        var bad = upload(f.who(), f.charge(), "GIF89a............".getBytes(StandardCharsets.ISO_8859_1), "x.png");
        assertThat(bad.getStatus()).isEqualTo(422);
        assertThat(json(bad).get("fields").get(0).get("field").asText()).isEqualTo("file");
        assertThat(uploadNothing(f.who(), f.charge()).getStatus()).isEqualTo(422);
        assertThat(count("select count(*) from payment_proof")).isZero();

        var sent = upload(f.who(), f.charge(), png(2000), "comprovante.png");
        assertThat(sent.getStatus()).isEqualTo(201);
        String id = json(sent).get("id").asText();
        assertThat(json(sent).get("status").asText()).isEqualTo("PENDING");
        assertThat(sent.getContentAsString()).doesNotContain("payment-proof-");
        var again = upload(f.who(), f.charge(), png(2000), "outra.png");
        assertThat(again.getStatus()).isEqualTo(409);
        assertThat(json(again).get("code").asText()).isEqualTo("CHARGE_NOT_PAYABLE");
        assertThat(instructions(f.who(), f.charge()).getStatus()).isEqualTo(409);

        var list = json(proofs("GET", "", admin, null));
        assertThat(list.get("total").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("id").asText()).isEqualTo(id);
        assertThat(list.get("items").get(0).get("imageUrl").asText()).isNotBlank();
        assertThat(list.get("items").get(0).get("studentName").asText()).isEqualTo("Aluno Um");
        assertThat(proofs("GET", "/" + id, admin, null).getStatus()).isEqualTo(200);

        assertThat(proofs("POST", "/" + id + "/refuse", admin, null).getStatus()).isEqualTo(400);
        assertThat(proofs("POST", "/" + id + "/refuse", admin, "{\"reason\":\"ab\"}").getStatus()).isEqualTo(400);
        var refused = proofs("POST", "/" + id + "/refuse", admin, "{\"reason\":\"Valor diferente\"}");
        assertThat(refused.getStatus()).isEqualTo(200);
        assertThat(json(refused).get("status").asText()).isEqualTo("REFUSED");
        assertThat(proofs("POST", "/" + id + "/confirm", admin, null).getStatus()).isEqualTo(409);

        String second = json(upload(f.who(), f.charge(), png(2000), "novo.png")).get("id").asText();
        var confirmed = proofs("POST", "/" + second + "/confirm", admin, "{\"sendReceipt\":false}");
        assertThat(confirmed.getStatus()).isEqualTo(200);
        assertThat(json(confirmed).get("proof").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(json(confirmed).get("payment").get("method").asText()).isEqualTo("PIX_KEY");
        var repeat = proofs("POST", "/" + second + "/confirm", admin, null);
        assertThat(repeat.getStatus()).isEqualTo(409);
        assertThat(json(repeat).get("code").asText()).isEqualTo("PROOF_NOT_PENDING");
        assertThat(count("select count(*) from payment")).isEqualTo(1);
        assertThat(json(proofs("GET", "", admin, null)).get("items")).isEmpty();
    }

    @Test
    @DisplayName("CA2, CA4: só o responsável financeiro envia (o outro 403), conta de outro aluno e de outra organização 404, unidade sem chave 422 PIX_KEY_MISSING")
    void whoPays() throws Exception {
        Tenant t = tenant();
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        UUID minor = student(t, "Menor Dois", "2015-01-01", null);
        UUID financial = account("fin@portal.example"), other = account("naofin@portal.example"), stranger = account("estranho@portal.example");
        guardian(t, financial, "11144477735", true, minor);
        guardian(t, other, "52998224725", false, minor);
        accepted(t, minor, financial);
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), minor, 9000, "OPEN");

        assertThat(upload(portal(t, other, Role.GUARDIAN), charge, png(500), "a.png").getStatus()).isEqualTo(403);
        assertThat(instructions(portal(t, other, Role.GUARDIAN), charge).getStatus()).isEqualTo(403);
        assertThat(upload(portal(t, stranger, Role.GUARDIAN), charge, png(500), "a.png").getStatus()).isEqualTo(404);
        assertThat(upload(portal(tenant(), financial, Role.GUARDIAN), charge, png(500), "a.png").getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from payment_proof")).isZero();

        var payer = portal(t, financial, Role.GUARDIAN);
        adminJdbc().update("update unit_settings set pix_key = null where unit_id = ?", t.unitId());
        var noKey = upload(payer, charge, png(500), "a.png");
        assertThat(noKey.getStatus()).isEqualTo(422);
        assertThat(json(noKey).get("code").asText()).isEqualTo("PIX_KEY_MISSING");
        assertThat(instructions(payer, charge).getStatus()).isEqualTo(422);
        adminJdbc().update("update unit_settings set pix_key = '+5511987654321' where unit_id = ?", t.unitId());
        assertThat(upload(payer, charge, png(500), "a.png").getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("CA7, CA13: perfis e alcance: gestão e Professor nas rotas do portal 403, portal e Professor nas da gestão 403, sem sessão 401, termo pendente TERM_PENDING, fora do alcance 404")
    void profiles() throws Exception {
        Tenant t = tenant();
        UUID unitB = secondUnit(t);
        Fx f = adult(t, "Aluno Tres", "tres@portal.example");
        String id = json(upload(f.who(), f.charge(), png(500), "a.png")).get("id").asText();

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var staff = who(t, role);
            assertThat(instructions(staff, f.charge()).getStatus()).isEqualTo(403);
            assertThat(upload(staff, f.charge(), png(500), "a.png").getStatus()).isEqualTo(403);
        }
        assertThat(instructions(null, f.charge()).getStatus()).isEqualTo(401);
        assertThat(upload(null, f.charge(), png(500), "a.png").getStatus()).isEqualTo(401);

        for (AccountPrincipal denied : new AccountPrincipal[] {f.who(), who(t, Role.TEACHER)}) {
            assertThat(proofs("GET", "", denied, null).getStatus()).isEqualTo(403);
            assertThat(proofs("GET", "/" + id, denied, null).getStatus()).isEqualTo(403);
            assertThat(proofs("POST", "/" + id + "/confirm", denied, null).getStatus()).isEqualTo(403);
            assertThat(proofs("POST", "/" + id + "/refuse", denied, "{\"reason\":\"motivo\"}").getStatus()).isEqualTo(403);
        }
        assertThat(proofs("GET", "", null, null).getStatus()).isEqualTo(401);

        var outsider = manager(t, Role.ADMINISTRATOR, unitB);
        assertThat(json(proofs("GET", "", outsider, null)).get("items")).isEmpty();
        assertThat(json(proofs("GET", "?unitId=" + t.unitId(), outsider, null)).get("items")).isEmpty();
        assertThat(proofs("GET", "/" + id, outsider, null).getStatus()).isEqualTo(404);
        assertThat(proofs("POST", "/" + id + "/confirm", outsider, null).getStatus()).isEqualTo(404);
        assertThat(proofs("POST", "/" + id + "/refuse", outsider, "{\"reason\":\"motivo\"}").getStatus()).isEqualTo(404);
        Tenant other = tenant();
        var foreign = who(other, Role.COORDINATOR);
        assertThat(json(proofs("GET", "", foreign, null)).get("items")).isEmpty();
        assertThat(proofs("GET", "/" + id, foreign, null).getStatus()).isEqualTo(404);
        assertThat(proofs("POST", "/" + id + "/confirm", foreign, null).getStatus()).isEqualTo(404);
        assertThat(count("select count(*) from payment_proof where status = 'PENDING'")).isEqualTo(1);
        var coordinator = who(t, Role.COORDINATOR);
        assertThat(json(proofs("GET", "", coordinator, null)).get("total").asInt()).isEqualTo(1);

        Fx g = adult(t, "Aluno Quatro", "quatro@portal.example");
        publish(t, "ENROLLMENT", 2, true);
        var pending = upload(g.who(), g.charge(), png(500), "a.png");
        assertThat(pending.getStatus()).isEqualTo(403);
        assertThat(json(pending).get("code").asText()).isEqualTo("TERM_PENDING");
        assertThat(instructions(g.who(), g.charge()).getStatus()).isEqualTo(403);
    }
}
