package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** RF-ALU-01 (R18, R19, R20): a lista com modalidades e situação, os filtros por modalidade, turma e nível, e as contagens dos filtros rápidos. */
class StudentListEnrollmentIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    private int cpf;

    @AfterEach
    void clean() {
        EnrollmentFixtures.clean(adminJdbc());
    }

    private UUID student(Tenant t, UUID unit, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, district, city, state) values (?, ?, ?, ?, ?, date '1990-05-20', ?, "
                + "'aluno@ficticia.example', '11912345678', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')", id, t.orgId(), unit, name, Modalities.normalize(name), "%011d".formatted(++cpf));
        return id;
    }

    private UUID unit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        return id;
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.ADMINISTRATOR, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private MockHttpServletResponse get(String query, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students" + query), who).getResponse();
    }

    private JsonNode body(String query, AccountPrincipal who) throws Exception {
        MockHttpServletResponse r = get(query, who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    private List<String> names(JsonNode b) {
        List<String> out = new ArrayList<>();
        b.get("items").forEach(i -> out.add(i.get("name").asText()));
        return out;
    }

    private JsonNode item(JsonNode b, String name) {
        for (JsonNode i : b.get("items")) if (i.get("name").asText().equals(name)) return i;
        throw new AssertionError(name);
    }

    @Test
    @DisplayName("CA27 (R18): a linha traz as modalidades ativas com unidade, turma principal e nível nulo, e a situação; sem matrícula vazia; ex-aluno sem modalidade ativa")
    void rows() throws Exception {
        Tenant t = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID two = student(t, t.unitId(), "Duas"), none = student(t, t.unitId(), "Nenhuma"), former = student(t, t.unitId(), "Ex");
        var first = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), two, t.unitId(), "ACTIVE", 0, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), two, t.unitId(), "ACTIVE", 1, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), former, t.unitId(), "ENDED", 0, null);

        JsonNode b = body("", c);
        JsonNode d = item(b, "Duas");
        assertThat(d.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(d.get("modalities")).hasSize(2);
        JsonNode m = d.get("modalities").get(0);
        assertThat(m.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(m.get("modality").get("name").asText()).isNotBlank();
        assertThat(m.get("level").isNull()).isTrue();
        List<String> classes = new ArrayList<>();
        d.get("modalities").forEach(x -> classes.add(x.get("primaryClass").get("id").asText()));
        assertThat(classes).contains(first.klass().toString());
        assertThat(item(b, "Nenhuma").get("status").asText()).isEqualTo("NONE");
        assertThat(item(b, "Nenhuma").get("modalities")).isEmpty();
        assertThat(item(b, "Ex").get("status").asText()).isEqualTo("FORMER");
        assertThat(item(b, "Ex").get("modalities")).isEmpty();
        assertThat(none).isNotNull();
    }

    @Test
    @DisplayName("CA28 (R19): filtros por modalidade, turma (principal e outro horário) e nível, sozinhos e combinados; turma ou unidade de outra organização 404")
    void filters() throws Exception {
        Tenant t = tenant(), other = tenant();
        var c = who(t, Role.COORDINATOR);
        UUID b2 = unit(t);
        UUID ana = student(t, t.unitId(), "Ana"), bia = student(t, t.unitId(), "Bia"), caio = student(t, b2, "Caio"), ended = student(t, t.unitId(), "Dani");
        UUID m0 = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0), m1 = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 1);
        UUID teacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), null);
        UUID k0 = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), m0, teacher), k1 = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), m1, teacher);
        UUID kb = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), b2, m1, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ana, t.unitId(), "ACTIVE", m0, k0, null, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), bia, t.unitId(), "ACTIVE", m0, k0, null, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), bia, t.unitId(), "ACTIVE", m1, k1, null, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), caio, b2, "ACTIVE", m1, kb, null, teacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ended, t.unitId(), "ENDED", m0, k0, null, teacher);
        // Outro horário: Ana também treina na turma k0b da mesma modalidade, como outro horário da linha dela.
        UUID k0b = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), m0, teacher);
        adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) select organization_id, id, ? from enrollment_modality "
                + "where student_id = ? and modality_id = ?", k0b, ana, m0);

        assertThat(names(body("?modalityId=" + m0, c))).containsExactly("Ana", "Bia");
        assertThat(names(body("?modalityId=" + m1, c))).containsExactly("Bia", "Caio");
        assertThat(names(body("?classId=" + k0, c))).containsExactly("Ana", "Bia");
        assertThat(names(body("?classId=" + k0b, c))).containsExactly("Ana");
        assertThat(names(body("?classId=" + k1, c))).containsExactly("Bia");
        assertThat(names(body("?modalityId=" + m1 + "&unitId=" + b2, c))).containsExactly("Caio");
        assertThat(names(body("?modalityId=" + m0 + "&classId=" + k1, c))).isEmpty();
        assertThat(names(body("?modalityId=" + m0 + "&q=an", c))).containsExactly("Ana");
        assertThat(body("?modalityId=" + m1, c).get("total").asLong()).isEqualTo(2);
        // D13: o nível é aceito e, até a graduação, ninguém o tem.
        assertThat(names(body("?levelId=" + UUID.randomUUID(), c))).isEmpty();

        UUID foreignClass = EnrollmentFixtures.klass(adminJdbc(), other.orgId(), other.unitId(), EnrollmentFixtures.modality(adminJdbc(), other.orgId(), 0),
                EnrollmentFixtures.teacher(adminJdbc(), other.orgId(), null));
        assertThat(get("?classId=" + foreignClass, c).getStatus()).isEqualTo(404);
        assertThat(get("?classId=" + UUID.randomUUID(), c).getStatus()).isEqualTo(404);
        assertThat(get("?unitId=" + other.unitId(), c).getStatus()).isEqualTo(404);
        assertThat(get("?status=OUTRO", c).getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA29 (R20): counts de Ativos e Ex-alunos coerentes com a lista, sem o filtro rápido; o Administrador conta só o alcance; filtro rápido filtra")
    void counts() throws Exception {
        Tenant t = tenant();
        UUID b2 = unit(t);
        UUID a1 = student(t, t.unitId(), "Ativo A1"), a2 = student(t, t.unitId(), "Ativo A2"), f1 = student(t, t.unitId(), "Ex A1"), none = student(t, t.unitId(), "Sem A"),
                inB = student(t, b2, "Ativo B");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), a1, t.unitId(), "ACTIVE", 0, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), a2, t.unitId(), "ACTIVE", 0, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), f1, t.unitId(), "ENDED", 0, null);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), inB, b2, "ACTIVE", 0, null);
        assertThat(none).isNotNull();
        var c = who(t, Role.COORDINATOR);

        JsonNode all = body("", c);
        assertThat(all.get("counts").get("active").asLong()).isEqualTo(3);
        assertThat(all.get("counts").get("former").asLong()).isEqualTo(1);
        assertThat(all.get("total").asLong()).isEqualTo(5);
        JsonNode active = body("?status=ACTIVE", c);
        assertThat(names(active)).containsExactly("Ativo A1", "Ativo A2", "Ativo B");
        assertThat(active.get("total").asLong()).isEqualTo(active.get("counts").get("active").asLong());
        assertThat(active.get("counts").get("former").asLong()).isEqualTo(1);
        JsonNode former = body("?status=FORMER", c);
        assertThat(names(former)).containsExactly("Ex A1");
        assertThat(former.get("counts").get("active").asLong()).isEqualTo(3);
        // A unidade entra na conta; a busca por nome, não.
        JsonNode inUnitB = body("?unitId=" + b2 + "&q=zzz", c);
        assertThat(inUnitB.get("counts").get("active").asLong()).isEqualTo(1);
        assertThat(inUnitB.get("counts").get("former").asLong()).isZero();
        // Administrador da A conta só o seu alcance.
        JsonNode adminA = body("", admin(t, t.unitId()));
        assertThat(adminA.get("counts").get("active").asLong()).isEqualTo(2);
        assertThat(adminA.get("counts").get("former").asLong()).isEqualTo(1);
        assertThat(body("?unitId=" + b2, admin(t, t.unitId())).get("counts").get("active").asLong()).isZero();
        // Professor e Aluno continuam sem a lista.
        assertThat(get("", who(t, Role.TEACHER)).getStatus()).isEqualTo(403);
        assertThat(get("", who(t, Role.STUDENT)).getStatus()).isEqualTo(403);
    }
}
