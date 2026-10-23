package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.student.EnrollmentFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-COB-16: PDF do recibo aberto pelo código, sem sessão, com o que o pagamento gravou. */
class PaymentReceiptIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from account where email like 'pay-%@example.com'");
    }

    private AccountPrincipal admin(Tenant t) {
        AccountPrincipal p = who(t, Role.ADMINISTRATOR);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Caixa Sintética', 'ACTIVE')", p.accountId(), "pay-" + p.accountId() + "@example.com");
        return p;
    }

    private String pay(AccountPrincipal who, UUID charge) throws Exception {
        var r = call(MockMvcRequestBuilders.post("/api/v1/charges/" + charge + "/payments").contentType("application/json").content("{\"method\":\"PIX_KEY\"}"), who).getResponse();
        assertThat(r.getStatus()).isEqualTo(201);
        return json.readTree(r.getContentAsString()).get("receipt").get("code").asText();
    }

    private MockHttpServletResponse pdf(String code) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/public/receipts/" + code + "/pdf").secure(true).with(csrf())).andReturn().getResponse();
    }

    private static String text(MockHttpServletResponse r) throws Exception {
        try (var doc = Loader.loadPDF(r.getContentAsByteArray())) {
            return new PDFTextStripper().getText(doc).replaceAll("[ \\r\\n]+", " ");
        }
    }

    private static byte[] logo() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("CA5, CA7: mensalidade de adulto com CPF, unidade com CNPJ e endereço e organização com logo: 200 application/pdf sem sessão, com o texto e a imagem")
    void adultWithLogo() throws Exception {
        var t = tenant();
        var admin = admin(t);
        adminJdbc().update("update unit set cnpj = '12345678000190', address = 'Rua das Flores, 10, Centro' where id = ?", t.unitId());
        assertThat(call(putLogo(logo(), "logo.png"), who(t, Role.COORDINATOR)).getResponse().getStatus()).isEqualTo(200);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set cpf = '12345678901' where id = ?", student);
        var enrolled = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", 0, null);
        String modality = adminJdbc().queryForObject("select m.name from enrollment_modality em join modality m on m.id = em.modality_id where em.id = ?", String.class, enrolled.row());
        UUID charge = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), student, enrolled.row(), LocalDate.of(2026, 11, 1), null, 15000, "OPEN");
        String code = pay(admin, charge);

        var r = pdf(code);
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentType()).isEqualTo("application/pdf");
        assertThat(r.getHeader("Cache-Control")).contains("no-store", "private");
        assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(text(r)).contains("Unit", "CNPJ: 12.345.678/0001-90", "Rua das Flores, 10, Centro", "Recibo nº 1", "Pago por: Maria Souza · CPF ***.456.789-**",
                "Aluno: Maria Souza", "Referente a: " + modality + " · nov/2026", "Valor: R$ 150,00", "Meio: Pix na chave", ReceiptPdf.NOT_INVOICE, "ctcore.com.br/recibo/" + code);
        try (var doc = Loader.loadPDF(r.getContentAsByteArray())) {
            var names = doc.getPage(0).getResources().getXObjectNames();
            assertThat(names).hasSize(1);
        }
        assertThat(text(r)).doesNotContain("12345678901", "123.456.789-01");
        // O contexto da organização aberto para ler a logo foi fechado.
        assertThat(OrganizationContext.organizationId()).isNull();
    }

    @Test
    @DisplayName("CA6: menor traz o responsável financeiro como pagador e o aluno à parte; avulsa mostra a descrição; sem CNPJ, endereço e logo sai sem erro")
    void minorOneOffBare() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pedro Souza", "2018-01-01");
        UUID guardian = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        adminJdbc().update("update guardian set name = 'Ana Souza', cpf = '98765432100' where id = ?", guardian);
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), minor, 9000, "OPEN");
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", guardian, charge);
        var r = pdf(pay(admin, charge));
        assertThat(r.getStatus()).isEqualTo(200);
        String text = text(r);
        assertThat(text).contains("Pago por: Ana Souza · CPF ***.654.321-**", "Aluno: Pedro Souza", "Referente a: Kimono", "Valor: R$ 90,00").doesNotContain("CNPJ");
        try (var doc = Loader.loadPDF(r.getContentAsByteArray())) {
            assertThat(doc.getPage(0).getResources().getXObjectNames()).isEmpty();
        }
    }

    @Test
    @DisplayName("CA6: logo que sumiu do armazenamento não derruba o PDF")
    void missingLogo() throws Exception {
        var t = tenant();
        adminJdbc().update("update organization set logo_url = 'sumiu.png' where id = ?", t.orgId());
        String code = pay(admin(t), BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria"), 100, "OPEN"));
        assertThat(pdf(code).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA7: código inexistente, minúsculo, curto, com curinga ou de tamanho errado dão o mesmo 404; nada é listado")
    void notFound() throws Exception {
        var t = tenant();
        var admin = admin(t);
        String code = pay(admin, BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria"), 100, "OPEN"));
        assertThat(pdf(code).getStatus()).isEqualTo(200);
        String reference = json.readTree(pdf("AAAAAAAAAAAAAAAA").getContentAsString()).get("code").asText();
        assertThat(pdf("AAAAAAAAAAAAAAAA").getStatus()).isEqualTo(404);
        for (String bad : new String[] {code.toLowerCase(), code.substring(0, 15), code + "A", "abc"}) {
            var r = pdf(bad);
            assertThat(r.getStatus()).as(bad).isEqualTo(404);
            assertThat(json.readTree(r.getContentAsString()).get("code").asText()).as(bad).isEqualTo(reference);
        }
    }

    private MockHttpServletResponse staffPdf(String code, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/receipts/" + code + "/pdf"), who).getResponse();
    }

    @Test
    @DisplayName("RN-COB-16: com sessão e acesso a cobranças da unidade o PDF traz o CPF completo; o link público segue mascarado")
    void staffGetsFullCpf() throws Exception {
        var t = tenant();
        var admin = admin(t);
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set cpf = '12345678901' where id = ?", student);
        String code = pay(admin, BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 5000, "OPEN"));
        var staff = staffPdf(code, who(t, Role.COORDINATOR));
        assertThat(staff.getStatus()).isEqualTo(200);
        assertThat(staff.getContentType()).isEqualTo("application/pdf");
        assertThat(staff.getHeader("Cache-Control")).contains("no-store", "private");
        assertThat(text(staff)).contains("Pago por: Maria Souza · CPF 123.456.789-01");
        assertThat(text(staffPdf(code, admin))).contains("CPF 123.456.789-01");
        assertThat(text(pdf(code))).contains("CPF ***.456.789-**").doesNotContain("123.456.789-01");
    }

    @Test
    @DisplayName("RN-COB-16: rota com sessão: sem sessão 401, Professor 403, outra organização, outra unidade e código desconhecido dão o mesmo 404")
    void staffRouteAccess() throws Exception {
        var t = tenant();
        var other = tenant();
        var admin = admin(t);
        String code = pay(admin, BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria"), 100, "OPEN"));
        assertThat(mvc.perform(MockMvcRequestBuilders.get("/api/v1/receipts/" + code + "/pdf").secure(true)).andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(staffPdf(code, who(t, Role.TEACHER)).getStatus()).isEqualTo(403);
        var foreign = staffPdf(code, who(other, Role.COORDINATOR));
        var unknown = staffPdf("AAAAAAAAAAAAAAAA", admin);
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(unknown.getStatus()).isEqualTo(404);
        assertThat(json.readTree(foreign.getContentAsString()).get("code").asText()).isEqualTo(json.readTree(unknown.getContentAsString()).get("code").asText());
        // Administrador sem a unidade do recibo.
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", second, t.orgId(), "u" + second.toString().substring(0, 12));
        var elsewhere = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, java.util.Set.of(second));
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Outra Gestora', 'ACTIVE')", elsewhere.accountId(), "pay-" + elsewhere.accountId() + "@example.com");
        assertThat(staffPdf(code, elsewhere).getStatus()).isEqualTo(404);
    }
}
