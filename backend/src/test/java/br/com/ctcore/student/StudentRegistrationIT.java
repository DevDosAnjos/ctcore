package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-01 (cadastro do adulto, validações, duplicidade, busca por CPF, permissão, alcance, isolamento e auditoria), com banco real. */
class StudentRegistrationIT extends OrganizationBase {
    static final String CPF = "52998224725";
    static final String OTHER_CPF = "11144477735";
    @Autowired ObjectMapper json;

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

    private ObjectNode adult(Tenant t, String name, String cpf) {
        return json.createObjectNode().put("registeredUnitId", t.unitId().toString()).put("name", name).put("birthDate", "1990-05-20")
                .put("postalCode", "01001-000").put("street", "Rua Fictícia").put("number", "10").put("district", "Centro").put("city", "São Paulo")
                .put("state", "SP").put("cpf", cpf).put("email", "aluno@ficticia.example").put("phone", "(11) 91234-5678");
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
        return real(who(t, Role.COORDINATOR));
    }

    /** Convite grava quem enviou: a conta do ator precisa existir. */
    private AccountPrincipal real(AccountPrincipal p) {
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private long students() {
        return adminJdbc().queryForObject("select count(*) from student", Long.class);
    }

    private UUID unit(Tenant t, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, ?)", id, t.orgId(),
                "u" + id.toString().substring(0, 12), active);
        return id;
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
    }

    @Test
    @DisplayName("CA1, CA3: cria o adulto, devolve o detalhe (CPF e telefone só com dígitos) e ignora campos de saúde")
    void createsAdult() throws Exception {
        Tenant t = tenant();
        ObjectNode body = adult(t, "Aluno Fictício", "529.982.247-25").put("bloodType", "O+").put("medicalCertificate", "x");
        JsonNode created = ok(send(HttpMethod.POST, "", body, coordinator(t)), 201);
        assertThat(created.get("cpf").asText()).isEqualTo(CPF);
        assertThat(created.get("phone").asText()).isEqualTo("11912345678");
        assertThat(created.get("registeredUnitId").asText()).isEqualTo(t.unitId().toString());
        assertThat(created.get("guardians")).isEmpty();
        assertThat(created.has("bloodType") || created.has("medicalCertificate")).isFalse();
        assertThat(ok(send(HttpMethod.GET, "/" + created.get("id").asText(), null, coordinator(t)), 200)).isEqualTo(created);
        assertThat(adminJdbc().queryForObject("select count(*) from information_schema.columns where table_name = 'student' "
                + "and column_name ~ 'blood|medical'", Long.class)).isZero();
    }

    @Test
    @DisplayName("RN-ALU-01, RN-ALU-03: CA2: dados inválidos dão 400 com o campo e nada é gravado")
    void validates() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String future = LocalDate.now().plusDays(1).toString();
        List<ObjectNode> bad = List.of(adult(t, "A", CPF), adult(t, "A".repeat(81), CPF), adult(t, "Aluno", CPF).put("birthDate", future),
                adult(t, "Aluno", "52998224724"), adult(t, "Aluno", "111.111.111-11"), adult(t, "Aluno", CPF).put("phone", "91234-5678"),
                adult(t, "Aluno", CPF).put("state", "XX"), adult(t, "Aluno", CPF).put("postalCode", "123"),
                adult(t, "Aluno", "").put("email", "aluno@ficticia.example"), adult(t, "Aluno", CPF).put("email", ""),
                adult(t, "Aluno", CPF).put("phone", ""), adult(t, "Aluno", CPF).put("emergencyName", "Mãe"),
                adult(t, "Aluno", CPF).put("gender", "X"), adult(t, "Aluno", CPF).put("referral", "X"));
        for (ObjectNode body : bad) {
            JsonNode error = ok(send(HttpMethod.POST, "", body, who), 400);
            assertThat(error.get("fields")).as(body.toString()).isNotEmpty();
        }
        assertThat(students()).isZero();
        ok(send(HttpMethod.POST, "", adult(t, "Aluno", CPF).put("emergencyName", "Mãe").put("emergencyRelationship", "Mãe")
                .put("emergencyPhone", "11987654321").put("gender", "FEMALE").put("referral", "INSTAGRAM"), who), 201);
    }

    @Test
    @DisplayName("RN-ALU-05: CA4: CPF repetido dá 409 só com id, nome e foto, também para o Administrador de outra unidade; outra organização cria")
    void cpfExists() throws Exception {
        Tenant t = tenant();
        String id = ok(send(HttpMethod.POST, "", adult(t, "Aluno Existente", CPF), coordinator(t)), 201).get("id").asText();
        UUID other = unit(t, true);
        ObjectNode again = adult(t, "Outro Nome", CPF).put("registeredUnitId", other.toString());
        JsonNode error = ok(send(HttpMethod.POST, "", again, admin(t, other)), 409);
        assertThat(error.get("code").asText()).isEqualTo("STUDENT_CPF_EXISTS");
        assertThat(error.get("fields")).hasSize(3);
        assertThat(error.get("fields").get(0).get("message").asText()).isEqualTo(id);
        assertThat(error.get("fields").get(1).get("message").asText()).isEqualTo("Aluno Existente");
        assertThat(error.toString()).doesNotContain(CPF).doesNotContain("ficticia.example");
        assertThat(students()).isEqualTo(1);
        Tenant outside = tenant();
        ok(send(HttpMethod.POST, "", adult(outside, "Aluno Existente", CPF), coordinator(outside)), 201);
    }

    @Test
    @DisplayName("CA5: sem CPF, mesmo nome (sem acento e caixa) e nascimento dão aviso; confirmar ou mudar o nascimento passa do aviso")
    void possibleDuplicate() throws Exception {
        Tenant t = tenant();
        AccountPrincipal who = coordinator(t);
        String birth = LocalDate.now().minusYears(10).toString();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, 'João Pedro', 'joao pedro', ?, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP')", UUID.randomUUID(), t.orgId(), t.unitId(),
                java.sql.Date.valueOf(birth));
        ObjectNode same = adult(t, "JOAO PEDRO", "").put("birthDate", birth).put("cpf", "");
        same.putArray("guardians").addObject().put("name", "Responsável Fictício").put("cpf", OTHER_CPF).put("email", "resp@ficticia.example")
                .put("phone", "11987654321");
        JsonNode warning = ok(send(HttpMethod.POST, "", same, who), 409);
        assertThat(warning.get("code").asText()).isEqualTo("STUDENT_POSSIBLE_DUPLICATE");
        assertThat(warning.get("fields")).hasSize(3);
        ok(send(HttpMethod.POST, "", same.deepCopy().put("confirmDuplicate", true), who), 201);
        ObjectNode other = same.deepCopy().put("birthDate", LocalDate.now().minusYears(11).toString());
        ((ObjectNode) other.get("guardians").get(0)).put("cpf", "12345678909").put("email", "resp2@ficticia.example");
        ok(send(HttpMethod.POST, "", other, who), 201);
    }

    @Test
    @DisplayName("RN-ALU-06: CA6: busca por CPF devolve só id, nome, foto e situação do aluno de qualquer unidade; inexistente, outra organização e inválido")
    void lookup() throws Exception {
        Tenant t = tenant();
        String id = ok(send(HttpMethod.POST, "", adult(t, "Aluno Busca", CPF), coordinator(t)), 201).get("id").asText();
        UUID other = unit(t, true);
        JsonNode found = ok(send(HttpMethod.GET, "/lookup?cpf=" + CPF, null, admin(t, other)), 200);
        assertThat(found.get("id").asText()).isEqualTo(id);
        assertThat(found.get("name").asText()).isEqualTo("Aluno Busca");
        assertThat(found.size()).isEqualTo(4); // id, nome, foto e situação da matrícula (R8)
        assertThat(found.get("enrollmentStatus").asText()).isEqualTo("NONE");
        assertThat(send(HttpMethod.GET, "/lookup?cpf=" + OTHER_CPF, null, coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/lookup?cpf=" + CPF, null, coordinator(tenant())).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/lookup?cpf=123", null, coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/lookup", null, coordinator(t)).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("RN-RSP-01: CA8: menor sem responsável não é gravado (os responsáveis entram na parte 2)")
    void minorNeedsGuardian() throws Exception {
        Tenant t = tenant();
        ObjectNode minor = adult(t, "Aluno Menor", "").put("birthDate", LocalDate.now().minusYears(10).toString()).put("cpf", "");
        assertThat(ok(send(HttpMethod.POST, "", minor, coordinator(t)), 400).get("fields").get(0).get("field").asText()).isEqualTo("guardians");
        assertThat(students()).isZero();
    }

    @Test
    @DisplayName("CA20: perfis sem STUDENT_MANAGE dão 403, sem sessão 401; alcance do Administrador e unidade inválida")
    void permissionsAndScope() throws Exception {
        Tenant t = tenant();
        String id = ok(send(HttpMethod.POST, "", adult(t, "Aluno Alcance", CPF), coordinator(t)), 201).get("id").asText();
        for (Role role : new Role[]{Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = who(t, role);
            assertThat(send(HttpMethod.POST, "", adult(t, "X Y", OTHER_CPF), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(HttpMethod.GET, "/" + id, null, who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(HttpMethod.GET, "/lookup?cpf=" + CPF, null, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(send(HttpMethod.GET, "/" + id, null, null).getStatus()).isEqualTo(401);
        assertThat(send(HttpMethod.POST, "", adult(t, "X Y", OTHER_CPF), null).getStatus()).isEqualTo(401);
        UUID other = unit(t, true), inactive = unit(t, false);
        AccountPrincipal outsider = admin(t, other);
        assertThat(send(HttpMethod.GET, "/" + id, null, outsider).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/" + id, null, admin(t, t.unitId())).getStatus()).isEqualTo(200);
        assertThat(send(HttpMethod.POST, "", adult(t, "X Y", OTHER_CPF), outsider).getStatus()).isEqualTo(403);
        ok(send(HttpMethod.POST, "", adult(t, "X Y", OTHER_CPF).put("registeredUnitId", other.toString()), coordinator(t)), 201);
        assertThat(send(HttpMethod.POST, "", adult(t, "X Z", "12345678909").put("registeredUnitId", inactive.toString()), coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "", adult(t, "X Z", "12345678909").put("registeredUnitId", UUID.randomUUID().toString()), coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.POST, "", adult(t, "X Z", "12345678909").put("registeredUnitId", tenant().unitId().toString()), coordinator(t)).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA21: aluno de outra organização dá 404 e a RLS não o mostra")
    void isolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        String id = ok(send(HttpMethod.POST, "", adult(a, "Aluno A", CPF), coordinator(a)), 201).get("id").asText();
        assertThat(send(HttpMethod.GET, "/" + id, null, coordinator(b)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/" + UUID.randomUUID(), null, coordinator(a)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA22: a auditoria de criação tem ator, ids e nenhum valor de CPF, e-mail, telefone ou endereço")
    void audit() throws Exception {
        Tenant t = tenant();
        String id = ok(send(HttpMethod.POST, "", adult(t, "Aluno Auditado", CPF), coordinator(t)), 201).get("id").asText();
        var rows = adminJdbc().queryForList("select entity_id || coalesce(after_data::text, '') from audit_log where organization_id = ? and action = 'student.created'",
                String.class, t.orgId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).contains(id).contains("informado").doesNotContain(CPF).doesNotContain("ficticia.example").doesNotContain("91234")
                .doesNotContain("Rua Fictícia").doesNotContain("01001");
    }
}
