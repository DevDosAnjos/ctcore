package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.storage.FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-04 (edição dos dados pessoais e da foto: validações, duplicidade, menor, permissão, alcance, isolamento e auditoria), com banco real. */
class StudentEditIT extends OrganizationBase {
    static final String CPF = "52998224725";
    static final String OTHER_CPF = "11144477735";
    @Autowired ObjectMapper json;
    @Autowired FileStorage storage;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private ObjectNode body(String name, String cpf) {
        return json.createObjectNode().put("name", name).put("birthDate", "1990-05-20").put("postalCode", "01001-000").put("street", "Rua Fictícia")
                .put("number", "10").put("district", "Centro").put("city", "São Paulo").put("state", "SP").put("cpf", cpf)
                .put("email", "aluno@ficticia.example").put("phone", "(11) 91234-5678");
    }

    /** Menor de 10 anos, sem CPF, e-mail e celular. */
    private ObjectNode child(String name) {
        return body(name, "").put("birthDate", LocalDate.now().minusYears(10).toString()).put("email", "").put("phone", "");
    }

    private String student(Tenant t, ObjectNode update) throws Exception {
        ObjectNode in = update.deepCopy().put("registeredUnitId", t.unitId().toString());
        if (LocalDate.parse(in.get("birthDate").asText()).isAfter(LocalDate.now().minusYears(18))) {
            in.putArray("guardians").addObject().put("name", "Responsável Fictício").put("cpf", OTHER_CPF).put("email", "resp@ficticia.example")
                    .put("phone", "11987654321");
        }
        return ok(send(HttpMethod.POST, "", in, coordinator(t)), 201).get("id").asText();
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json")
                .content(body == null ? "" : body.toString()), who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private AccountPrincipal coordinator(Tenant t) {
        AccountPrincipal p = who(t, Role.COORDINATOR);
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE') on conflict do nothing", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private UUID otherUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(),
                "u" + id.toString().substring(0, 12));
        return id;
    }

    private JsonNode edit(Tenant t, String id, ObjectNode update, int status) throws Exception {
        return ok(send(HttpMethod.PUT, "/" + id, update, coordinator(t)), status);
    }

    private String field(JsonNode error) {
        return error.get("fields").get(0).get("field").asText();
    }

    private List<String> audits(Tenant t, String action) {
        return adminJdbc().queryForList("select coalesce(before_data::text, '') || coalesce(after_data::text, '') from audit_log "
                + "where organization_id = ? and action = ?", String.class, t.orgId(), action);
    }

    @Test
    @DisplayName("CA1, CA3, CA13: altera os dados do adulto, ignora campo de saúde; o menor salva sem CPF, e-mail e celular")
    void updates() throws Exception {
        Tenant t = tenant();
        String id = student(t, body("Aluno Fictício", CPF));
        JsonNode saved = edit(t, id, body("Aluno Novo", CPF).put("street", "Rua Nova").put("email", "novo@ficticia.example").put("healthNotes", "x")
                .put("phone", "(21) 91234-5678"), 200);
        assertThat(saved.get("name").asText()).isEqualTo("Aluno Novo");
        assertThat(saved.get("street").asText()).isEqualTo("Rua Nova");
        assertThat(saved.get("phone").asText()).isEqualTo("21912345678");
        assertThat(saved.has("healthNotes")).isFalse();
        assertThat(saved.get("registeredUnitId").asText()).isEqualTo(t.unitId().toString());
        assertThat(ok(send(HttpMethod.GET, "/" + id, null, coordinator(t)), 200)).isEqualTo(saved);
        assertThat(adminJdbc().queryForObject("select name_key from student where id = ?::uuid", String.class, id)).isEqualTo("aluno novo");

        String minorId = student(t, child("Aluno Menor"));
        edit(t, minorId, child("Aluno Menor").put("city", "Campinas"), 200);
    }

