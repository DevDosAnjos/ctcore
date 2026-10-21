package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.organization.OrganizationBase;
import br.com.ctcore.trainingclass.TrainingClasses.AgeRangeRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RF-MAT-02 (R1, R2, R5): avisos de faixa etária e capacidade da turma, só leitura, com banco real. */
class ClassWarningsIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        EnrollmentFixtures.clean(adminJdbc());
        adminJdbc().update("delete from age_range where name like 'Faixa %'");
    }

    private AccountPrincipal as(Tenant t, Role role, UUID... units) {
        return new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
    }

    private UUID student(Tenant t, LocalDate birth) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state) "
                + "values (?, ?, ?, 'Aluno Fictício', 'aluno ficticio', ?, '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", id, t.orgId(), t.unitId(), birth);
        return id;
    }

    private UUID range(Tenant t, int min, Integer max) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into age_range (id, organization_id, name, name_key, min_age, max_age) values (?, ?, ?, ?, ?, ?)", id, t.orgId(), "Faixa " + id,
                "faixa " + id, min, max);
        return id;
    }

    private UUID klass(Tenant t, UUID range, Integer capacity) {
        UUID modality = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0);
        UUID id = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), null));
        adminJdbc().update("update training_class set age_range_id = ?, capacity = ? where id = ?", range, capacity, id);
        return id;
    }

    private void enroll(Tenant t, UUID student, UUID klass) {
        UUID modality = adminJdbc().queryForObject("select modality_id from training_class where id = ?", UUID.class, klass);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", modality, klass, null, null);
    }

    private MockHttpServletResponse get(UUID student, String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students/" + student + "/enrollment/class-warnings" + query), who).getResponse();
    }

    private JsonNode ok(UUID student, UUID klass, AccountPrincipal who) throws Exception {
        var r = get(student, "?classId=" + klass, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private static LocalDate yearsAgo(int years, int extraDays) {
        return LocalDate.now().minusYears(years).minusDays(extraDays);
    }

    @Test
    @DisplayName("CA1, CA2: fora da faixa só abaixo do mínimo ou acima do máximo; sem faixa e sem máximo não avisam; aniversário hoje conta a idade nova")
    void age() throws Exception {
        var t = tenant();
        var c = as(t, Role.COORDINATOR, t.unitId());
        UUID juvenile = klass(t, range(t, 13, 17), null);
        var ten = ok(student(t, yearsAgo(10, 30)), juvenile, c);
        assertThat(ten.get("ageOutOfRange").asBoolean()).isTrue();
        assertThat(ten.get("studentAge").asInt()).isEqualTo(10);
        assertThat(ten.at("/ageRange/minAge").asInt()).isEqualTo(13);
        assertThat(ok(student(t, yearsAgo(15, 30)), juvenile, c).get("ageOutOfRange").asBoolean()).isFalse();
        assertThat(ok(student(t, yearsAgo(18, 30)), juvenile, c).get("ageOutOfRange").asBoolean()).isTrue();
        var none = ok(student(t, yearsAgo(10, 30)), klass(t, null, null), c);
        assertThat(none.get("ageOutOfRange").asBoolean()).isFalse();
        assertThat(none.get("ageRange").isNull()).isTrue();
        assertThat(ok(student(t, yearsAgo(40, 30)), klass(t, range(t, 18, null), null), c).get("ageOutOfRange").asBoolean()).isFalse();
        var birthday = ok(student(t, LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).minusYears(13)), juvenile, c); // fuso padrão da unidade
        assertThat(birthday.get("studentAge").asInt()).isEqualTo(13);
    }

    @Test
    @DisplayName("CA3, CA4: acima da capacidade quando esta matrícula passaria dela; aluno já ativo não conta de novo; sem capacidade não avisa")
    void capacity() throws Exception {
        var t = tenant();
        var c = as(t, Role.COORDINATOR, t.unitId());
        UUID full = klass(t, null, 2);
        UUID inside = student(t, yearsAgo(30, 5));
        enroll(t, student(t, yearsAgo(30, 5)), full);
        var one = ok(student(t, yearsAgo(30, 5)), full, c);
        assertThat(one.get("activeStudents").asInt()).isEqualTo(1);
        assertThat(one.get("overCapacity").asBoolean()).isFalse();
        enroll(t, inside, full);
        var two = ok(student(t, yearsAgo(30, 5)), full, c);
        assertThat(two.get("capacity").asInt()).isEqualTo(2);
        assertThat(two.get("overCapacity").asBoolean()).isTrue();
        assertThat(ok(inside, full, c).get("overCapacity").asBoolean()).isFalse();
        UUID free = klass(t, null, null);
        enroll(t, student(t, yearsAgo(30, 5)), free);
        var open = ok(student(t, yearsAgo(30, 5)), free, c);
        assertThat(open.get("capacity").isNull()).isTrue();
        assertThat(open.get("overCapacity").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA7: perfis 403, Administrador fora do alcance 403, outra organização 404, sem sessão 401, classId ausente ou inválido 400")
    void access() throws Exception {
        var t = tenant();
        var other = tenant();
        UUID klass = klass(t, null, null);
        UUID s = student(t, yearsAgo(30, 5));
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            assertThat(get(s, "?classId=" + klass, as(t, role, t.unitId())).getStatus()).isEqualTo(403);
        }
        UUID u2 = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'Outra', ?)", u2, t.orgId(), "u" + u2.toString().substring(0, 12));
        UUID far = klass(t, null, null);
        adminJdbc().update("update training_class set unit_id = ? where id = ?", u2, far);
        assertThat(get(s, "?classId=" + far, as(t, Role.ADMINISTRATOR, t.unitId())).getStatus()).isEqualTo(403);
        var c = as(t, Role.COORDINATOR, t.unitId());
        assertThat(get(student(other, yearsAgo(30, 5)), "?classId=" + klass, c).getStatus()).isEqualTo(404);
        assertThat(get(s, "?classId=" + klass(other, null, null), c).getStatus()).isEqualTo(404);
        assertThat(get(s, "?classId=" + klass, null).getStatus()).isEqualTo(401);
        assertThat(get(s, "", c).getStatus()).isEqualTo(400);
        assertThat(get(s, "?classId=x", c).getStatus()).isEqualTo(400);
        assertThat(ok(s, klass, as(t, Role.ADMINISTRATOR, t.unitId())).get("activeStudents").asInt()).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from enrollment_modality", Integer.class)).isZero();
    }

    @Test
    @DisplayName("regras puras de faixa e capacidade")
    void rules() {
        var range = new AgeRangeRef(UUID.randomUUID(), "Juvenil", 13, 17);
        assertThat(ClassWarnings.outOfRange(12, range)).isTrue();
        assertThat(ClassWarnings.outOfRange(13, range)).isFalse();
        assertThat(ClassWarnings.outOfRange(17, range)).isFalse();
        assertThat(ClassWarnings.outOfRange(18, range)).isTrue();
        assertThat(ClassWarnings.outOfRange(90, new AgeRangeRef(UUID.randomUUID(), "Adulto", 18, null))).isFalse();
        assertThat(ClassWarnings.outOfRange(5, null)).isFalse();
        assertThat(ClassWarnings.over(2, false, 2)).isTrue();
        assertThat(ClassWarnings.over(2, true, 2)).isFalse();
        assertThat(ClassWarnings.over(1, false, 2)).isFalse();
        assertThat(ClassWarnings.over(9, false, null)).isFalse();
    }
}
