package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * RN-ALU-07 (R14, R15, CA21, CA22): o alcance do Administrador sobre o aluno é ter modalidade numa unidade dele (ativa ou encerrada); sem
 * nenhuma modalidade vale a unidade de cadastro; o Coordenador alcança todos. Lista, filtro de unidade, ficha, matrícula e aniversariantes.
 */
class StudentReachIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        for (String table : new String[] {"enrollment_modality_class", "enrollment_modality", "enrollment", "student", "plan", "training_class_schedule",
                "training_class", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
    }

    private UUID unit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(),
                "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID student(Tenant t, UUID registered, String name, String cpf) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state) values (?, ?, ?, ?, ?, date '1990-05-20', ?, 'aluno@ficticia.example', '11912345678', '01001000', 'Rua', '1', 'Centro', "
                + "'Cidade', 'SP')", id, t.orgId(), registered, name, Modalities.normalize(name), cpf);
        return id;
    }

    /** Matricula o aluno com uma modalidade (ativa ou encerrada) na unidade, com turma e plano próprios. */
    private UUID enroll(Tenant t, UUID student, UUID unit, String status, int kind) {
        UUID modality = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(kind);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), modality);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        UUID teacher = UUID.randomUUID(), klass = UUID.randomUUID(), plan = UUID.randomUUID(), row = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                klass, t.orgId(), unit, "T" + klass, "t" + klass, modality, teacher);
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 15000, 'ACTIVE')",
                plan, t.orgId(), unit, modality, "P" + plan, "p" + plan);
        UUID enrollment = adminJdbc().queryForList("select id from enrollment where student_id = ?", UUID.class, student).stream().findFirst().orElse(null);
        if (enrollment == null) {
            enrollment = UUID.randomUUID();
            adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        }
        // Uma modalidade ativa por tipo e aluno (D3): a segunda linha do mesmo aluno é encerrada.
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at, ended_at) values (?, ?, ?, ?, ?, ?, ?, ?, 5, ?, now(), case when ? = 'ENDED' then now() end)",
                row, t.orgId(), enrollment, student, unit, modality, klass, plan, status, status);
        return row;
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private MockHttpServletResponse get(String path, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students" + path), who).getResponse();
    }

    private List<String> names(String query, AccountPrincipal who) throws Exception {
        MockHttpServletResponse r = get(query, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return java.util.stream.StreamSupport.stream(json.readTree(r.getContentAsString()).get("items").spliterator(), false).map(i -> i.get("name").asText()).toList();
    }

    @Test
    @DisplayName("CA21 (RN-ALU-07): lista, filtro de unidade e ficha seguem a modalidade na unidade; sem modalidade vale a unidade de cadastro; Coordenador vê todos")
    void reach() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID a = t.unitId(), b = unit(t);
        UUID onlyB = student(t, a, "Só na B", "52998224725"), both = student(t, a, "Nas duas", "11144477735"), none = student(t, a, "Sem modalidade A", "39053344705"),
                noneB = student(t, b, "Sem modalidade B", "16899535009"), formerB = student(t, a, "Ex na B", "11222333000");
        enroll(t, onlyB, b, "ACTIVE", 0);
        enroll(t, both, a, "ACTIVE", 0);
        enroll(t, both, b, "ENDED", 0);
        enroll(t, formerB, b, "ENDED", 0);
        student(other, other.unitId(), "De outra organização", "98765432100");
        AccountPrincipal adminA = admin(t, a), adminB = admin(t, b), adminAB = admin(t, a, b), coordinator = who(t, Role.COORDINATOR);

        assertThat(names("", adminA)).containsExactly("Nas duas", "Sem modalidade A");
        assertThat(names("", adminB)).containsExactly("Ex na B", "Nas duas", "Sem modalidade B", "Só na B");
        assertThat(names("", adminAB)).hasSize(5);
        assertThat(names("", coordinator)).hasSize(5).doesNotContain("De outra organização");
        // Filtro de unidade: só quem tem modalidade nela (ou, sem nenhuma, a unidade de cadastro).
        assertThat(names("?unitId=" + b, coordinator)).containsExactly("Ex na B", "Nas duas", "Sem modalidade B", "Só na B");
        assertThat(names("?unitId=" + a, coordinator)).containsExactly("Nas duas", "Sem modalidade A");
        assertThat(names("?unitId=" + b, adminA)).isEmpty();

        // Ficha: fora do alcance, o mesmo 404 de quem não existe.
        assertThat(get("/" + onlyB, adminA).getStatus()).isEqualTo(404);
        assertThat(get("/" + onlyB + "/record", adminA).getStatus()).isEqualTo(404);
        assertThat(get("/" + onlyB + "/enrollment", adminA).getStatus()).isEqualTo(404);
        assertThat(get("/" + onlyB, adminB).getStatus()).isEqualTo(200);
        assertThat(get("/" + formerB, adminB).getStatus()).isEqualTo(200);
        assertThat(get("/" + none, adminA).getStatus()).isEqualTo(200);
        assertThat(get("/" + none, adminB).getStatus()).isEqualTo(404);
        assertThat(get("/" + noneB, adminA).getStatus()).isEqualTo(404);
        assertThat(get("/" + onlyB, coordinator).getStatus()).isEqualTo(200);
        assertThat(get("/" + onlyB, who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA22 (RN-ALU-07): aluno com modalidade na A e na B: o Administrador da A vê as duas, só a da A é editável, a troca na B é 403 e os dados pessoais ele edita")
    void viewsBothEditsOwn() throws Exception {
        Tenant t = tenant();
        UUID a = t.unitId(), b = unit(t);
        UUID s = student(t, a, "Nas duas", "52998224725");
        UUID rowA = enroll(t, s, a, "ACTIVE", 0);
        // Outro tipo de modalidade na B, ativa (D3: um tipo ativo por aluno).
        UUID rowB = enroll(t, s, b, "ACTIVE", 1);
        AccountPrincipal adminA = admin(t, a);

        JsonNode enrollment = json.readTree(get("/" + s + "/enrollment", adminA).getContentAsString());
        assertThat(enrollment.get("modalities")).hasSize(2);
        for (JsonNode m : enrollment.get("modalities")) assertThat(m.get("canEdit").asBoolean()).isEqualTo(m.get("unit").get("id").asText().equals(a.toString()));
        var denied = call(MockMvcRequestBuilders.put("/api/v1/students/" + s + "/modalities/" + rowB + "/plan").contentType("application/json")
                .content("{\"planId\":\"" + adminJdbc().queryForObject("select plan_id from enrollment_modality where id = ?", UUID.class, rowB) + "\"}"), adminA).getResponse();
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(rowA).isNotEqualTo(rowB);

        var edit = call(MockMvcRequestBuilders.put("/api/v1/students/" + s).contentType("application/json").content(
                "{\"name\":\"Nas duas\",\"birthDate\":\"1990-05-20\",\"postalCode\":\"01001-000\",\"street\":\"Rua Nova\",\"number\":\"1\",\"district\":\"Centro\","
                        + "\"city\":\"Cidade\",\"state\":\"SP\",\"cpf\":\"52998224725\",\"email\":\"aluno@ficticia.example\",\"phone\":\"(11) 91234-5678\"}"),
                adminA).getResponse();
        assertThat(edit.getStatus()).as(edit.getContentAsString()).isEqualTo(200);
    }
}
