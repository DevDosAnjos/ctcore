package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-02: selos calculados na leitura (RN-ALU-09, RN-ALU-10), filtro por selo, perfis e isolamento, com banco real. */
class StudentSealsIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean Clock clock;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private void today(LocalDate d) {
        doReturn(d.atTime(12, 0).toInstant(ZoneOffset.UTC)).when(clock).instant();
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students" + query), who).getResponse();
    }

    private JsonNode body(String query, AccountPrincipal who) throws Exception {
        var r = get(query, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private UUID student(Tenant t, String name, LocalDate birth, String cpf, String email, boolean emailProblem) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, email_problem_at, postal_code, street, number, district, city, state, emergency_name, emergency_relationship, emergency_phone) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, " + (emailProblem ? "now()" : "null") + ", '01001000', 'Rua', '1', 'Centro', 'São Paulo', 'SP', 'Contato', 'Mãe', '11911112222')", id, t.orgId(), t.unitId(), name,
                Modalities.normalize(name), birth, cpf, email);
        return id;
    }

    private UUID adult(Tenant t, String name, String cpf, String email) {
        return student(t, name, LocalDate.of(1990, 5, 10), cpf, email, false);
    }

    private void financial(Tenant t, UUID student, String cpf, String email, boolean problem) {
        UUID account = UUID.randomUUID();
        UUID guardian = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, email);
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone, email_problem_at) values (?, ?, ?, 'Responsável', ?, "
                + "'11987654321', " + (problem ? "now()" : "null") + ")", guardian, t.orgId(), account, cpf);
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, true)", t.orgId(), student,
                guardian);
    }

    private List<String> seals(JsonNode b, String name) {
        for (JsonNode i : b.get("items")) {
            if (i.get("name").asText().equals(name)) {
                List<String> out = new ArrayList<>();
                i.get("seals").forEach(s -> out.add(s.asText()));
                return out;
            }
        }
        throw new AssertionError(name);
    }

    private List<String> names(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.get("items").forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    @Test
    @DisplayName("CA1: aniversário hoje; 29/02 em 28/02 de ano não bissexto, em 29/02 de ano bissexto e não em 01/03")
    void birthday() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        student(t, "Aniversariante", LocalDate.of(1990, 3, 15), "11111111111", "a@ficticia.example", false);
        student(t, "Outro", LocalDate.of(1990, 3, 16), "22222222222", "b@ficticia.example", false);
        student(t, "Bissexto", LocalDate.of(1992, 2, 29), "33333333333", "c@ficticia.example", false);
        today(LocalDate.of(2025, 3, 15));
        JsonNode b = body("", c);
        assertThat(seals(b, "Aniversariante")).containsExactly("BIRTHDAY_TODAY");
        assertThat(seals(b, "Outro")).isEmpty();
        today(LocalDate.of(2025, 2, 28));
        assertThat(seals(body("", c), "Bissexto")).containsExactly("BIRTHDAY_TODAY");
        today(LocalDate.of(2025, 3, 1));
        assertThat(seals(body("", c), "Bissexto")).isEmpty();
        today(LocalDate.of(2024, 2, 28));
        assertThat(seals(body("", c), "Bissexto")).isEmpty();
        today(LocalDate.of(2024, 2, 29));
        assertThat(seals(body("", c), "Bissexto")).containsExactly("BIRTHDAY_TODAY");
    }

    @Test
    @DisplayName("CA2, CA3: cadastro incompleto por adulto e por menor; dado da criança é opcional; completar o CPF tira o selo")
    void incomplete() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        today(LocalDate.of(2025, 6, 1));
        UUID semCpf = adult(t, "Adulto Sem Cpf", null, "a@ficticia.example");
        adult(t, "Adulto Sem Email", "22222222222", null);
        adult(t, "Adulto Completo", "33333333333", "c@ficticia.example");
        LocalDate kid = LocalDate.of(2015, 1, 1);
        UUID ok = student(t, "Menor Completo", kid, null, null, false);
        financial(t, ok, "44444444444", "r1@ficticia.example", false);
        student(t, "Menor Sem Financeiro", kid, "66666666666", "m@ficticia.example", false);
        JsonNode b = body("", c);
        assertThat(seals(b, "Adulto Sem Cpf")).containsExactly("INCOMPLETE_REGISTRATION");
        assertThat(seals(b, "Adulto Sem Email")).containsExactly("INCOMPLETE_REGISTRATION");
        assertThat(seals(b, "Adulto Completo")).isEmpty();
        assertThat(seals(b, "Menor Completo")).isEmpty();
        assertThat(seals(b, "Menor Sem Financeiro")).containsExactly("INCOMPLETE_REGISTRATION");
        adminJdbc().update("update student set cpf = '77777777777' where id = ?", semCpf);
        assertThat(seals(body("", c), "Adulto Sem Cpf")).isEmpty();
    }

    @Test
    @DisplayName("CA4, CA5: e-mail com problema de quem paga; só o do aluno menor não conta; ordem dos selos; sem selo, lista vazia")
    void emailProblemAndOrder() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        today(LocalDate.of(2025, 5, 10));
        student(t, "Adulto Problema", LocalDate.of(1990, 5, 10), null, "a@ficticia.example", true);
        UUID kid = student(t, "Menor Resp Problema", LocalDate.of(2015, 1, 1), null, null, false);
        financial(t, kid, "44444444444", "r1@ficticia.example", true);
        UUID own = student(t, "Menor Proprio", LocalDate.of(2015, 1, 1), null, "m@ficticia.example", true);
        financial(t, own, "55555555555", "r2@ficticia.example", false);
        student(t, "Sem Selo", LocalDate.of(1990, 1, 1), "66666666666", "s@ficticia.example", false);
        JsonNode b = body("", c);
        assertThat(seals(b, "Adulto Problema")).containsExactly("INCOMPLETE_REGISTRATION", "EMAIL_PROBLEM", "BIRTHDAY_TODAY");
        assertThat(seals(b, "Menor Resp Problema")).containsExactly("EMAIL_PROBLEM");
        assertThat(seals(b, "Menor Proprio")).isEmpty();
        assertThat(seals(b, "Sem Selo")).isEmpty();
        assertThat(names(body("?seal=EMAIL_PROBLEM", c))).containsExactly("Adulto Problema", "Menor Resp Problema");
    }

    @Test
    @DisplayName("CA6: filtro por selo sozinho e combinado, total confere, desconhecido 400, Administrador só da unidade")
    void filter() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        today(LocalDate.of(2025, 6, 1));
        UUID second = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Zona Sul', 'zs')", second, t.orgId());
        adult(t, "Ana Sem Cpf", null, "a@ficticia.example");
        adult(t, "Bia Completa", "22222222222", "b@ficticia.example");
        UUID far = adult(t, "Caio Sem Cpf", null, "c@ficticia.example");
        adminJdbc().update("update student set registered_unit_id = ? where id = ?", second, far);
        JsonNode all = body("?seal=INCOMPLETE_REGISTRATION", c);
        assertThat(names(all)).containsExactly("Ana Sem Cpf", "Caio Sem Cpf");
        assertThat(all.get("total").asLong()).isEqualTo(2);
        assertThat(names(body("?seal=INCOMPLETE_REGISTRATION&q=caio&unitId=" + second, c))).containsExactly("Caio Sem Cpf");
        UUID adultRange = adminJdbc().queryForObject("select id from age_range where organization_id = ? and name = 'Adulto'", UUID.class, t.orgId());
        assertThat(names(body("?seal=INCOMPLETE_REGISTRATION&ageRangeId=" + adultRange, c))).containsExactly("Ana Sem Cpf", "Caio Sem Cpf");
        assertThat(names(body("?seal=BIRTHDAY_TODAY", c))).isEmpty();
        assertThat(get("?seal=BOLSISTA", c).getStatus()).isEqualTo(400);
        assertThat(names(body("?seal=INCOMPLETE_REGISTRATION", who(t, Role.ADMINISTRATOR)))).containsExactly("Ana Sem Cpf");
    }

    @Test
    @DisplayName("CA7, CA8: perfis sem acesso 403, anônimo 401, outra organização não aparece, resposta sem dado pessoal")
    void permissionsIsolationMinimal() throws Exception {
        Tenant a = tenant();
        Tenant b = tenant();
        today(LocalDate.of(2025, 6, 1));
        adult(a, "Da A", null, "segredo@ficticia.example");
        adult(b, "Da B", null, "outro@ficticia.example");
        var ca = who(a, Role.COORDINATOR);
        assertThat(names(body("?seal=INCOMPLETE_REGISTRATION", ca))).containsExactly("Da A");
        assertThat(get("?seal=INCOMPLETE_REGISTRATION", ca).getContentAsString()).doesNotContain("segredo@ficticia.example").doesNotContain("\"email\"");
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get("?seal=EMAIL_PROBLEM", who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(get("?seal=EMAIL_PROBLEM", null).getStatus()).isEqualTo(401);
    }
}
