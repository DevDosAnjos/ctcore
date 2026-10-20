package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

/** RF-ALU-06: aniversariantes do dia: data, 29/02, alcance, perfis, contato, minimização, isolamento e leitura sem auditoria. */
class StudentBirthdaysIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @MockitoSpyBean Clock clock;
    @MockitoSpyBean StudentRecords records;
    private static final LocalDate TODAY = LocalDate.of(2025, 3, 15);

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

    private void today(LocalDate d) {
        doReturn(d.atTime(12, 0).toInstant(ZoneOffset.UTC)).when(clock).instant();
    }

    private MockHttpServletResponse get(AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students/birthdays"), who).getResponse();
    }

    private JsonNode ok(AccountPrincipal who) throws Exception {
        var r = get(who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private List<String> names(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    private UUID student(Tenant t, UUID unit, String name, LocalDate birth, String phone) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state, emergency_name, emergency_relationship, emergency_phone) values (?, ?, ?, ?, ?, ?, null, 'segredo@ficticia.example', ?, "
                + "'01001000', 'Rua Secreta', '1', 'Centro', 'São Paulo', 'SP', 'Contato', 'Mãe', '11911112222')",
                id, t.orgId(), unit, name, Modalities.normalize(name), birth, phone);
        return id;
    }

    private void guardian(Tenant t, UUID student, String name, String phone, boolean financial) {
        UUID account = UUID.randomUUID(), guardian = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, "g" + account + "@ficticia.example");
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, ?, ?, ?)", guardian, t.orgId(), account, name,
                "%011d".formatted(Math.abs(guardian.getMostSignificantBits() % 100000000000L)), phone);
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", t.orgId(), student, guardian, financial);
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    @Test
    @DisplayName("CA1: só os de hoje, por nome; 29/02 em 28/02 de ano não bissexto e só em 29/02 de ano bissexto")
    void today() throws Exception {
        Tenant t = tenant();
        var admin = who(t, Role.ADMINISTRATOR);
        student(t, t.unitId(), "Bruno Hoje", LocalDate.of(1990, 3, 15), null);
        student(t, t.unitId(), "Ana Hoje", LocalDate.of(2010, 3, 15), null);
        student(t, t.unitId(), "Ontem", LocalDate.of(1991, 3, 14), null);
        student(t, t.unitId(), "Amanha", LocalDate.of(1992, 3, 16), null);
        student(t, t.unitId(), "Bissexto", LocalDate.of(1992, 2, 29), null);
        today(TODAY);
        JsonNode b = ok(admin);
        assertThat(names(b)).containsExactly("Ana Hoje", "Bruno Hoje");
        assertThat(b.get(0).get("age").asInt()).isEqualTo(15);
        assertThat(b.get(0).get("ageRange").get("name").asText()).isNotBlank();
        assertThat(b.get(0).get("registeredUnit").get("id").asText()).isEqualTo(t.unitId().toString());
        today(LocalDate.of(2025, 2, 28));
        assertThat(names(ok(admin))).containsExactly("Bissexto");
        today(LocalDate.of(2024, 2, 28));
        assertThat(names(ok(admin))).isEmpty();
        today(LocalDate.of(2024, 2, 29));
        assertThat(names(ok(admin))).containsExactly("Bissexto");
    }

    @Test
    @DisplayName("CA2: Administrador só vê as unidades do alcance; sem unidades, lista vazia")
    void adminReach() throws Exception {
        Tenant t = tenant();
        UUID other = unit(t, "Zona Sul");
        today(TODAY);
        student(t, t.unitId(), "Da A", LocalDate.of(1990, 3, 15), null);
        student(t, other, "Da B", LocalDate.of(1990, 3, 15), null);
        assertThat(names(ok(who(t, Role.ADMINISTRATOR)))).containsExactly("Da A");
        var none = who(t, Role.ADMINISTRATOR);
        assertThat(names(ok(new AccountPrincipal(none.accountId(), none.sessionId(), none.activeLinkId(), none.organizationId(), none.role(), java.util.Set.of())))).isEmpty();
    }

    @Test
    @DisplayName("CA3: Coordenador, Aluno e Responsável 403; anônimo 401; outra organização nunca aparece")
    void profilesAndIsolation() throws Exception {
        Tenant a = tenant(), b = tenant();
        today(TODAY);
        student(a, a.unitId(), "Da A", LocalDate.of(1990, 3, 15), null);
        student(b, b.unitId(), "Da B", LocalDate.of(1990, 3, 15), null);
        for (Role role : new Role[] {Role.COORDINATOR, Role.STUDENT, Role.GUARDIAN}) assertThat(get(who(a, role)).getStatus()).as(role.name()).isEqualTo(403);
        assertThat(get(null).getStatus()).isEqualTo(401);
        assertThat(names(ok(who(a, Role.ADMINISTRATOR)))).containsExactly("Da A");
        assertThat(names(ok(who(b, Role.ADMINISTRATOR)))).containsExactly("Da B");
    }

    @Test
    @DisplayName("CA4: Professor recebe 200 e lista vazia até a matrícula; o corte passa por teacherReach")
    void teacher() throws Exception {
        Tenant t = tenant();
        today(TODAY);
        UUID mine = student(t, t.unitId(), "Da Turma", LocalDate.of(1990, 3, 15), "11987654321");
        student(t, t.unitId(), "De Fora", LocalDate.of(1990, 3, 15), "11987654322");
        var teacher = who(t, Role.TEACHER);
        assertThat(names(ok(teacher))).isEmpty();
        doReturn(true).when(records).teacherReach(any(), org.mockito.ArgumentMatchers.eq(mine));
        assertThat(names(ok(teacher))).containsExactly("Da Turma");
        assertThat(ok(teacher).get(0).get("contactPhone").asText()).isEqualTo("11987654321");
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,false,true,true}' where unit_id = ?", t.unitId());
        assertThat(ok(teacher).get(0).get("contactPhone").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA5, CA6: contato do aluno, do responsável financeiro, de outro responsável ou nulo; sem dado pessoal")
    void contact() throws Exception {
        Tenant t = tenant();
        var admin = who(t, Role.ADMINISTRATOR);
        today(TODAY);
        student(t, t.unitId(), "A Adulto", LocalDate.of(1990, 3, 15), "11987654321");
        UUID kid = student(t, t.unitId(), "B Menor", LocalDate.of(2015, 3, 15), null);
        guardian(t, kid, "Outra Mae", "11900000002", false);
        guardian(t, kid, "Mae Financeira", "11900000001", true);
        UUID kid2 = student(t, t.unitId(), "C Menor", LocalDate.of(2015, 3, 15), null);
        guardian(t, kid2, "Outro Resp", "11900000003", false);
        student(t, t.unitId(), "D Sem Nada", LocalDate.of(2015, 3, 15), null);
        var r = get(admin);
        JsonNode b = json.readTree(r.getContentAsString());
        assertThat(b.get(0).get("contactPhone").asText()).isEqualTo("11987654321");
        assertThat(b.get(0).get("contactIsGuardian").asBoolean()).isFalse();
        assertThat(b.get(1).get("contactPhone").asText()).isEqualTo("11900000001");
        assertThat(b.get(1).get("contactName").asText()).isEqualTo("Mae Financeira");
        assertThat(b.get(1).get("contactIsGuardian").asBoolean()).isTrue();
        assertThat(b.get(2).get("contactPhone").asText()).isEqualTo("11900000003");
        assertThat(b.get(3).get("contactPhone").isNull()).isTrue();
        assertThat(b.get(3).get("contactIsGuardian").asBoolean()).isFalse();
        assertThat(r.getContentAsString()).doesNotContain("cpf").doesNotContain("email").doesNotContain("birthDate").doesNotContain("Rua Secreta")
                .doesNotContain("segredo@");
    }

    @Test
    @DisplayName("CA10: a leitura não grava auditoria")
    void noAudit() throws Exception {
        Tenant t = tenant();
        today(TODAY);
        student(t, t.unitId(), "Da A", LocalDate.of(1990, 3, 15), null);
        long before = adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ?", Long.class, t.orgId());
        ok(who(t, Role.ADMINISTRATOR));
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ?", Long.class, t.orgId())).isEqualTo(before);
    }
}
