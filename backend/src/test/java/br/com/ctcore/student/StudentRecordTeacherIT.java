package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
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
 * R16 e R17 (CA23 a CA25): a ficha do Professor só abre para aluno de turma ativa dele (titular ou auxiliar; principal ou outro horário),
 * com as chaves "ver ficha" e "ver contatos" conferidas na unidade da turma, e só traz os campos da lista permitida.
 */
class StudentRecordTeacherIT extends OrganizationBase {
    /** Campos de RecordView que o Professor pode receber; campo novo só entra aqui depois de decidido (R17). */
    private static final Set<String> TEACHER_FIELDS = Set.of("id", "name", "photoUrl", "age", "minor", "registeredUnit", "ageRange", "contact", "emergency", "guardians");
    /** Campos que são só da gestão. */
    private static final Set<String> MANAGEMENT_ONLY = Set.of("birthDate", "seals", "cpf", "address");

    @Autowired ObjectMapper json;

    @AfterEach
    void clean() {
        EnrollmentFixtures.clean(adminJdbc());
        String mine = "(select id from account where email like '%.example')";
        adminJdbc().update("delete from account_invite where account_id in " + mine);
        adminJdbc().update("delete from account_session where account_id in " + mine);
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in " + mine + ")");
        adminJdbc().update("delete from account_link where account_id in " + mine);
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal teacher(Tenant t, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), Role.TEACHER, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@prof.example");
        return p;
    }

    private UUID unit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, true)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID student(Tenant t, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state, emergency_name, emergency_relationship, emergency_phone) values (?, ?, ?, ?, ?, ?, ?, 'aluno@ficticia.example', "
                + "'11987654321', '01001000', 'Rua Um', '1', 'Centro', 'São Paulo', 'SP', 'Contato Um', 'Mãe', '11911112222')",
                id, t.orgId(), t.unitId(), name, Modalities.normalize(name), LocalDate.now().minusYears(10), "%011d".formatted(Math.abs(id.getMostSignificantBits() % 100000000000L)));
        return id;
    }

    private void keys(UUID unit, String flags) {
        adminJdbc().update("update unit_settings set teacher_permissions = ?::boolean[] where unit_id = ?", flags, unit);
    }

    private MockHttpServletResponse record(UUID student, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.get("/api/v1/students/" + student + "/record"), who).getResponse();
    }

    @Test
    @DisplayName("CA23, CA24 (R16): 200 para aluno de turma ativa do Professor (titular, auxiliar, principal ou outro horário); outra turma, turma inativa e modalidade encerrada 404")
    void reach() throws Exception {
        Tenant t = tenant(), other = tenant();
        keys(t.unitId(), "{true,true,true,true,true}");
        AccountPrincipal me = teacher(t, t.unitId());
        UUID myTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), me.accountId());
        UUID otherTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), null);
        UUID bjj = EnrollmentFixtures.modality(adminJdbc(), t.orgId(), 0);
        UUID mine = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, myTeacher);
        UUID theirs = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, otherTeacher);
        UUID assisted = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, otherTeacher);
        adminJdbc().update("insert into training_class_assistant (organization_id, class_id, teacher_id) values (?, ?, ?)", t.orgId(), assisted, myTeacher);

        UUID primary = student(t, "Principal"), second = student(t, "Outro Horario"), aux = student(t, "Da Auxiliar"), stranger = student(t, "De Outra Turma"),
                none = student(t, "Sem Matricula"), ended = student(t, "Encerrado"), inactive = student(t, "Turma Inativa");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), primary, t.unitId(), "ACTIVE", bjj, mine, null, myTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), second, t.unitId(), "ACTIVE", bjj, theirs, mine, otherTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), aux, t.unitId(), "ACTIVE", bjj, assisted, null, otherTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), stranger, t.unitId(), "ACTIVE", bjj, theirs, null, otherTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), ended, t.unitId(), "ENDED", bjj, mine, null, myTeacher);
        UUID stopped = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), bjj, myTeacher);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), inactive, t.unitId(), "ACTIVE", bjj, stopped, null, myTeacher);
        adminJdbc().update("update training_class set status = 'INACTIVE' where id = ?", stopped);

        for (UUID ok : new UUID[] {primary, second, aux}) assertThat(record(ok, me).getStatus()).isEqualTo(200);
        for (UUID no : new UUID[] {stranger, none, ended, inactive, UUID.randomUUID()}) assertThat(record(no, me).getStatus()).isEqualTo(404);
        // Outra organização: nunca alcançável.
        keys(other.unitId(), "{true,true,true,true,true}");
        AccountPrincipal foreign = teacher(other, other.unitId());
        assertThat(record(primary, foreign).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA23, CA24 (R16): a chave vale na unidade da turma, não em qualquer unidade")
    void keysPerUnit() throws Exception {
        Tenant t = tenant();
        UUID b = unit(t);
        AccountPrincipal me = teacher(t, t.unitId(), b);
        UUID myTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), me.accountId());
        UUID student = student(t, "Aluno da A");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", 0, myTeacher);

        // Ficha desligada na unidade da turma (A), ligada na outra (B): 403.
        keys(t.unitId(), "{true,true,true,true,false}");
        keys(b, "{true,true,true,true,true}");
        assertThat(record(student, me).getStatus()).isEqualTo(403);
        // Ligada na unidade da turma: 200; contatos só se ligados lá, mesmo ligados na outra.
        keys(t.unitId(), "{true,true,false,true,true}");
        JsonNode withoutContacts = json.readTree(record(student, me).getContentAsString());
        assertThat(withoutContacts.has("contact")).isFalse();
        assertThat(withoutContacts.has("emergency")).isFalse();
        keys(t.unitId(), "{true,true,true,true,true}");
        keys(b, "{true,true,false,true,false}");
        assertThat(json.readTree(record(student, me).getContentAsString()).has("contact")).isTrue();
    }

    @Test
    @DisplayName("CA25 (R17): a ficha do Professor só tem campos da lista permitida; campo novo de RecordView reprova o teste até ser classificado")
    void allowedFields() throws Exception {
        Set<String> declared = new HashSet<>();
        for (RecordComponent c : StudentRecords.RecordView.class.getRecordComponents()) declared.add(c.getName());
        Set<String> known = new HashSet<>(TEACHER_FIELDS);
        known.addAll(MANAGEMENT_ONLY);
        assertThat(declared).as("campo novo em RecordView: classifique como do Professor ou só da gestão").isEqualTo(known);

        Tenant t = tenant();
        keys(t.unitId(), "{true,true,true,true,true}");
        AccountPrincipal me = teacher(t, t.unitId());
        UUID myTeacher = EnrollmentFixtures.teacher(adminJdbc(), t.orgId(), me.accountId());
        UUID student = student(t, "Ficha Professor");
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), "ACTIVE", 0, myTeacher);
        String body = record(student, me).getContentAsString();
        List<String> fields = new ArrayList<>();
        json.readTree(body).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).isNotEmpty().allMatch(TEACHER_FIELDS::contains);
        assertThat(body).doesNotContain("cpf", "address", "birthDate", "seals", "benefit", "plan", "monthly", "financial", "valueCents", "\"street\"");
    }
}
