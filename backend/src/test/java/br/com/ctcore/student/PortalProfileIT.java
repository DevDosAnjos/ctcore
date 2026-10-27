package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-ALU-11, RN-ALU-02, RN-ALU-03, RF-POR-07: edição de contato e foto pelo portal, com banco real. */
class PortalProfileIT extends PortalTermBase {
    @Autowired FileStorage storage;

    private static final String[] CPFS = {"52998224725", "11144477735", "39053344705", "86288366757", "71428793860", "11122233396"};

    /** Aluno adulto com CPF, e-mail e celular, como o cadastro da gestão exige. */
    private UUID adult(Tenant t, UUID account) {
        int n = adminJdbc().queryForObject("select count(*) from student", Integer.class);
        UUID id = student(t, "Aluno Adulto", "1990-01-01", account);
        adminJdbc().update("update student set cpf = ?, email = 'adulto@portal.example', phone = '11987654321' where id = ?", CPFS[n % CPFS.length], id);
        return id;
    }

    private ObjectNode contact() {
        return json.createObjectNode().put("phone", "(11) 98888-7777").put("postalCode", "20040-020").put("street", "Rua Nova").put("number", "10")
                .put("complement", "Apto 2").put("district", "Centro").put("city", "Rio de Janeiro").put("state", "RJ").put("emergencyName", "Maria Souza")
                .put("emergencyRelationship", "Mãe").put("emergencyPhone", "11912345678");
    }

    private MockHttpServletResponse contact(AccountPrincipal who, UUID student, String body) throws Exception {
        return call(MockMvcRequestBuilders.put("/api/v1/portal/students/" + student + "/contact").contentType("application/json").content(body), who).getResponse();
    }

    private MockHttpServletResponse photo(AccountPrincipal who, UUID student, byte[] content) throws Exception {
        return call(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/portal/students/" + student + "/photo")
                .file(new MockMultipartFile("file", "f.png", "image/png", content)), who).getResponse();
    }

    private MockHttpServletResponse removePhoto(AccountPrincipal who, UUID student) throws Exception {
        return call(MockMvcRequestBuilders.delete("/api/v1/portal/students/" + student + "/photo"), who).getResponse();
    }

    private static byte[] image(String format, int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private List<String> audit(Tenant t, String action) {
        return adminJdbc().queryForList("select actor_type || '|' || coalesce(actor_id::text, '') || '|' || coalesce(actor_profile, '') || '|' || coalesce(before_data::text, '') || '|' "
                + "|| coalesce(after_data::text, '') from audit_log where organization_id = ? and action = ?", String.class, t.orgId(), action);
    }

    private String column(UUID id, String column) {
        return adminJdbc().queryForObject("select " + column + " from student where id = ?", String.class, id);
    }

    private boolean exists(Tenant t, String file) {
        try (var ignored = Scopes.open(t.orgId(), null)) {
            storage.readUrl(file);
            return true;
        } catch (FileStorage.NotFound e) {
            return false;
        }
    }

    @Test
    @DisplayName("CA3: contato do adulto muda, aparece na leitura e audita com ator PORTAL só os campos mudados; repetir não grava")
    void contactAdult() throws Exception {
        Tenant t = tenant();
        UUID x = account("x@portal.example");
        UUID s = adult(t, x);
        AccountPrincipal who = portal(t, x, Role.STUDENT);
        var r = contact(who, s, contact().toString());
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
        JsonNode p = body(r);
        assertThat(p.get("phone").asText()).isEqualTo("11988887777");
        assertThat(p.get("address").get("postalCode").asText()).isEqualTo("20040020");
        assertThat(p.get("address").get("city").asText()).isEqualTo("Rio de Janeiro");
        assertThat(p.get("emergency").get("name").asText()).isEqualTo("Maria Souza");
        assertThat(p.get("maskedCpf").asText()).isEqualTo("***.***.***-25");
        assertThat(column(s, "phone")).isEqualTo("11988887777");
        assertThat(column(s, "name")).isEqualTo("Aluno Adulto");
        var rows = audit(t, "student.updated");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).startsWith("PORTAL|" + x + "|STUDENT|").contains("alterado").doesNotContain("52998224725").doesNotContain("98888").doesNotContain("Rua Nova")
                .doesNotContain("Maria Souza");
        assertThat(contact(who, s, contact().toString()).getStatus()).isEqualTo(200);
        assertThat(audit(t, "student.updated")).hasSize(1);
        // só o telefone muda: a auditoria não cita endereço nem emergência
        assertThat(contact(who, s, contact().put("phone", "11977776666").toString()).getStatus()).isEqualTo(200);
        var second = audit(t, "student.updated");
        assertThat(second).hasSize(2);
        assertThat(second.stream().filter(a -> !a.contains("address")).findFirst().orElseThrow()).contains("phone").doesNotContain("emergencyContact");
    }

