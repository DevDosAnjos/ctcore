package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * R12 e R13 (CA19, CA20): as contagens de alunos de modalidade, unidade e turma leem a matrícula (RN-MOD-06, RN-ORG-11, RN-TUR-02 a 04),
 * contam alunos distintos com modalidade ativa e ficam por organização.
 */
class EnrollmentCountsIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        for (String table : new String[] {"enrollment_modality_class", "enrollment_modality", "enrollment", "student", "plan", "training_class_schedule",
                "training_class", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
    }

    private UUID modality(Tenant t) {
        UUID id = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(0);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), id);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), t.unitId(), id);
        return id;
    }

    private UUID klass(Tenant t, UUID modality, String name) {
        UUID teacher = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key) values (?, ?, 'Professor', 'professor')", teacher, t.orgId());
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                id, t.orgId(), t.unitId(), name, Modalities.normalize(name), modality, teacher);
        return id;
    }

    /** Aluno matriculado: modalidade ativa com a turma principal e, se houver, outro horário. Devolve a linha da modalidade. */
    private UUID enrolled(Tenant t, UUID modality, UUID plan, String name, UUID primary, UUID other) {
        UUID student = UUID.randomUUID(), enrollment = UUID.randomUUID(), row = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) values (?, ?, ?, ?, ?, date '1990-01-01', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')",
                student, t.orgId(), t.unitId(), name, Modalities.normalize(name));
        adminJdbc().update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, t.orgId(), student);
        adminJdbc().update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at) values (?, ?, ?, ?, ?, ?, ?, ?, 5, 'ACTIVE', now())", row, t.orgId(), enrollment, student, t.unitId(), modality, primary, plan);
        if (other != null) adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", t.orgId(), row, other);
        return row;
    }

    private UUID plan(Tenant t, UUID modality) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, 'Plano', 'plano', 15000, 'ACTIVE')",
                id, t.orgId(), t.unitId(), modality);
        return id;
    }

    private JsonNode get(String path, AccountPrincipal who) throws Exception {
        return json.readTree(call(MockMvcRequestBuilders.get(path), who).getResponse().getContentAsString());
    }

    @Test
    @DisplayName("CA19, CA20 (RN-MOD-06, RN-ORG-11, RN-TUR-02, RN-TUR-03, RN-TUR-04): contagens reais de modalidade, unidade e turma, só de modalidade ativa e da própria organização")
    void counts() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID bjj = modality(t);
        UUID x = klass(t, bjj, "Turma X"), y = klass(t, bjj, "Turma Y"), z = klass(t, bjj, "Turma Z");
        UUID plan = plan(t, bjj);
        UUID rowA = enrolled(t, bjj, plan, "Aluno A", x, y), rowB = enrolled(t, bjj, plan, "Aluno B", y, null);
        UUID otherBjj = modality(other);
        UUID ox = klass(other, otherBjj, "Turma O");
        enrolled(other, otherBjj, plan(other, otherBjj), "Aluno O", ox, null);
        AccountPrincipal coordinator = who(t, Role.COORDINATOR);

        JsonNode panelY = get("/api/v1/classes/" + y, coordinator);
        assertThat(panelY.get("activeStudents").asInt()).isEqualTo(2);
        assertThat(panelY.get("students").findValuesAsText("name")).containsExactly("Aluno A", "Aluno B");
        assertThat(panelY.get("students").findValues("primary").stream().map(JsonNode::asBoolean)).containsExactly(false, true);
        assertThat(panelY.get("deactivation").get("blockingStudents").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/classes/" + x, coordinator).get("activeStudents").asInt()).isEqualTo(1);
        assertThat(get("/api/v1/classes?q=turma", coordinator).get("items").findValues("activeStudents").stream().map(JsonNode::asInt)).containsExactlyInAnyOrder(1, 2, 0);
        // Capacidade excedida só avisa (RN-TUR-02).
        adminJdbc().update("update training_class set capacity = 1 where id = ?", y);
        assertThat(get("/api/v1/classes/" + y, coordinator).get("overCapacity").asBoolean()).isTrue();

        assertThat(call(MockMvcRequestBuilders.post("/api/v1/classes/" + x + "/deactivate"), coordinator).getResponse().getStatus()).isEqualTo(409);
        assertThat(call(MockMvcRequestBuilders.post("/api/v1/classes/" + z + "/deactivate"), coordinator).getResponse().getStatus()).isEqualTo(200);

        assertThat(get("/api/v1/units/" + t.unitId() + "/deactivation-check", coordinator).get("activeStudents").asInt()).isEqualTo(2);
        assertThat(get("/api/v1/units", coordinator).findValues("activeStudents").stream().map(JsonNode::asInt)).containsExactly(2);
        var refused = call(MockMvcRequestBuilders.put("/api/v1/modalities/" + bjj + "/activation").contentType("application/json").content("{\"active\":false}"), coordinator).getResponse();
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("MODALITY_IN_USE").contains("{\"field\":\"activeStudents\",\"message\":\"2\"}");

        // Modalidade encerrada não conta; sem turma ativa e sem aluno ativo, a modalidade desativa.
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where id in (?, ?)", rowA, rowB);
        adminJdbc().update("update training_class set status = 'INACTIVE' where organization_id = ?", t.orgId());
        assertThat(get("/api/v1/classes/" + y, coordinator).get("activeStudents").asInt()).isZero();
        assertThat(get("/api/v1/units/" + t.unitId() + "/deactivation-check", coordinator).get("activeStudents").asInt()).isZero();
        assertThat(get("/api/v1/units", coordinator).findValues("activeStudents").stream().map(JsonNode::asInt)).containsExactly(0);
        assertThat(call(MockMvcRequestBuilders.put("/api/v1/modalities/" + bjj + "/activation").contentType("application/json").content("{\"active\":false}"), coordinator)
                .getResponse().getStatus()).isEqualTo(200);
    }
}
