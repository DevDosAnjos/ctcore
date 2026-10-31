package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-ALU-03: ficha (record) e histórico do aluno: campos, selos, alcance, perfis, chave do Professor, paginação e isolamento. */
class StudentRecordIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    private long cpfSeq = 100;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from enrollment_modality_class");
        adminJdbc().update("delete from enrollment_modality");
        adminJdbc().update("delete from enrollment");
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
    }

    private MockHttpServletResponse get(String path, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students" + path), who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private UUID unit(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, ?, ?)", id, t.orgId(), name, "u" + id.toString().substring(0, 12));
        return id;
    }

    private UUID student(Tenant t, UUID unit, String name, int age) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state, emergency_name, emergency_relationship, emergency_phone) values (?, ?, ?, ?, ?, ?, ?, 'aluno@ficticia.example', "
                + "'11987654321', '01001000', 'Rua Um', '1', 'Centro', 'São Paulo', 'SP', 'Contato Um', 'Mãe', '11911112222')",
                id, t.orgId(), unit, name, Modalities.normalize(name), LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusYears(age), "%011d".formatted(++cpfSeq));
        return id;
    }

    private void guardian(Tenant t, UUID student, String name, boolean financial, boolean active) {
        UUID account = UUID.randomUUID(), guardian = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, "g" + account + "@ficticia.example");
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, ?, ?, '11933334444')",
                guardian, t.orgId(), account, name, "%011d".formatted(++cpfSeq));
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", t.orgId(), student, guardian, financial);
        if (active) {
            adminJdbc().update("insert into account_link (account_id, organization_id, role, status) values (?, ?, 'GUARDIAN', 'ACTIVE')", account, t.orgId());
        }
    }

    private void audit(Tenant t, UUID student, String action, OffsetDateTime at, String reason) {
        adminJdbc().update("insert into audit_log (organization_id, actor_type, actor_profile, action, entity_type, entity_id, before_data, after_data, reason, occurred_at) "
                + "values (?, 'ACCOUNT', 'COORDINATOR', ?, 'student', ?, '{\"cpf\":\"segredo\"}'::jsonb, '{\"cpf\":\"segredo2\"}'::jsonb, ?, ?)",
                t.orgId(), action, student.toString(), reason, at);
    }

    @Test
    @DisplayName("CA1: ficha traz todos os campos, selos iguais aos da lista, responsáveis; outra organização e id inexistente dão 404")
    void record() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID id = student(t, t.unitId(), "Menor Ficha", 10);
        guardian(t, id, "Resp Financeiro", true, true);
        guardian(t, id, "Resp Pendente", false, false);
        JsonNode r = ok(get("/" + id + "/record", c));
        assertThat(r.get("name").asText()).isEqualTo("Menor Ficha");
        assertThat(r.get("age").asInt()).isEqualTo(10);
        assertThat(r.get("minor").asBoolean()).isTrue();
        assertThat(r.get("birthDate").asText()).isEqualTo(LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusYears(10).toString());
        assertThat(r.get("registeredUnit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(r.get("ageRange").get("name").asText()).isEqualTo("Infantil");
        assertThat(r.get("contact").get("phone").asText()).isEqualTo("11987654321");
        assertThat(r.get("contact").get("email").asText()).isEqualTo("aluno@ficticia.example");
        assertThat(r.get("cpf").asText()).hasSize(11);
        assertThat(r.get("address").get("street").asText()).isEqualTo("Rua Um");
        assertThat(r.get("emergency").get("name").asText()).isEqualTo("Contato Um");
        assertThat(r.get("guardians")).hasSize(2);
        assertThat(r.get("guardians").get(0).get("financial").asBoolean()).isTrue();
        assertThat(r.get("guardians").get(0).get("access").asText()).isEqualTo("ACTIVE");
        assertThat(r.get("guardians").get(1).get("access").asText()).isEqualTo("PENDING");
        JsonNode listed = ok(get("?q=Menor", c)).get("items").get(0);
        assertThat(r.get("seals")).isEqualTo(listed.get("seals"));
        var other = tenant();
        assertThat(get("/" + id + "/record", who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(get("/" + UUID.randomUUID() + "/record", c).getStatus()).isEqualTo(404);
        for (String key : new String[] {"password", "token", "card"}) assertThat(r.toString()).doesNotContain(key);
    }

    @Test
    @DisplayName("RN-ALU-07: CA2: Administrador abre o aluno da sua unidade e recebe 404 do cadastrado em outra unidade da organização")
    void adminReach() throws Exception {
        Tenant t = tenant();
        UUID second = unit(t, "Zona Sul");
        UUID mine = student(t, t.unitId(), "Minha Ficha", 30), theirs = student(t, second, "Outra Ficha", 30);
        var admin = who(t, Role.ADMINISTRATOR);
        assertThat(get("/" + mine + "/record", admin).getStatus()).isEqualTo(200);
        assertThat(get("/" + theirs + "/record", admin).getStatus()).isEqualTo(404);
        assertThat(get("/" + theirs + "/history", admin).getStatus()).isEqualTo(404);
        assertThat(get("/" + mine + "/history", admin).getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("CA3: Aluno, Responsável e anônimo recebem 403, 403 e 401 na ficha e no histórico")
    void profiles() throws Exception {
        Tenant t = tenant();
        UUID id = student(t, t.unitId(), "Alguem Ficha", 30);
        for (String path : new String[] {"/record", "/history"}) {
            assertThat(get("/" + id + path, who(t, Role.STUDENT)).getStatus()).as(path).isEqualTo(403);
            assertThat(get("/" + id + path, who(t, Role.GUARDIAN)).getStatus()).as(path).isEqualTo(403);
            assertThat(get("/" + id + path, null).getStatus()).as(path).isEqualTo(401);
        }
        assertThat(get("/" + id + "/history", who(t, Role.TEACHER)).getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("CA6: Professor sem a chave recebe 403 (até de aluno inexistente); com a chave, 404 até haver matrícula")
    void teacherKey() throws Exception {
        Tenant t = tenant();
        UUID id = student(t, t.unitId(), "Alguem Ficha", 30);
        var teacher = who(t, Role.TEACHER);
        assertThat(get("/" + id + "/record", teacher).getStatus()).isEqualTo(403);
        assertThat(get("/" + UUID.randomUUID() + "/record", teacher).getStatus()).isEqualTo(403);
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,true,true,true}' where unit_id = ?", t.unitId());
        assertThat(get("/" + id + "/record", teacher).getStatus()).isEqualTo(404);
        assertThat(get("/" + UUID.randomUUID() + "/record", teacher).getStatus()).isEqualTo(404);
        var other = tenant();
        UUID foreign = student(other, other.unitId(), "De Fora", 30);
        assertThat(get("/" + foreign + "/record", teacher).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("443 CA6: o Professor com a chave ligada abre o aluno ativo de turma dele (200, sem os campos omitidos) e não o de turma alheia; Coordenador e Administrador abrem")
    void teacherClass() throws Exception {
        Tenant t = tenant();
        var teacher = who(t, Role.TEACHER);
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", teacher.accountId(), teacher.accountId() + "@prof.example");
        UUID teacherId = UUID.randomUUID(), mine = UUID.randomUUID(), theirs = UUID.randomUUID(), plan = UUID.randomUUID();
        UUID modality = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(0);
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, 'Professor', 'professor', ?)", teacherId, t.orgId(), teacher.accountId());
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Outro', 'outro')", theirs, t.orgId());
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents) values (?, ?, ?, ?, 'Plano', 'plano', 15000)", plan, t.orgId(), t.unitId(), modality);
        UUID myClass = UUID.randomUUID(), otherClass = UUID.randomUUID();
        for (Object[] c : new Object[][] {{myClass, "Minha", teacherId}, {otherClass, "Alheia", theirs}}) {
            adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                    c[0], t.orgId(), t.unitId(), c[1], Modalities.normalize((String) c[1]), modality, c[2]);
        }
        UUID inClass = student(t, t.unitId(), "Na Turma", 30), outClass = student(t, t.unitId(), "Fora Turma", 30);
        enroll(t, inClass, myClass, modality, plan);
        enroll(t, outClass, otherClass, modality, plan);
        adminJdbc().update("update unit_settings set teacher_permissions = '{true,true,true,true,true}' where unit_id = ?", t.unitId());
        JsonNode view = ok(get("/" + inClass + "/record", teacher));
        for (String omitted : new String[] {"cpf", "address", "seals", "birthDate"}) assertThat(view.has(omitted)).as(omitted).isFalse();
        assertThat(view.get("name").asText()).isEqualTo("Na Turma");
        assertThat(get("/" + outClass + "/record", teacher).getStatus()).isEqualTo(404);
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,false,false,false,false}' where unit_id = ?", t.unitId());
        assertThat(get("/" + inClass + "/record", teacher).getStatus()).isEqualTo(403);
        assertThat(get("/" + inClass + "/record", who(t, Role.COORDINATOR)).getStatus()).isEqualTo(200);
        assertThat(get("/" + inClass + "/record", who(t, Role.ADMINISTRATOR)).getStatus()).isEqualTo(200);
        var other = tenant();
        assertThat(get("/" + inClass + "/record", who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }

    private void enroll(Tenant t, UUID student, UUID klass, UUID modality, UUID plan) {
        UUID enrollment = UUID.randomUUID();
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, status, started_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, 5, 'ACTIVE', now())", UUID.randomUUID(), t.orgId(), enrollment, student, t.unitId(), modality, klass, plan);
    }

    @Test
    @DisplayName("CA8: histórico da mais recente para a mais antiga, com ator, perfil e motivo, sem before/after, paginado e isolado")
    void history() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID id = student(t, t.unitId(), "Com Historico", 30), other = student(t, t.unitId(), "Sem Historico", 30);
        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        audit(t, id, "student.created", base, null);
        audit(t, id, "student.guardian_linked", base.plusHours(1), "novo responsável");
        audit(t, id, "student.guardian_unlinked", base.plusHours(2), "saiu");
        audit(t, other, "student.created", base, null);
        var third = tenant();
        audit(third, id, "student.created", base, null); // mesmo id em outra organização: nunca aparece
        JsonNode h = ok(get("/" + id + "/history", c));
        assertThat(h.get("total").asLong()).isEqualTo(3);
        assertThat(h.get("items").get(0).get("action").asText()).isEqualTo("student.guardian_unlinked");
        assertThat(h.get("items").get(0).get("reason").asText()).isEqualTo("saiu");
        assertThat(h.get("items").get(0).get("actorProfile").asText()).isEqualTo("COORDINATOR");
        assertThat(h.get("items").get(2).get("action").asText()).isEqualTo("student.created");
        assertThat(h.toString()).doesNotContain("before").doesNotContain("after").doesNotContain("segredo");
        assertThat(ok(get("/" + id + "/history?size=25&page=2", c)).get("items")).isEmpty();
        assertThat(get("/" + id + "/history?size=7", c).getStatus()).isEqualTo(400);
        assertThat(get("/" + id + "/history", who(third, Role.COORDINATOR)).getStatus()).isEqualTo(404);
        assertThat(get("/" + UUID.randomUUID() + "/history", c).getStatus()).isEqualTo(404);
    }
}