    @Test
    @DisplayName("CA2, CA4, CA10: dados inválidos dão 400 com o campo e nada é gravado; emergência é tudo ou nada")
    void validates() throws Exception {
        Tenant t = tenant();
        String id = student(t, body("Aluno Fictício", CPF));
        String future = LocalDate.now().plusDays(1).toString();
        for (ObjectNode bad : List.of(body("A", CPF), body("Aluno", ""), body("Aluno", CPF).put("email", ""), body("Aluno", CPF).put("phone", ""),
                body("Aluno", CPF).put("birthDate", future), body("Aluno", CPF).put("postalCode", "0100100"), body("Aluno", "52998224724"),
                body("Aluno", CPF).put("phone", "91234-5678"), body("Aluno", CPF).put("emergencyName", "Mãe"))) {
            assertThat(edit(t, id, bad, 400).get("fields")).as(bad.toString()).isNotEmpty();
        }
        assertThat(field(edit(t, id, body("Aluno", CPF).put("emergencyName", "Mãe"), 400))).isEqualTo("emergencyRelationship");
        assertThat(field(edit(t, id, body("Aluno", "52998224724"), 400))).isEqualTo("cpf");
        assertThat(field(edit(t, id, body("Aluno", CPF).put("phone", "91234-5678"), 400))).isEqualTo("phone");
        assertThat(adminJdbc().queryForObject("select name from student where id = ?::uuid", String.class, id)).isEqualTo("Aluno Fictício");
        edit(t, id, body("Aluno Fictício", CPF).put("emergencyName", "Mãe").put("emergencyRelationship", "Mãe").put("emergencyPhone", "11987654321"), 200);
        assertThat(edit(t, id, body("Aluno Fictício", CPF), 200).get("emergencyName").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA5: CPF de outro aluno dá 409 com id, nome e foto; manter o próprio CPF dá 200")
    void cpfExists() throws Exception {
        Tenant t = tenant();
        String first = student(t, body("Aluno Existente", CPF));
        String second = student(t, body("Aluno Dois", OTHER_CPF));
        JsonNode error = edit(t, second, body("Aluno Dois", CPF), 409);
        assertThat(error.get("code").asText()).isEqualTo("STUDENT_CPF_EXISTS");
        assertThat(error.get("fields")).hasSize(3);
        assertThat(error.get("fields").get(0).get("message").asText()).isEqualTo(first);
        assertThat(error.get("fields").get(1).get("message").asText()).isEqualTo("Aluno Existente");
        assertThat(error.toString()).doesNotContain(CPF);
        edit(t, second, body("Aluno Dois Editado", OTHER_CPF), 200);
    }

    @Test
    @DisplayName("CA6: dois PUT com o mesmo CPF novo em alunos diferentes: um 200 e um 409")
    void race() throws Exception {
        Tenant t = tenant();
        String a = student(t, body("Aluno A", OTHER_CPF));
        String b = student(t, body("Aluno B", "12345678909"));
        AccountPrincipal who = coordinator(t);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> toA = () -> send(HttpMethod.PUT, "/" + a, body("Aluno A", CPF), who).getStatus();
            Callable<Integer> toB = () -> send(HttpMethod.PUT, "/" + b, body("Aluno B", CPF), who).getStatus();
            var results = pool.invokeAll(List.of(toA, toB));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("CA7: sem CPF, nome de outro aluno de mesmo nascimento avisa; confirmar passa; mudar só o telefone não avisa")
    void possibleDuplicate() throws Exception {
        Tenant t = tenant();
        student(t, child("Ana Lima"));
        String id = student(t, child("Bia Lima"));
        ObjectNode same = child("ANA LIMA");
        JsonNode warning = edit(t, id, same, 409);
        assertThat(warning.get("code").asText()).isEqualTo("STUDENT_POSSIBLE_DUPLICATE");
        assertThat(warning.get("fields")).hasSize(3);
        edit(t, id, same.deepCopy().put("confirmDuplicate", true), 200);
        edit(t, id, same.deepCopy().put("phone", "11987654321"), 200);
        edit(t, id, child("Bia Lima"), 200);
    }

    @Test
    @DisplayName("CA8: virar adulto mantém os responsáveis; virar menor exige responsável (400 em birthDate)")
    void minorRule() throws Exception {
        Tenant t = tenant();
        String minor = student(t, child("Aluno Menor"));
        assertThat(field(edit(t, minor, child("Aluno Menor").put("birthDate", "1990-01-01"), 400))).isEqualTo("cpf");
        JsonNode adult = edit(t, minor, body("Aluno Menor", CPF).put("birthDate", "1990-01-01"), 200);
        assertThat(adult.get("guardians")).hasSize(1);
        edit(t, minor, child("Aluno Menor"), 200);
        String alone = student(t, body("Aluno Adulto", OTHER_CPF));
        assertThat(field(edit(t, alone, child("Aluno Adulto"), 400))).isEqualTo("birthDate");
    }

    @Test
    @DisplayName("CA9: permissão e alcance nas três rotas; outra organização e id desconhecido dão 404")
    void permissionsAndIsolation() throws Exception {
        Tenant t = tenant(), other = tenant();
        String id = student(t, body("Aluno Alcance", CPF));
        UUID elsewhere = otherUnit(t);
        for (Role role : new Role[]{Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = who(t, role);
            assertThat(send(HttpMethod.PUT, "/" + id, body("X Y", CPF), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(call(photo(id, image("png", 512, 512)), who).getResponse().getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(HttpMethod.DELETE, "/" + id + "/photo", null, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(send(HttpMethod.PUT, "/" + id, body("X Y", CPF), null).getStatus()).isEqualTo(401);
        AccountPrincipal outsider = admin(t, elsewhere);
        assertThat(send(HttpMethod.PUT, "/" + id, body("X Y", CPF), outsider).getStatus()).isEqualTo(404);
        assertThat(call(photo(id, image("png", 512, 512)), outsider).getResponse().getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/" + id + "/photo", null, outsider).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/" + id, body("X Y", CPF), admin(t, t.unitId())).getStatus()).isEqualTo(200);
        AccountPrincipal foreign = coordinator(other);
        assertThat(send(HttpMethod.PUT, "/" + id, body("X Y", CPF), foreign).getStatus()).isEqualTo(404);
        assertThat(call(photo(id, image("png", 512, 512)), foreign).getResponse().getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.DELETE, "/" + id + "/photo", null, foreign).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/" + UUID.randomUUID(), body("X Y", CPF), coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(column(id)).isNull();
    }

    @Test
    @DisplayName("RN-ALU-02: CA11: foto 512 x 512 troca e apaga o arquivo anterior; tipo ou tamanho errado dá 400; DELETE remove")
    void photo() throws Exception {
        Tenant t = tenant();
        String id = student(t, body("Aluno Foto", CPF));
        AccountPrincipal c = coordinator(t);
        JsonNode first = ok(call(photo(id, image("png", 512, 512)), c).getResponse(), 200);
        assertThat(first.get("photoUrl").asText()).isNotBlank();
        String file = column(id);
        assertThat(file).startsWith("student-").endsWith(".png");
        assertThat(exists(t, file)).isTrue();
        assertThat(field(ok(call(photo(id, image("png", 300, 300)), c).getResponse(), 400))).isEqualTo("file");
        assertThat(field(ok(call(photo(id, "texto".getBytes()), c).getResponse(), 400))).isEqualTo("file");
        assertThat(column(id)).isEqualTo(file);
        ok(call(photo(id, image("jpg", 512, 512)), c).getResponse(), 200);
        String second = column(id);
        assertThat(second).endsWith(".jpg");
        assertThat(exists(t, file)).isFalse();
        assertThat(ok(send(HttpMethod.DELETE, "/" + id + "/photo", null, c), 200).get("photoUrl").isNull()).isTrue();
        assertThat(column(id)).isNull();
        assertThat(exists(t, second)).isFalse();
        ok(send(HttpMethod.DELETE, "/" + id + "/photo", null, c), 200);
        assertThat(audits(t, "student.photo_updated")).hasSize(2);
        assertThat(audits(t, "student.photo_removed")).hasSize(1);
    }

    @Test
    @DisplayName("CA12: auditoria com ator, aluno e unidade, sem dado pessoal; PUT idêntico não grava")
    void audit() throws Exception {
        Tenant t = tenant();
        String id = student(t, body("Aluno Auditado", CPF));
        edit(t, id, body("Aluno Auditado", CPF), 200);
        assertThat(audits(t, "student.updated")).isEmpty();
        edit(t, id, body("Aluno Renomeado", OTHER_CPF).put("street", "Rua Secreta").put("email", "outro@ficticia.example").put("phone", "11999990000"), 200);
        var rows = audits(t, "student.updated");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).contains("Aluno Auditado").contains("Aluno Renomeado").contains("alterado").doesNotContain(CPF).doesNotContain(OTHER_CPF)
                .doesNotContain("ficticia.example").doesNotContain("91234").doesNotContain("99999").doesNotContain("Rua Secreta").doesNotContain("01001");
        var meta = adminJdbc().queryForMap("select actor_id, entity_id, unit_id from audit_log where organization_id = ? and action = 'student.updated'", t.orgId());
        assertThat(meta.get("entity_id")).isEqualTo(id);
        assertThat(meta.get("unit_id")).isEqualTo(t.unitId());
        assertThat(meta.get("actor_id")).isNotNull();
        assertThat(ok(send(HttpMethod.GET, "/" + id + "/history", null, coordinator(t)), 200).toString()).contains("student.updated");
    }

    private String column(String id) {
        return adminJdbc().queryForObject("select photo from student where id = ?::uuid", String.class, id);
    }

    private static byte[] image(String format, int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private MockHttpServletRequestBuilder photo(String id, byte[] content) {
        return MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/students/" + id + "/photo")
                .file(new MockMultipartFile("file", "f.png", "image/png", content));
    }

    private boolean exists(Tenant t, String file) {
        try (var ignored = Scopes.open(t.orgId(), null)) {
            storage.readUrl(file);
            return true;
        } catch (FileStorage.NotFound e) {
            return false;
        }
    }
}
