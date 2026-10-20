package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-01 (responsáveis): ligar, financeiro, limite, menor com responsável, conta e convite, desligar, reenviar, permissão e auditoria. */
class StudentGuardiansIT extends OrganizationBase {
    static final String G1_CPF = "52998224725", G2_CPF = "11144477735", G3_CPF = "12345678909";
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from email_message where to_email like '%.example'");
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

    private AccountPrincipal real(AccountPrincipal p) {
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private AccountPrincipal coordinator(Tenant t) {
        return real(who(t, Role.COORDINATOR));
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return real(new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units)));
    }

    private ObjectNode guardian(String name, String cpf, String email, Boolean financial) {
        ObjectNode g = json.createObjectNode().put("name", name).put("cpf", cpf).put("email", email).put("phone", "(11) 98765-4321");
        if (financial != null) g.put("financial", financial);
        return g;
    }

    private ObjectNode student(Tenant t, String name, int age, ObjectNode... guardians) {
        ObjectNode s = json.createObjectNode().put("registeredUnitId", t.unitId().toString()).put("name", name)
                .put("birthDate", LocalDate.now().minusYears(age).minusDays(30).toString()).put("postalCode", "01001000").put("street", "Rua Fictícia")
                .put("number", "10").put("district", "Centro").put("city", "São Paulo").put("state", "SP");
        if (age >= 18) s.put("cpf", "39053344705").put("email", "adulto@ficticia.example").put("phone", "11912345678");
        var list = s.putArray("guardians");
        for (ObjectNode g : guardians) list.add(g);
        return s;
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json")
                .content(body == null ? "{}" : body.toString()), who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isBlank() ? null : json.readTree(r.getContentAsString());
    }

    private String create(Tenant t, AccountPrincipal who, ObjectNode body) throws Exception {
        return ok(send(HttpMethod.POST, "", body, who), 201).get("id").asText();
    }

    private long count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Long.class, args);
    }

    private JsonNode reason(String text) {
        return json.createObjectNode().put("reason", text);
    }

    private String guardianId(String studentId, String email) {
        return adminJdbc().queryForObject("select g.id::text from guardian g join account a on a.id = g.account_id join student_guardian sg "
                + "on sg.guardian_id = g.id where sg.student_id = ?::uuid and a.email = ?", String.class, studentId, email);
    }

    @Test
    @DisplayName("RN-RSP-01: CA7, CA8, CA9: menor exige responsável; um é financeiro; dois com um financeiro; zero ou dois financeiros e três dão 400; conta, vínculo, convite e e-mail por responsável")
    void createsMinor() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        long accounts = count("select count(*) from account");
        assertThat(send(HttpMethod.POST, "", student(t, "Menor Sem", 10), who).getStatus()).isEqualTo(400);
        ObjectNode badPhone = guardian("Resp Um", G1_CPF, "r1@ficticia.example", null).put("phone", "123");
        JsonNode err = ok(send(HttpMethod.POST, "", student(t, "Menor Fone", 10, badPhone), who), 400);
        assertThat(err.get("fields").get(0).get("field").asText()).isEqualTo("guardians.phone");
        for (ObjectNode[] pair : new ObjectNode[][]{
                {guardian("Resp Um", G1_CPF, "r1@ficticia.example", false), guardian("Resp Dois", G2_CPF, "r2@ficticia.example", false)},
                {guardian("Resp Um", G1_CPF, "r1@ficticia.example", true), guardian("Resp Dois", G2_CPF, "r2@ficticia.example", true)}}) {
            assertThat(send(HttpMethod.POST, "", student(t, "Menor Fin", 10, pair), who).getStatus()).isEqualTo(400);
        }
        assertThat(send(HttpMethod.POST, "", student(t, "Menor Três", 10, guardian("A B", G1_CPF, "r1@ficticia.example", true),
                guardian("C D", G2_CPF, "r2@ficticia.example", false), guardian("E F", G3_CPF, "r3@ficticia.example", false)), who).getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from student")).isZero();
        assertThat(count("select count(*) from account")).isEqualTo(accounts);

        JsonNode one = ok(send(HttpMethod.POST, "", student(t, "Menor Um", 10, guardian("Resp Um", G1_CPF, "r1@ficticia.example", null)), who), 201);
        assertThat(one.get("minor").asBoolean()).isTrue();
        assertThat(one.get("guardians")).hasSize(1);
        assertThat(one.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        assertThat(one.get("guardians").get(0).get("access").asText()).isEqualTo("PENDING");
        assertThat(count("select count(*) from account where email = 'r1@ficticia.example' and status = 'INVITED'")).isEqualTo(1);
        assertThat(count("select count(*) from account_link l join account a on a.id = l.account_id where a.email = 'r1@ficticia.example' "
                + "and l.role = 'GUARDIAN' and l.status = 'PENDING'")).isEqualTo(1);
        assertThat(count("select count(*) from email_message where to_email = 'r1@ficticia.example' and template = 'INVITE'")).isEqualTo(1);

        JsonNode two = ok(send(HttpMethod.POST, "", student(t, "Menor Dois", 12, guardian("Resp Dois", G2_CPF, "r2@ficticia.example", false),
                guardian("Resp Três", G3_CPF, "r3@ficticia.example", true)), who), 201);
        assertThat(two.get("guardians")).hasSize(2);
        assertThat(two.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        assertThat(two.get("guardians").get(0).get("name").asText()).isEqualTo("Resp Três");
        assertThat(count("select count(distinct code_hash) from account_invite")).isEqualTo(3);
        assertThat(count("select count(*) from email_message where template = 'INVITE' and to_email in ('r2@ficticia.example', 'r3@ficticia.example')")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA10, CA11: o mesmo e-mail liga a vários alunos sem duplicar cadastro nem convite; conflito de CPF ou e-mail dá 409; conta ativa só ganha o vínculo")
    void reusesGuardian() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        create(t, who, student(t, "Filho A", 9, guardian("Resp Um", G1_CPF, "r1@ficticia.example", null)));
        UUID other = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", other, t.orgId(), "u" + other.toString().substring(0, 12));
        ObjectNode second = student(t, "Filho B", 7, guardian("Resp Um", G1_CPF, "R1@ficticia.example", null));
        second.put("registeredUnitId", other.toString());
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id in (select id from account where email = 'r1@ficticia.example')");
        String b = create(t, who, second);
        assertThat(count("select count(*) from guardian")).isEqualTo(1);
        assertThat(count("select count(*) from account where lower(email) = 'r1@ficticia.example'")).isEqualTo(1);
        assertThat(count("select count(*) from email_message where template = 'INVITE'")).isEqualTo(1);
        assertThat(count("select count(*) from student_guardian where student_id = ?::uuid", b)).isEqualTo(1);

        long students = count("select count(*) from student");
        for (ObjectNode g : new ObjectNode[]{guardian("Resp Um", G2_CPF, "r1@ficticia.example", null), guardian("Outro", G1_CPF, "r9@ficticia.example", null)}) {
            JsonNode error = ok(send(HttpMethod.POST, "", student(t, "Filho C", 5, g), who), 409);
            assertThat(error.get("code").asText()).isEqualTo("GUARDIAN_CONFLICT");
        }
        assertThat(count("select count(*) from student")).isEqualTo(students);

        adminJdbc().update("insert into account (email, name, status, password_hash) values ('ativa@ficticia.example', 'Ativa', 'ACTIVE', 'x')");
        create(t, who, student(t, "Filho D", 6, guardian("Resp Ativa", G3_CPF, "ativa@ficticia.example", null)));
        assertThat(count("select count(*) from account where email = 'ativa@ficticia.example' and status = 'ACTIVE' and password_hash = 'x'")).isEqualTo(1);
        assertThat(count("select count(*) from account_link l join account a on a.id = l.account_id where a.email = 'ativa@ficticia.example' and l.role = 'GUARDIAN'")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA12, CA13: ligar com motivo, financeiro, limite de dois e desligar (menor nunca sem responsável; o financeiro passa ao que fica; adulto desliga o último)")
    void linkAndUnlink() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = admin(t, t.unitId());
        String id = create(t, who, student(t, "Aluno Adulto", 30));
        ObjectNode g1 = guardian("Resp Um", G1_CPF, "r1@ficticia.example", null);
        for (String bad : new String[]{null, "ab", "x".repeat(201)}) {
            ObjectNode body = g1.deepCopy();
            if (bad != null) body.put("reason", bad);
            assertThat(send(HttpMethod.POST, "/" + id + "/guardians", body, who).getStatus()).as(String.valueOf(bad)).isEqualTo(400);
        }
        assertThat(count("select count(*) from student_guardian")).isZero();
        JsonNode linked = ok(send(HttpMethod.POST, "/" + id + "/guardians", g1.deepCopy().put("reason", "Pedido da família"), who), 200);
        assertThat(linked.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        ok(send(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Dois", G2_CPF, "r2@ficticia.example", null).put("reason", "Segundo"), who), 200);
        JsonNode third = ok(send(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Três", G3_CPF, "r3@ficticia.example", null).put("reason", "Terceiro"), who), 409);
        assertThat(third.get("code").asText()).isEqualTo("GUARDIAN_LIMIT");

        String g2 = guardianId(id, "r2@ficticia.example"), g1Id = guardianId(id, "r1@ficticia.example");
        assertThat(send(HttpMethod.PUT, "/" + id + "/guardians/" + g2, json.createObjectNode().put("financial", false), who).getStatus()).isEqualTo(400);
        JsonNode swapped = ok(send(HttpMethod.PUT, "/" + id + "/guardians/" + g2, json.createObjectNode().put("financial", true), who), 200);
        assertThat(swapped.get("guardians").get(0).get("guardianId").asText()).isEqualTo(g2);
        assertThat(count("select count(*) from student_guardian where student_id = ?::uuid and financial", id)).isEqualTo(1);

        assertThat(send(HttpMethod.POST, "/" + id + "/guardians/" + g2 + "/unlink", reason("x"), who).getStatus()).isEqualTo(400);
        JsonNode after = ok(send(HttpMethod.POST, "/" + id + "/guardians/" + g2 + "/unlink", reason("Pedido do responsável"), who), 200);
        assertThat(after.get("guardians")).hasSize(1);
        assertThat(after.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        ok(send(HttpMethod.POST, "/" + id + "/guardians/" + g1Id + "/unlink", reason("Sem responsável"), who), 200);
        assertThat(count("select count(*) from student_guardian")).isZero();

        String minor = create(t, who, student(t, "Aluno Menor", 8, guardian("Resp Um", G1_CPF, "r1@ficticia.example", null)));
        String mg = guardianId(minor, "r1@ficticia.example");
        JsonNode refused = ok(send(HttpMethod.POST, "/" + minor + "/guardians/" + mg + "/unlink", reason("Tentativa"), who), 409);
        assertThat(refused.get("code").asText()).isEqualTo("MINOR_NEEDS_GUARDIAN");
        assertThat(count("select count(*) from student_guardian where student_id = ?::uuid", minor)).isEqualTo(1);
        ok(send(HttpMethod.POST, "/" + minor + "/guardians", guardian("Resp Dois", G2_CPF, "r2@ficticia.example", true).put("reason", "Segundo"), who), 200);
        JsonNode left = ok(send(HttpMethod.POST, "/" + minor + "/guardians/" + guardianId(minor, "r2@ficticia.example") + "/unlink", reason("Troca"), who), 200);
        assertThat(left.get("guardians")).hasSize(1);
        assertThat(left.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        assertThat(left.get("guardians").get(0).get("guardianId").asText()).isEqualTo(mg);
    }

    @Test
    @DisplayName("CA14: o desligado recebe e-mail neutro; sem aluno nenhum o vínculo GUARDIAN termina e o convite é cancelado; com outro aluno continua")
    void unlinkNotifies() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String a = create(t, who, student(t, "Filho A", 30));
        String b = create(t, who, student(t, "Filho B", 31).put("cpf", "11144477735"));
        for (String id : new String[]{a, b}) {
            ok(send(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Um", G1_CPF, "r1@ficticia.example", null).put("reason", "Ligar"), who), 200);
        }
        String g = guardianId(a, "r1@ficticia.example");
        ok(send(HttpMethod.POST, "/" + a + "/guardians/" + g + "/unlink", reason("Motivo reservado"), who), 200);
        assertThat(count("select count(*) from account_link l join account x on x.id = l.account_id where x.email = 'r1@ficticia.example' and l.status = 'PENDING'")).isEqualTo(1);
        var mails = adminJdbc().queryForList("select subject || coalesce(body, '') from email_message where template = 'GUARDIAN_UNLINKED' and to_email = 'r1@ficticia.example'", String.class);
        assertThat(mails).hasSize(1);
        assertThat(mails.get(0)).doesNotContain("Filho").doesNotContain("Motivo reservado");
        ok(send(HttpMethod.POST, "/" + b + "/guardians/" + g + "/unlink", reason("Último aluno"), who), 200);
        assertThat(count("select count(*) from account_link l join account x on x.id = l.account_id where x.email = 'r1@ficticia.example' and l.status = 'ENDED'")).isEqualTo(1);
        assertThat(count("select count(*) from account_invite i join account x on x.id = i.account_id where x.email = 'r1@ficticia.example' and i.canceled_at is null and i.used_at is null")).isZero();
    }

    @Test
    @DisplayName("CA15: reenvio cancela o convite anterior; o quarto no dia dá 429; acesso ativo dá 409")
    void resends() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String id = create(t, who, student(t, "Filho", 8, guardian("Resp Um", G1_CPF, "r1@ficticia.example", null)));
        String g = guardianId(id, "r1@ficticia.example");
        for (int i = 0; i < 3; i++) assertThat(send(HttpMethod.POST, "/" + id + "/guardians/" + g + "/invite", null, who).getStatus()).isEqualTo(204);
        assertThat(count("select count(*) from account_invite where canceled_at is null and used_at is null")).isEqualTo(1);
        assertThat(count("select count(*) from account_invite where canceled_at is not null")).isEqualTo(3);
        assertThat(send(HttpMethod.POST, "/" + id + "/guardians/" + g + "/invite", null, who).getStatus()).isEqualTo(429);
        adminJdbc().update("update account_link set status = 'ACTIVE' where account_id in (select id from account where email = 'r1@ficticia.example')");
        assertThat(send(HttpMethod.POST, "/" + id + "/guardians/" + g + "/invite", null, who).getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("CA20, CA21: perfis sem permissão 403, sem sessão 401, Administrador sem alcance 404 e outra organização 404")
    void permissionsAndIsolation() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String id = create(t, who, student(t, "Filho", 8, guardian("Resp Um", G1_CPF, "r1@ficticia.example", null)));
        String g = guardianId(id, "r1@ficticia.example");
        UUID other = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", other, t.orgId(), "u" + other.toString().substring(0, 12));
        record Call(HttpMethod m, String path, JsonNode body) {}
        var calls = java.util.List.of(new Call(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Dois", G2_CPF, "r2@ficticia.example", null).put("reason", "Ligar")),
                new Call(HttpMethod.PUT, "/" + id + "/guardians/" + g, json.createObjectNode().put("financial", true)),
                new Call(HttpMethod.POST, "/" + id + "/guardians/" + g + "/unlink", reason("Desligar")),
                new Call(HttpMethod.POST, "/" + id + "/guardians/" + g + "/invite", null));
        Tenant outside = tenant();
        for (Call c : calls) {
            for (Role role : new Role[]{Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
                assertThat(send(c.m(), c.path(), c.body(), who(t, role)).getStatus()).as(c.path() + role).isEqualTo(403);
            }
            assertThat(send(c.m(), c.path(), c.body(), null).getStatus()).as(c.path()).isEqualTo(401);
            assertThat(send(c.m(), c.path(), c.body(), admin(t, other)).getStatus()).as(c.path()).isEqualTo(404);
            assertThat(send(c.m(), c.path(), c.body(), coordinator(outside)).getStatus()).as(c.path()).isEqualTo(404);
        }
        assertThat(count("select count(*) from student_guardian")).isEqualTo(1);
        assertThat(count("select count(*) from guardian")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA22: auditoria de ligar, trocar financeiro, desligar e reenviar tem ator, ids e motivo, sem CPF, e-mail ou telefone")
    void audit() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String id = create(t, who, student(t, "Filho", 30));
        ok(send(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Um", G1_CPF, "r1@ficticia.example", null).put("reason", "Motivo de ligar"), who), 200);
        ok(send(HttpMethod.POST, "/" + id + "/guardians", guardian("Resp Dois", G2_CPF, "r2@ficticia.example", null).put("reason", "Segundo"), who), 200);
        String g2 = guardianId(id, "r2@ficticia.example");
        ok(send(HttpMethod.PUT, "/" + id + "/guardians/" + g2, json.createObjectNode().put("financial", true), who), 200);
        assertThat(send(HttpMethod.POST, "/" + id + "/guardians/" + g2 + "/invite", null, who).getStatus()).isEqualTo(204);
        ok(send(HttpMethod.POST, "/" + id + "/guardians/" + g2 + "/unlink", reason("Motivo de desligar"), who), 200);
        for (String action : new String[]{"student.guardian_linked", "student.guardian_financial_changed", "student.guardian_invite_resent", "student.guardian_unlinked"}) {
            var rows = adminJdbc().queryForList("select entity_id || coalesce(after_data::text, '') || coalesce(reason, '') from audit_log where organization_id = ? and action = ?",
                    String.class, t.orgId(), action);
            assertThat(rows).as(action).isNotEmpty();
            for (String row : rows) assertThat(row).contains(id).doesNotContain(G1_CPF).doesNotContain(G2_CPF).doesNotContain("ficticia.example").doesNotContain("98765");
        }
        assertThat(adminJdbc().queryForList("select reason from audit_log where action = 'student.guardian_linked' and organization_id = ?", String.class, t.orgId()))
                .contains("Motivo de ligar");
        assertThat(adminJdbc().queryForObject("select reason from audit_log where action = 'student.guardian_unlinked' and organization_id = ?", String.class, t.orgId()))
                .isEqualTo("Motivo de desligar");
    }
}