    @Test
    @DisplayName("CA4: celular sem DDD, CEP, UF, emergência incompleta e campo fora da lista dão 422 e nada muda nem é auditado")
    void contactValidation() throws Exception {
        Tenant t = tenant();
        UUID x = account("x@portal.example");
        UUID s = adult(t, x);
        AccountPrincipal who = portal(t, x, Role.STUDENT);
        String[][] bad = {{"phone", "987654321"}, {"postalCode", "123"}, {"state", "ZZ"}, {"street", ""}, {"emergencyPhone", ""}};
        for (String[] b : bad) {
            var r = contact(who, s, contact().put(b[0], b[1]).toString());
            assertThat(r.getStatus()).as(b[0]).isEqualTo(422);
            assertThat(body(r).toString()).contains(b[0]);
        }
        for (String field : new String[] {"name", "cpf", "birthDate", "email", "photo", "registeredUnitId"}) {
            var r = contact(who, s, contact().put(field, "x").toString());
            assertThat(r.getStatus()).as(field).isEqualTo(422);
            assertThat(body(r).toString()).contains(field);
        }
        assertThat(contact(who, s, contact().put("phone", 11987654321L).toString()).getStatus()).isEqualTo(422);
        assertThat(column(s, "street")).isEqualTo("Rua");
        assertThat(column(s, "phone")).isEqualTo("11987654321");
        assertThat(audit(t, "student.updated")).isEmpty();
    }

    @Test
    @DisplayName("CA4: dado menor, o responsável (financeiro ou não) altera o contato do filho e a auditoria é gravada")
    void contactMinorGuardians() throws Exception {
        Tenant t = tenant();
        UUID pay = account("pay@portal.example"), other = account("other@portal.example");
        UUID child = student(t, "Filho Menor", "2015-05-05", null);
        guardian(t, pay, "11144477735", true, child);
        guardian(t, other, "52998224725", false, child);
        for (UUID who : new UUID[] {pay, other}) {
            var r = contact(portal(t, who, Role.GUARDIAN), child, contact().put("street", "Rua " + who.toString().substring(0, 4)).toString());
            assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        }
        assertThat(audit(t, "student.updated")).hasSize(2).allMatch(a -> a.startsWith("PORTAL|") && a.contains("|GUARDIAN|"));
    }

    @Test
    @DisplayName("CA4, CA18: contato - outra conta, outra organização e inexistente dão 404; gestão e Professor 403; sem sessão 401; termo pendente 403")
    void contactIsolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = adult(a, x), sy = adult(a, y), sb = adult(b, account("b@portal.example"));
        AccountPrincipal who = portal(a, x, Role.STUDENT);
        for (UUID target : new UUID[] {sy, sb, UUID.randomUUID()}) {
            assertThat(contact(who, target, contact().toString()).getStatus()).isEqualTo(404);
        }
        assertThat(contact(portal(b, x, Role.STUDENT), sx, contact().toString()).getStatus()).isEqualTo(404);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            assertThat(contact(portal(a, x, role), sx, contact().toString()).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(contact(null, sx, contact().toString()).getStatus()).isEqualTo(401);
        assertThat(column(sy, "street")).isEqualTo("Rua");
        assertThat(column(sb, "street")).isEqualTo("Rua");
        assertThat(audit(a, "student.updated")).isEmpty();
        EnrollmentFixtures.enroll(adminJdbc(), a.orgId(), sx, a.unitId(), "ACTIVE", 0, null);
        publish(a, "ENROLLMENT", 1, true);
        var blocked = contact(who, sx, contact().toString());
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
        assertThat(column(sx, "street")).isEqualTo("Rua");
    }

