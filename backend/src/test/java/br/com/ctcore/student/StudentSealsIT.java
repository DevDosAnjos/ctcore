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
        EnrollmentFixtures.clean(adminJdbc());
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

    private void enrolled(Tenant t, UUID student) {
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", 0, null);
        adminJdbc().update("update enrollment set enrolled_at = ?::timestamptz where student_id = ?", "2020-01-01T00:00:00Z", student);
    }

    @Test
    @DisplayName("RF-ALU-02, RF-ALU-06: a data de referência é a do fuso da unidade; às 23h30 em Brasília (02h30 UTC) vale o dia de Brasília, e em Tóquio o de Tóquio")
    void referenceDateIsTheUnitDate() throws Exception {
        Tenant t = tenant();
        var coordinator = who(t, Role.COORDINATOR);
        var admin = who(t, Role.ADMINISTRATOR);
        enrolled(t, student(t, "Hoje em Brasilia", LocalDate.of(1990, 3, 15), "11111111111", "a@ficticia.example", false));
        enrolled(t, student(t, "Hoje em Toquio", LocalDate.of(1990, 3, 16), "22222222222", "b@ficticia.example", false));
        enrolled(t, student(t, "Vespera dos 18", LocalDate.of(2007, 3, 16), "33333333333", "c@ficticia.example", false));
        // 15/03/2025 23:30 em Brasília = 16/03 02:30 UTC = 16/03 11:30 em Tóquio
        doReturn(LocalDate.of(2025, 3, 16).atTime(2, 30).toInstant(ZoneOffset.UTC)).when(clock).instant();

        JsonNode brasilia = body("", coordinator);
        assertThat(seals(brasilia, "Hoje em Brasilia")).containsExactly("BIRTHDAY_TODAY");
        assertThat(seals(brasilia, "Hoje em Toquio")).isEmpty();
        assertThat(age(brasilia, "Vespera dos 18")).isEqualTo("17 true");
        assertThat(birthdayNames(admin)).containsExactly("Hoje em Brasilia");

        adminJdbc().update("update unit set timezone = 'Asia/Tokyo' where id = ?", t.unitId());
        JsonNode tokyo = body("", coordinator);
        assertThat(seals(tokyo, "Hoje em Toquio")).containsExactly("BIRTHDAY_TODAY");
        assertThat(seals(tokyo, "Hoje em Brasilia")).isEmpty();
        assertThat(age(tokyo, "Vespera dos 18")).isEqualTo("18 false");
        assertThat(birthdayNames(admin)).containsExactlyInAnyOrder("Hoje em Toquio", "Vespera dos 18");
    }

    private String age(JsonNode b, String name) {
        for (JsonNode i : b.get("items")) if (i.get("name").asText().equals(name)) return i.get("age").asInt() + " " + i.get("minor").asBoolean();
        throw new AssertionError(name);
    }

    private List<String> birthdayNames(AccountPrincipal who) throws Exception {
        var r = call(MockMvcRequestBuilders.get("/api/v1/students/birthdays"), who).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        List<String> out = new ArrayList<>();
        json.readTree(r.getContentAsString()).forEach(i -> out.add(i.get("name").asText()));
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

    private void enrolled(Tenant t, UUID student, String enrolledAt, String reopenedAt) {
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at, reopened_at) values (?, ?, ?, ?::timestamptz, ?::timestamptz)",
                UUID.randomUUID(), t.orgId(), student, enrolledAt, reopenedAt);
    }

    @Test
    @DisplayName("CA30 (R21, D8): o selo Novo vale até 30 dias da matrícula (dia 30 incluso, 31 fora), no fuso da unidade, volta com a reabertura e aparece na lista, na ficha e no filtro")
    void newSeal() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        today(LocalDate.of(2025, 6, 30));
        UUID day30 = adult(t, "Trinta Dias", "11111111111", "a@ficticia.example"), day31 = adult(t, "Trinta e Um", "22222222222", "b@ficticia.example"),
                fresh = adult(t, "Recente", "33333333333", "c@ficticia.example"), back = adult(t, "Voltou", "44444444444", "d@ficticia.example"),
                never = adult(t, "Sem Matricula", "55555555555", "e@ficticia.example");
        enrolled(t, day30, "2025-05-31T12:00:00-03:00", null);
        enrolled(t, day31, "2025-05-30T12:00:00-03:00", null);
        enrolled(t, fresh, "2025-06-30T08:00:00-03:00", null);
        enrolled(t, back, "2024-01-10T12:00:00-03:00", "2025-06-10T12:00:00-03:00");
        assertThat(never).isNotNull();
        JsonNode b = body("", c);
        assertThat(seals(b, "Trinta Dias")).containsExactly("NEW");
        assertThat(seals(b, "Trinta e Um")).isEmpty();
        assertThat(seals(b, "Recente")).containsExactly("NEW");
        assertThat(seals(b, "Voltou")).containsExactly("NEW");
        assertThat(seals(b, "Sem Matricula")).isEmpty();
        assertThat(names(body("?seal=NEW", c))).containsExactly("Recente", "Trinta Dias", "Voltou");
        var record = call(MockMvcRequestBuilders.get("/api/v1/students/" + day30 + "/record"), c).getResponse();
        assertThat(json.readTree(record.getContentAsString()).get("seals").toString()).contains("NEW");

        // 23h30 em Brasília (02h30 UTC do dia seguinte): a conta usa o dia da unidade, que ainda é 30/06; 31/05 00h30 em Brasília é o dia 30.
        doReturn(LocalDate.of(2025, 7, 1).atTime(2, 30).toInstant(ZoneOffset.UTC)).when(clock).instant();
        adminJdbc().update("update enrollment set enrolled_at = '2025-05-31T00:30:00-03:00' where student_id = ?", day30);
        assertThat(seals(body("", c), "Trinta Dias")).containsExactly("NEW");
        // Em Tóquio já é 01/07: o mesmo instante de matrícula (31/05 12h30 em Tóquio) fica a 31 dias.
        adminJdbc().update("update unit set timezone = 'Asia/Tokyo' where id = ?", t.unitId());
        assertThat(seals(body("", c), "Trinta Dias")).isEmpty();
    }

    private void benefit(Tenant t, UUID row, String type, long value, String validUntil, String status) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Gestora', 'ACTIVE')", account, "sch-" + account + ".example");
        adminJdbc().update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, "
                + "valid_until, status, ended_at) select ?, em.organization_id, em.id, em.student_id, em.unit_id, ?, ?, 'Motivo', 'Mestre', ?, "
                + (validUntil == null ? "null" : "date '" + validUntil + "'") + ", ?, case when ? = 'ENDED' then now() end from enrollment_modality em where em.id = ?",
                UUID.randomUUID(), type, value, account, status, status, row);
    }

    @Test
    @DisplayName("CA12 (RF-BEN-02, D10): Bolsista só com bolsa percentual ativa e dentro da validade; vencida, encerrada, desconto em reais e sem benefício não; lista, ficha e filtro")
    void scholarshipSeal() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        today(LocalDate.of(2025, 6, 30));
        String[] names = {"Com Bolsa", "Bolsa Hoje", "Bolsa Vencida", "Bolsa Encerrada", "So Desconto", "Sem Beneficio"};
        UUID[] ids = new UUID[names.length];
        UUID[] rows = new UUID[names.length];
        for (int i = 0; i < names.length; i++) {
            ids[i] = adult(t, names[i], "1111111111" + i, "s" + i + "@ficticia.example");
            rows[i] = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ids[i], t.unitId(), "ACTIVE", 0, null).row();
        }
        benefit(t, rows[0], "PERCENT", 30, null, "ACTIVE");
        benefit(t, rows[1], "PERCENT", 100, "2025-06-30", "ACTIVE");
        benefit(t, rows[2], "PERCENT", 30, "2025-06-29", "ACTIVE");
        benefit(t, rows[3], "PERCENT", 30, null, "ENDED");
        benefit(t, rows[4], "AMOUNT", 5000, null, "ACTIVE");
        JsonNode b = body("", c);
        assertThat(seals(b, "Com Bolsa")).contains("SCHOLARSHIP");
        assertThat(seals(b, "Bolsa Hoje")).contains("SCHOLARSHIP");
        for (String n : new String[] {"Bolsa Vencida", "Bolsa Encerrada", "So Desconto", "Sem Beneficio"}) assertThat(seals(b, n)).as(n).doesNotContain("SCHOLARSHIP");
        assertThat(names(body("?seal=SCHOLARSHIP", c))).containsExactly("Bolsa Hoje", "Com Bolsa");
        var record = call(MockMvcRequestBuilders.get("/api/v1/students/" + ids[0] + "/record"), c).getResponse();
        assertThat(json.readTree(record.getContentAsString()).get("seals").toString()).contains("SCHOLARSHIP");
        // Administrador só enxerga o alcance dele
        UUID other = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", other, t.orgId(), "o" + other.toString().substring(0, 12));
        assertThat(names(body("?seal=SCHOLARSHIP", who(t, Role.ADMINISTRATOR)))).containsExactly("Bolsa Hoje", "Com Bolsa");
        adminJdbc().update("update student set registered_unit_id = ? where id = ?", other, ids[0]);
        adminJdbc().update("update enrollment_modality set unit_id = ? where student_id = ?", other, ids[0]);
        adminJdbc().update("update benefit set unit_id = ? where student_id = ?", other, ids[0]);
        assertThat(names(body("?seal=SCHOLARSHIP", who(t, Role.ADMINISTRATOR)))).containsExactly("Bolsa Hoje");
        // modalidade encerrada tira o selo
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where student_id = ?", ids[1]);
        assertThat(names(body("?seal=SCHOLARSHIP", c))).containsExactly("Com Bolsa");
    }
}
