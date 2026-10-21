package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** R22 (CA31, CA32): a lista simples da turma ou da aula, só com nome, foto, faixa e bloqueio, sem depender das chaves do Professor. */
class StudentSimpleListIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from lesson");
        EnrollmentFixtures.clean(adminJdbc());
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal account(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@prof.example");
        return p;
    }

    private UUID student(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state, emergency_name, emergency_relationship, emergency_phone) values (?, ?, ?, ?, ?, ?, ?, 'aluno@ficticia.example', "
                + "'11987654321', '01001000', 'Rua Um', '1', 'Centro', 'São Paulo', 'SP', 'Contato Um', 'Mãe', '11911112222')",
                id, t.orgId(), t.unitId(), name, Modalities.normalize(name), LocalDate.now().minusYears(10), "%011d".formatted(Math.abs(id.getMostSignificantBits() % 100000000000L)));
        return id;
    }

    private UUID lesson(Tenant t, UUID klass, UUID teacher) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id) values (?, ?, ?, ?, current_date, '19:00', '20:00', ?)",
                id, t.orgId(), t.unitId(), klass, teacher);
        return id;
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students/simple" + query), who).getResponse();
    }

    private List<String> names(String query, AccountPrincipal who) throws Exception {
        MockHttpServletResponse r = get(query, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        List<String> out = new ArrayList<>();
        json.readTree(r.getContentAsString()).forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    @Test
    @DisplayName("CA31 (R22): o Professor vê nome, foto, faixa e blocked dos alunos ativos da turma dele, sem a chave da ficha; turma de outro, 404; Aluno e Responsável 403; sem sessão 401")
    void teacher() throws Exception {
        Tenant t = tenant(), other = tenant();
        adminJdbc().update("update unit_settings set teacher_permissions = '{false,false,false,false,false}'::boolean[] where unit_id = ?", t.unitId());
        AccountPrincipal me = account(t, Role.TEACHER, t.unitId());
        UUID myTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), me.accountId()), otherTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), null);
        UUID bjj = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0);
        UUID mine = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, myTeacher), theirs = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, otherTeacher);
        UUID principal = student(t, "Bia Principal"), second = student(t, "Ana Outro Horario"), ended = student(t, "Encerrado"), stranger = student(t, "De Outra Turma");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), principal, t.unitId(), "ACTIVE", bjj, mine, null, myTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), second, t.unitId(), "ACTIVE", bjj, theirs, mine, otherTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ended, t.unitId(), "ENDED", bjj, mine, null, myTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), stranger, t.unitId(), "ACTIVE", bjj, theirs, null, otherTeacher);

        assertThat(names("?classId=" + mine, me)).containsExactly("Ana Outro Horario", "Bia Principal");
        JsonNode body = json.readTree(get("?classId=" + mine, me).getContentAsString());
        assertThat(body.get(0).fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "photoUrl", "belt", "blocked");
        assertThat(body.get(0).get("blocked").asBoolean()).isFalse();
        assertThat(get("?classId=" + mine, me).getContentAsString()).doesNotContain("52998").doesNotContain("987654321").doesNotContain("Rua Um").doesNotContain("seals");
        // A chave "ver ficha" desligada não tira a lista simples, mas a ficha continua fechada.
        assertThat(call(MockMvcRequestBuilders.get("/api/v1/students/" + principal + "/record"), me).getResponse().getStatus()).isEqualTo(403);
        assertThat(get("?classId=" + theirs, me).getStatus()).isEqualTo(404);
        assertThat(get("?classId=" + UUID.randomUUID(), me).getStatus()).isEqualTo(404);
        // Aula: a da turma dele vale; a de outro Professor não; sem classe nem aula, 400.
        UUID myLesson = lesson(t, mine, myTeacher), theirLesson = lesson(t, theirs, otherTeacher);
        assertThat(names("?lessonId=" + myLesson, me)).containsExactly("Ana Outro Horario", "Bia Principal");
        assertThat(get("?lessonId=" + theirLesson, me).getStatus()).isEqualTo(404);
        assertThat(get("", me).getStatus()).isEqualTo(400);
        assertThat(get("?classId=" + mine + "&lessonId=" + myLesson, me).getStatus()).isEqualTo(400);

        for (Role role : new Role[] {Role.STUDENT, Role.GUARDIAN}) assertThat(get("?classId=" + mine, who(t, role)).getStatus()).as(role.name()).isEqualTo(403);
        assertThat(get("?classId=" + mine, null).getStatus()).isEqualTo(401);
        // Isolamento: outra organização não alcança a turma.
        AccountPrincipal foreign = account(other, Role.TEACHER, other.unitId());
        EnrollmentFixtures.teacher(adminJdbc(), other.orgId(), foreign.accountId());
        assertThat(get("?classId=" + mine, foreign).getStatus()).isEqualTo(404);
        assertThat(get("?lessonId=" + myLesson, foreign).getStatus()).isEqualTo(404);
        assertThat(get("?classId=" + mine, who(other, Role.COORDINATOR)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA32 (R22): a gestão chama a mesma rota com o alcance de unidade: Coordenador em qualquer unidade, Administrador só na dele (outra unidade 404)")
    void management() throws Exception {
        Tenant t = tenant();
        UUID unitB = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", unitB, t.orgId(), "u" + unitB.toString().substring(0, 12));
        UUID teacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), null);
        UUID bjj = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0);
        UUID inA = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, teacher), inB = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), unitB, bjj, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student(t, "Aluno A"), t.unitId(), "ACTIVE", bjj, inA, null, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student(t, "Aluno B"), unitB, "ACTIVE", bjj, inB, null, teacher);

        assertThat(names("?classId=" + inA, who(t, Role.COORDINATOR))).containsExactly("Aluno A");
        assertThat(names("?classId=" + inB, who(t, Role.COORDINATOR))).containsExactly("Aluno B");
        AccountPrincipal admin = account(t, Role.ADMINISTRATOR, t.unitId());
        assertThat(names("?classId=" + inA, admin)).containsExactly("Aluno A");
        assertThat(get("?classId=" + inB, admin).getStatus()).isEqualTo(404);
        assertThat(names("?lessonId=" + lesson(t, inA, teacher), admin)).containsExactly("Aluno A");
    }
}