    @Test
    @DisplayName("CA5: foto troca (512 px) e apaga a anterior; DELETE remove; auditoria PORTAL sem nome de arquivo")
    void photoReplaceAndRemove() throws Exception {
        Tenant t = tenant();
        UUID x = account("x@portal.example");
        UUID s = adult(t, x);
        AccountPrincipal who = portal(t, x, Role.STUDENT);
        var first = photo(who, s, image("png", 512, 512));
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
        assertThat(body(first).get("photoUrl").asText()).isNotBlank();
        String file = column(s, "photo");
        assertThat(file).startsWith("student-").endsWith(".png");
        assertThat(exists(t, file)).isTrue();
        assertThat(photo(who, s, image("jpg", 512, 512)).getStatus()).isEqualTo(200);
        String second = column(s, "photo");
        assertThat(second).endsWith(".jpg");
        assertThat(exists(t, file)).isFalse();
        var removed = removePhoto(who, s);
        assertThat(removed.getStatus()).isEqualTo(200);
        assertThat(body(removed).get("photoUrl").isNull()).isTrue();
        assertThat(column(s, "photo")).isNull();
        assertThat(exists(t, second)).isFalse();
        assertThat(removePhoto(who, s).getStatus()).isEqualTo(200);
        var updated = audit(t, "student.photo_updated");
        assertThat(updated).hasSize(2).allMatch(a -> a.startsWith("PORTAL|" + x + "|STUDENT|") && !a.contains("student-") && !a.contains(".png") && !a.contains(".jpg"));
        assertThat(audit(t, "student.photo_removed")).hasSize(1).allMatch(a -> !a.contains("student-"));
    }

    @Test
    @DisplayName("CA5: arquivo de outro tipo dá 422 e a foto fica")
    void photoInvalid() throws Exception {
        Tenant t = tenant();
        UUID x = account("x@portal.example");
        UUID s = adult(t, x);
        AccountPrincipal who = portal(t, x, Role.STUDENT);
        assertThat(photo(who, s, image("png", 512, 512)).getStatus()).isEqualTo(200);
        String file = column(s, "photo");
        var bad = photo(who, s, "texto".getBytes());
        assertThat(bad.getStatus()).isEqualTo(422);
        assertThat(body(bad).toString()).contains("file");
        assertThat(photo(who, s, image("png", 300, 300)).getStatus()).isEqualTo(422);
        var missing = call(MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/portal/students/" + s + "/photo"), who).getResponse();
        assertThat(missing.getStatus()).isEqualTo(422);
        assertThat(column(s, "photo")).isEqualTo(file);
        assertThat(exists(t, file)).isTrue();
    }

    @Test
    @DisplayName("CA5, CA18: foto - outra conta, outra organização e inexistente dão 404; gestão e Professor 403; sem sessão 401")
    void photoIsolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = adult(a, x), sy = adult(a, y), sb = adult(b, account("b@portal.example"));
        AccountPrincipal who = portal(a, x, Role.STUDENT);
        for (UUID target : new UUID[] {sy, sb, UUID.randomUUID()}) {
            assertThat(photo(who, target, image("png", 512, 512)).getStatus()).isEqualTo(404);
            assertThat(removePhoto(who, target).getStatus()).isEqualTo(404);
            // Arquivo inválido não muda a resposta: o 404 vem antes da validação.
            assertThat(photo(who, target, "texto".getBytes()).getStatus()).isEqualTo(404);
        }
        assertThat(photo(portal(b, x, Role.STUDENT), sx, image("png", 512, 512)).getStatus()).isEqualTo(404);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            assertThat(photo(portal(a, x, role), sx, image("png", 512, 512)).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(removePhoto(portal(a, x, role), sx).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(photo(null, sx, image("png", 512, 512)).getStatus()).isEqualTo(401);
        assertThat(removePhoto(null, sx).getStatus()).isEqualTo(401);
        assertThat(column(sy, "photo")).isNull();
        assertThat(column(sb, "photo")).isNull();
        assertThat(column(sx, "photo")).isNull();
        assertThat(audit(a, "student.photo_updated")).isEmpty();
    }
}
