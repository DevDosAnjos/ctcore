package br.com.ctcore.teacher;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.Scopes;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.storage.FileStorage;
import br.com.ctcore.storage.S3FileStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

/** RF-PRO-03: cadastro, edição, foto e opções do professor; permissão, alcance, isolamento (API e RLS) e auditoria, com banco real. */
class TeacherWriteIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @Autowired S3FileStorage storage;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from training_class_assistant");
        adminJdbc().update("delete from training_class");
        adminJdbc().update("delete from teacher_modality");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
    }

    private UUID modality(String name, boolean active, boolean graduation, Tenant t) {
        UUID id = adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active, graduation_enabled) values (?, ?, ?, ?) "
                + "on conflict (organization_id, modality_id) do update set active = excluded.active, graduation_enabled = excluded.graduation_enabled",
                t.orgId(), id, active, graduation);
        return id;
    }

    private UUID level(Tenant t, UUID modality, String name, int max) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, max_degree, kids) "
                + "values (?, ?, ?, (select count(*) from graduation_level where organization_id = ? and modality_id = ?), ?, '#112233', ?, false)",
                id, t.orgId(), modality, t.orgId(), modality, name, max);
        return id;
    }

    private UUID unit(Tenant t, String name, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, ?, ?, ?)", id, t.orgId(), name,
                "u" + id.toString().substring(0, 12), active);
        return id;
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
    }

    private ObjectNode input(String name, UUID... units) {
        ObjectNode n = json.createObjectNode().put("name", name).put("directoryConsent", false).put("consentDeclaredByManagement", false);
        ArrayNode u = n.putArray("unitIds");
        for (UUID id : units) u.add(id.toString());
        n.putArray("modalities");
        return n;
    }

    private static ObjectNode mod(ObjectNode input, UUID modality, UUID level, Integer degree) {
        ((ArrayNode) input.get("modalities")).addObject().put("modalityId", modality.toString())
                .put("levelId", level == null ? null : level.toString()).put("degree", degree);
        return input;
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.request(method, "/api/v1/teachers" + path).contentType("application/json")
                .content(body == null ? "" : body.toString()), who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private UUID create(Tenant t, ObjectNode body) throws Exception {
        return UUID.fromString(ok(send(HttpMethod.POST, "", body, who(t, Role.COORDINATOR)), 201).get("id").asText());
    }

    private long teachers() {
        return adminJdbc().queryForObject("select count(*) from teacher", Long.class);
    }

    private List<String> audits(Tenant t, String action) {
        return adminJdbc().queryForList("select coalesce(before_data::text, '') || coalesce(after_data::text, '') from audit_log "
                + "where organization_id = ? and action = ?", String.class, t.orgId(), action);
    }

    private static byte[] image(String format, int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    private MockHttpServletRequestBuilder photo(UUID id, byte[] content) {
        return MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/v1/teachers/" + id + "/photo")
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

    private String photoColumn(UUID id) {
        return adminJdbc().queryForObject("select photo from teacher where id = ?", String.class, id);
    }

    private UUID classOf(Tenant t, String name, UUID modality, UUID head, String status, UUID... assistants) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), t.unitId(), name, name.toLowerCase(), modality, head, status);
        for (UUID a : assistants) {
            adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), id, a);
        }
        return id;
    }

    @Test
    @DisplayName("RN-PRO-09: tirar uma modalidade em que é titular ou auxiliar de turma ativa dá 409 listando as turmas; turma desativada não impede; nada muda")
    void removingTaughtModalityIsRefused() throws Exception {
        var t = tenant();
        UUID judo = modality("Judô", true, false, t);
        UUID boxe = modality("Boxe", true, false, t);
        UUID ana = create(t, mod(input("Ana", t.unitId()), judo, null, null));
        UUID beto = create(t, mod(input("Beto", t.unitId()), judo, null, null));
        classOf(t, "Judô Adulto", judo, ana, "ACTIVE");
        classOf(t, "Judô Kids", judo, beto, "ACTIVE", ana);
        classOf(t, "Judô Antiga", judo, ana, "INACTIVE");
        var coordinator = who(t, Role.COORDINATOR);

        var r = send(HttpMethod.PUT, "/" + ana, mod(input("Ana", t.unitId()), boxe, null, null), coordinator);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(409);
        var body = json.readTree(r.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("TEACHER_MODALITY_IN_USE");
        assertThat(body.get("fields").findValuesAsText("message")).containsExactly("Judô Adulto", "Judô Kids");
        assertThat(adminJdbc().queryForObject("select count(*) from teacher_modality where teacher_id = ? and modality_id = ?", Integer.class, ana, judo)).isEqualTo(1);
        assertThat(audits(t, "teacher.updated")).isEmpty();

        // manter a modalidade e acrescentar outra vale; Beto é titular de Judô Kids, então trocar Judô por Boxe também é recusado
        var keep = input("Ana", t.unitId());
        keep.putArray("modalities").addObject().put("modalityId", judo.toString()).putNull("levelId").putNull("degree");
        keep.withArray("modalities").addObject().put("modalityId", boxe.toString()).putNull("levelId").putNull("degree");
        ok(send(HttpMethod.PUT, "/" + ana, keep, coordinator), 200);
        ok(send(HttpMethod.PUT, "/" + beto, mod(input("Beto", t.unitId()), boxe, null, null), coordinator), 409);

        adminJdbc().update("update training_class set status = 'INACTIVE'");
        ok(send(HttpMethod.PUT, "/" + ana, mod(input("Ana", t.unitId()), boxe, null, null), coordinator), 200);
    }

    @Test
    @DisplayName("CA1, CA11: Coordenador cria com todos os campos; 201 com detalhe, telefone só com dígitos, aparece na lista e a auditoria não traz contato")
    void create() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID jj = modality("Jiu-Jitsu", true, true, t);
        UUID blue = level(t, jj, "Azul", 4);
        UUID judo = modality("Judô", true, false, t);
        ObjectNode in = input("  Ana Souza ", t.unitId(), second).put("email", "ana.secreta@synthetic.example").put("phone", "(11) 98888-7777")
                .put("bio", "Faixa preta").put("directoryConsent", true).put("consentDeclaredByManagement", true);
        mod(mod(in, jj, blue, 2), judo, null, null);
        var r = ok(send(HttpMethod.POST, "", in, who(t, Role.COORDINATOR)), 201);
        assertThat(r.get("name").asText()).isEqualTo("Ana Souza");
        assertThat(r.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(r.get("access").asText()).isEqualTo("NONE");
        assertThat(r.get("phone").asText()).isEqualTo("11988887777");
        assertThat(r.get("email").asText()).isEqualTo("ana.secreta@synthetic.example");
        assertThat(r.get("directoryConsent").asBoolean()).isTrue();
        assertThat(r.get("consentDeclaredByManagement").asBoolean()).isTrue();
        assertThat(r.get("units").findValuesAsText("name")).containsExactly("Unit", "Zona Sul");
        var mods = r.get("modalities");
        assertThat(mods.findValuesAsText("name")).containsExactly("Jiu-Jitsu", "Judô");
        assertThat(mods.get(0).get("levelId").asText()).isEqualTo(blue.toString());
        assertThat(mods.get(0).get("degree").asInt()).isEqualTo(2);
        assertThat(mods.get(1).get("levelId").isNull()).isTrue();
        assertThat(mods.get(1).get("degree").isNull()).isTrue();
        var list = call(MockMvcRequestBuilders.get("/api/v1/teachers"), who(t, Role.COORDINATOR)).getResponse().getContentAsString();
        assertThat(list).contains(r.get("id").asText());
        var created = audits(t, "teacher.created");
        assertThat(created).hasSize(1);
        assertThat(created.get(0)).contains("Ana Souza").doesNotContain("secreta", "98888", "11988887777");
    }

    @Test
    @DisplayName("CA2: nome, e-mail, telefone, apresentação e unidades inválidos dão 400 com o campo e nada é gravado")
    void invalid() throws Exception {
        var t = tenant();
        var c = who(t, Role.COORDINATOR);
        record Case(String field, ObjectNode body) {}
        for (Case k : List.of(
                new Case("name", input("A", t.unitId())),
                new Case("name", input("x".repeat(81), t.unitId())),
                new Case("name", input("    ", t.unitId())),
                new Case("email", input("Ana", t.unitId()).put("email", "sem-arroba")),
                new Case("phone", input("Ana", t.unitId()).put("phone", "123")),
                new Case("bio", input("Ana", t.unitId()).put("bio", "x".repeat(301))),
                new Case("unitIds", input("Ana")))) {
            var r = send(HttpMethod.POST, "", k.body(), c);
            assertThat(r.getStatus()).as(k.field()).isEqualTo(400);
            assertThat(r.getContentAsString()).contains(k.field());
        }
        assertThat(send(HttpMethod.POST, "", input("Ana", t.unitId()).put("bio", "x".repeat(300)), c).getStatus()).isEqualTo(201);
        assertThat(teachers()).isEqualTo(1);
    }

    @Test
    @DisplayName("CA3, CA11: PUT substitui unidades e modalidades, mantém situação e conta, funciona em inativo e audita sem contato")
    void update() throws Exception {
        var t = tenant();
        UUID second = unit(t, "Zona Sul", true);
        UUID judo = modality("Judô", true, false, t);
        UUID boxe = modality("Boxe", true, false, t);
        UUID id = create(t, mod(input("Ana", t.unitId()).put("email", "velho@synthetic.example"), judo, null, null));
        adminJdbc().update("update teacher set status = 'INACTIVE' where id = ?", id);
        var c = who(t, Role.COORDINATOR);
        var r = ok(send(HttpMethod.PUT, "/" + id, mod(input("Ana Maria", second).put("email", "novo@synthetic.example").put("phone", "11 3333-4444"),
                boxe, null, null), c), 200);
        assertThat(r.get("name").asText()).isEqualTo("Ana Maria");
        assertThat(r.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(r.get("access").asText()).isEqualTo("NONE");
        assertThat(r.get("units").findValuesAsText("name")).containsExactly("Zona Sul");
        assertThat(r.get("modalities").findValuesAsText("name")).containsExactly("Boxe");
        assertThat(adminJdbc().queryForObject("select name_key from teacher where id = ?", String.class, id)).isEqualTo("ana maria");
        var logged = audits(t, "teacher.updated");
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0)).contains("Ana Maria").doesNotContain("synthetic.example", "33334444");
        assertThat(send(HttpMethod.PUT, "/" + UUID.randomUUID(), input("Ana", t.unitId()), c).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("RF-MOD-02: editar com a graduação desligada não apaga o nível gravado")
    void updateKeepsHiddenLevel() throws Exception {
        var t = tenant();
        UUID jj = modality("Jiu-Jitsu", true, true, t);
        UUID blue = level(t, jj, "Azul", 4);
        UUID id = create(t, mod(input("Ana", t.unitId()), jj, blue, 2));
        modality("Jiu-Jitsu", true, false, t);
        var c = who(t, Role.COORDINATOR);
        var r = ok(send(HttpMethod.PUT, "/" + id, mod(input("Ana Maria", t.unitId()), jj, null, null), c), 200);
        assertThat(r.get("modalities").get(0).get("level").isNull()).isTrue();
        assertThat(adminJdbc().queryForObject("select level_id from teacher_modality where teacher_id = ?", UUID.class, id)).isEqualTo(blue);
        modality("Jiu-Jitsu", true, true, t);
        var back = ok(send(HttpMethod.GET, "/" + id, null, c), 200);
        assertThat(back.get("modalities").get(0).get("level").asText()).isEqualTo("Azul");
        assertThat(back.get("modalities").get(0).get("degree").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("CA4: graduação obrigatória só com graduação ligada; nível de outra modalidade, grau acima, repetida e inativa dão 400; grau 0 e máximo salvam")
    void modalities() throws Exception {
        var t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID jj = modality("Jiu-Jitsu", true, true, t);
        UUID blue = level(t, jj, "Azul", 4);
        UUID judo = modality("Judô", true, false, t);
        UUID boxe = modality("Boxe", false, false, t);
        UUID kf = modality("Kung Fu", true, true, t);
        UUID kfLevel = level(t, kf, "Faixa", 3);
        for (ObjectNode bad : List.of(
                mod(input("Ana", t.unitId()), jj, null, null),
                mod(input("Ana", t.unitId()), jj, kfLevel, 1),
                mod(input("Ana", t.unitId()), jj, blue, 5),
                mod(input("Ana", t.unitId()), jj, blue, -1),
                mod(input("Ana", t.unitId()), jj, UUID.randomUUID(), 1),
                mod(input("Ana", t.unitId()), judo, blue, null),
                mod(mod(input("Ana", t.unitId()), judo, null, null), judo, null, null),
                mod(input("Ana", t.unitId()), boxe, null, null))) {
            assertThat(send(HttpMethod.POST, "", bad, c).getStatus()).as(bad.toString()).isEqualTo(400);
        }
        assertThat(teachers()).isZero();
        ok(send(HttpMethod.POST, "", mod(mod(input("Ana", t.unitId()), jj, blue, 0), judo, null, null), c), 201);
        ok(send(HttpMethod.POST, "", mod(input("Beto", t.unitId()), jj, blue, 4), c), 201);
    }

    @Test
    @DisplayName("CA5: Administrador só adiciona unidade dele (403 fora), preserva as de fora no PUT e não zera as dele; Coordenador escolhe qualquer; inativa dá 400")
    void adminReach() throws Exception {
        var t = tenant();
        UUID b = unit(t, "B", true);
        UUID inactive = unit(t, "Inativa", false);
        var a = admin(t, t.unitId());
        assertThat(send(HttpMethod.POST, "", input("Ana", b), a).getStatus()).isEqualTo(403);
        assertThat(send(HttpMethod.POST, "", input("Ana", t.unitId(), b), a).getStatus()).isEqualTo(403);
        UUID id = UUID.fromString(ok(send(HttpMethod.POST, "", input("Ana", t.unitId()), a), 201).get("id").asText());
        // professor compartilhado: A (dele) e B (de fora)
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, b);
        UUID c2 = unit(t, "C", true);
        var a2 = admin(t, t.unitId(), c2);
        var r = ok(send(HttpMethod.PUT, "/" + id, input("Ana 2", c2), a2), 200);
        assertThat(r.get("units").findValuesAsText("name")).containsExactly("B", "C");
        assertThat(send(HttpMethod.PUT, "/" + id, input("Ana 3", b), a2).getStatus()).isEqualTo(403);
        assertThat(send(HttpMethod.PUT, "/" + id, input("Ana 3"), a2).getStatus()).isEqualTo(400);
        var coordinator = who(t, Role.COORDINATOR);
        ok(send(HttpMethod.PUT, "/" + id, input("Ana 4", b, c2), coordinator), 200);
        assertThat(send(HttpMethod.PUT, "/" + id, input("Ana 5", inactive), coordinator).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.PUT, "/" + id, input("Ana 5", UUID.randomUUID()), coordinator).getStatus()).isEqualTo(400);
        // sem alcance sobre o professor: 404
        assertThat(send(HttpMethod.PUT, "/" + id, input("Ana 6", t.unitId()), admin(t, t.unitId())).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA6: consentimento exige a declaração da gestão; os dois voltam no detalhe; sem consentimento aceita qualquer declaração")
    void consent() throws Exception {
        var t = tenant();
        var c = who(t, Role.COORDINATOR);
        var bad = send(HttpMethod.POST, "", input("Ana", t.unitId()).put("directoryConsent", true), c);
        assertThat(bad.getStatus()).isEqualTo(400);
        assertThat(bad.getContentAsString()).contains("consentDeclaredByManagement");
        var both = ok(send(HttpMethod.POST, "", input("Ana", t.unitId()).put("directoryConsent", true).put("consentDeclaredByManagement", true), c), 201);
        assertThat(both.get("directoryConsent").asBoolean()).isTrue();
        assertThat(both.get("consentDeclaredByManagement").asBoolean()).isTrue();
        var declared = ok(send(HttpMethod.POST, "", input("Beto", t.unitId()).put("consentDeclaredByManagement", true), c), 201);
        assertThat(declared.get("directoryConsent").asBoolean()).isFalse();
        assertThat(declared.get("consentDeclaredByManagement").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("CA7, CA11: foto 512 x 512 troca e apaga o arquivo anterior; tipo ou tamanho errado dá 400 e mantém a antiga; DELETE remove; auditoria")
    void photo() throws Exception {
        var t = tenant();
        UUID id = create(t, input("Ana", t.unitId()));
        var c = who(t, Role.COORDINATOR);
        var first = ok(call(photo(id, image("png", 512, 512)), c).getResponse(), 200);
        assertThat(first.get("photoUrl").asText()).isNotBlank();
        String file = photoColumn(id);
        assertThat(file).startsWith("teacher-").endsWith(".png");
        assertThat(exists(t, file)).isTrue();
        assertThat(call(photo(id, image("png", 100, 100)), c).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(photo(id, "texto".getBytes()), c).getResponse().getStatus()).isEqualTo(400);
        assertThat(call(photo(id, new byte[5 * 1024 * 1024 + 1]), c).getResponse().getStatus()).isEqualTo(400);
        assertThat(photoColumn(id)).isEqualTo(file);
        assertThat(exists(t, file)).isTrue();
        ok(call(photo(id, image("jpg", 512, 512)), c).getResponse(), 200);
        String second = photoColumn(id);
        assertThat(second).endsWith(".jpg");
        assertThat(exists(t, file)).isFalse();
        var gone = ok(call(MockMvcRequestBuilders.delete("/api/v1/teachers/" + id + "/photo"), c).getResponse(), 200);
        assertThat(gone.get("photoUrl").isNull()).isTrue();
        assertThat(photoColumn(id)).isNull();
        assertThat(exists(t, second)).isFalse();
        assertThat(audits(t, "teacher.photo_updated")).hasSize(2);
        assertThat(audits(t, "teacher.photo_removed")).hasSize(1);
    }

    @Test
    @DisplayName("CA8: opções trazem só modalidades ativas, com níveis quando a graduação está ligada; Administrador também lê")
    void options() throws Exception {
        var t = tenant();
        UUID jj = modality("Jiu-Jitsu", true, true, t);
        level(t, jj, "Branca", 4);
        level(t, jj, "Azul", 4);
        modality("Judô", true, false, t);
        modality("Boxe", false, false, t);
        for (AccountPrincipal who : List.of(who(t, Role.COORDINATOR), admin(t, t.unitId()))) {
            var r = ok(call(MockMvcRequestBuilders.get("/api/v1/teachers/options"), who).getResponse(), 200).get("modalities");
            assertThat(r.findValuesAsText("name")).contains("Jiu-Jitsu", "Judô").doesNotContain("Boxe");
            assertThat(r.get(0).get("graduationEnabled").asBoolean()).isTrue();
            assertThat(r).hasSize(2);
            assertThat(r.get(0).get("levels").findValuesAsText("name")).containsExactly("Branca", "Azul");
            assertThat(r.get(0).get("levels").get(0).get("maxDegree").asInt()).isEqualTo(4);
            assertThat(r.get(1).get("graduationEnabled").asBoolean()).isFalse();
            assertThat(r.get(1).get("levels")).isEmpty();
        }
    }

    @Test
    @DisplayName("CA9: Professor, Aluno e Responsável 403 em todas as rotas novas; sem sessão 401")
    void permissions() throws Exception {
        var t = tenant();
        UUID id = create(t, input("Ana", t.unitId()));
        for (Role role : List.of(Role.TEACHER, Role.STUDENT, Role.GUARDIAN)) {
            var who = who(t, role);
            assertThat(send(HttpMethod.POST, "", input("Beto", t.unitId()), who).getStatus()).as(role + " POST").isEqualTo(403);
            assertThat(send(HttpMethod.PUT, "/" + id, input("Beto", t.unitId()), who).getStatus()).as(role + " PUT").isEqualTo(403);
            assertThat(call(photo(id, image("png", 512, 512)), who).getResponse().getStatus()).as(role + " photo").isEqualTo(403);
            assertThat(call(MockMvcRequestBuilders.delete("/api/v1/teachers/" + id + "/photo"), who).getResponse().getStatus()).as(role + " delete").isEqualTo(403);
            assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/options"), who).getResponse().getStatus()).as(role + " options").isEqualTo(403);
        }
        assertThat(send(HttpMethod.POST, "", input("Beto", t.unitId()), null).getStatus()).isEqualTo(401);
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/teachers/options"), null).getResponse().getStatus()).isEqualTo(401);
        var a = admin(t, t.unitId());
        ok(call(photo(id, image("png", 512, 512)), a).getResponse(), 200);
        ok(call(MockMvcRequestBuilders.delete("/api/v1/teachers/" + id + "/photo"), a).getResponse(), 200);
        var noReach = admin(t, unit(t, "Outra", true));
        assertThat(call(photo(id, image("png", 512, 512)), noReach).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(MockMvcRequestBuilders.delete("/api/v1/teachers/" + id + "/photo"), noReach).getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA10: unidade, modalidade e nível de outra organização dão 400; PUT e foto de professor de outra organização dão 404; RLS com ctcore_app")
    void isolation() throws Exception {
        var a = tenant();
        var b = tenant();
        var c = who(a, Role.COORDINATOR);
        UUID jjB = modality("Jiu-Jitsu", true, true, b);
        UUID levelB = level(b, jjB, "Azul", 4);
        UUID judoA = modality("Judô", true, true, a);
        UUID levelA = level(a, judoA, "Faixa", 4);
        UUID ofB = create(b, input("Ana", b.unitId()));
        assertThat(send(HttpMethod.POST, "", input("X", b.unitId()), c).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "", mod(input("X", a.unitId()), jjB, levelB, 1), c).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "", mod(input("X", a.unitId()), judoA, levelB, 1), c).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.PUT, "/" + ofB, input("Invadido", a.unitId()), c).getStatus()).isEqualTo(404);
        assertThat(call(photo(ofB, image("png", 512, 512)), c).getResponse().getStatus()).isEqualTo(404);
        assertThat(call(MockMvcRequestBuilders.delete("/api/v1/teachers/" + ofB + "/photo"), c).getResponse().getStatus()).isEqualTo(404);
        assertThat(adminJdbc().queryForObject("select name from teacher where id = ?", String.class, ofB)).isEqualTo("Ana");
        assertThat(photoColumn(ofB)).isNull();
        assertThat(levelA).isNotNull();
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            assertThat(sql.executeUpdate("update teacher set name = 'Invadido' where id = '" + ofB + "'")).isZero();
            app.rollback();
        }
    }
}
