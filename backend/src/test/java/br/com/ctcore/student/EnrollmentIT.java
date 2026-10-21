package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * RN-MAT-01, RN-MAT-02, RN-MAT-03, RN-MAT-04 e RN-MAT-19 (R1 a R8): matrícula, adição de modalidade, limite, validações, tudo ou nada, retorno de
 * ex-aluno, leitura, busca por CPF e e-mail, perfis, alcance, isolamento e auditoria, com banco real.
 */
class EnrollmentIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    /** Uma modalidade completa da unidade: turma e plano ativos. */
    record Offer(UUID unit, UUID modality, UUID klass, UUID plan) {}

    @AfterEach
    void clean() {
        for (String table : new String[] {"enrollment_modality_class", "enrollment_modality", "enrollment", "student_guardian", "guardian", "student", "plan",
                "training_class_schedule", "training_class", "teacher_unit", "teacher_modality", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal coordinator(Tenant t) {
        return principal(t, Role.COORDINATOR, t.unitId());
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return principal(t, Role.ADMINISTRATOR, units);
    }

    private AccountPrincipal principal(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private UUID unit(Tenant t, boolean active) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Outra', ?, ?)", id, t.orgId(),
                "u" + id.toString().substring(0, 12), active);
        adminJdbc().update("insert into unit_settings (organization_id, unit_id, due_days) values (?, ?, '{5,10}') on conflict do nothing", t.orgId(), id);
        return id;
    }

    private UUID teacher(Tenant t) {
        UUID account = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", account, account + "@prof.example");
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, 'Professor', 'professor', ?)", id, t.orgId(), account);
        return id;
    }

    private UUID modalityId(Tenant t, int index, boolean active, UUID unit, boolean offered) {
        UUID id = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(index);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, ?) on conflict do nothing", t.orgId(), id, active);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, ?) on conflict do nothing", t.orgId(), unit, id, offered);
        return id;
    }

    private UUID klass(Tenant t, UUID unit, UUID modality, String name, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), unit, name, Modalities.normalize(name), modality, teacher(t), status);
        return id;
    }

    private UUID plan(Tenant t, UUID unit, UUID modality, String name, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 15000, ?)",
                id, t.orgId(), unit, modality, name, Modalities.normalize(name), status);
        return id;
    }

    private Offer offer(Tenant t, UUID unit, int index) {
        UUID m = modalityId(t, index, true, unit, true);
        return new Offer(unit, m, klass(t, unit, m, "Turma " + index + unit.toString().substring(0, 4), "ACTIVE"), plan(t, unit, m, "Plano " + index, "ACTIVE"));
    }

    private UUID student(Tenant t, String cpf, String email) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, email, phone, postal_code, street, number, "
                + "district, city, state) values (?, ?, ?, 'Aluno Fictício', 'aluno ficticio', date '1990-01-01', ?, ?, '11900000000', '01001000', 'Rua', '1', 'Centro', 'Cidade', 'SP')",
                id, t.orgId(), t.unitId(), cpf, email);
        return id;
    }

    private ObjectNode input(Offer o, Integer dueDay) {
        ObjectNode n = json.createObjectNode().put("unitId", o.unit().toString()).put("modalityId", o.modality().toString())
                .put("primaryClassId", o.klass().toString()).put("planId", o.plan().toString());
        if (dueDay != null) n.put("dueDay", dueDay);
        n.putArray("otherClassIds");
        return n;
    }

    private ObjectNode body(ObjectNode... modalities) {
        ObjectNode b = json.createObjectNode();
        var array = b.putArray("modalities");
        for (ObjectNode m : modalities) array.add(m);
        return b;
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json").content(body == null ? "" : body.toString());
        return call(request, who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private MockHttpServletResponse enroll(UUID student, ObjectNode body, AccountPrincipal who) throws Exception {
        return send(HttpMethod.POST, "/" + student + "/modalities", body, who);
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private int audits(Tenant t, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }

    @Test
    @DisplayName("CA1, CA5, CA6, CA12 (RN-MAT-01, RN-MAT-02, RN-MAT-04): matricula, adiciona à mesma matrícula e audita sem dado pessoal")
    void createsAndAdds() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0), judo = offer(t, t.unitId(), 1);
        UUID s = student(t, "52998224725", "aluno@ficticia.example");
        assertThat(ok(send(HttpMethod.GET, "/" + s + "/enrollment", null, coordinator(t)), 200).get("status").asText()).isEqualTo("NONE");

        JsonNode created = ok(enroll(s, body(input(bjj, 5).put("otherClassIds", (String) null)), coordinator(t)), 201);
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(created.get("enrolledAt").isNull()).isFalse();
        JsonNode m = created.get("modalities").get(0);
        assertThat(m.get("dueDay").asInt()).isEqualTo(5);
        assertThat(m.get("primaryClass").get("id").asText()).isEqualTo(bjj.klass().toString());
        assertThat(m.get("plan").get("id").asText()).isEqualTo(bjj.plan().toString());
        assertThat(m.get("canEdit").asBoolean()).isTrue();

        // Segunda modalidade na mesma unidade herda o dia; dia diferente é recusado (RN-MAT-03).
        assertThat(enroll(s, body(input(judo, 10)), coordinator(t)).getStatus()).isEqualTo(422);
        JsonNode added = ok(enroll(s, body(input(judo, null)), coordinator(t)), 201);
        assertThat(added.get("id").asText()).isEqualTo(created.get("id").asText());
        assertThat(added.get("modalities")).hasSize(2);
        assertThat(added.get("modalities").get(1).get("dueDay").asInt()).isEqualTo(5);
        assertThat(count("select count(*) from enrollment where student_id = ?", s)).isEqualTo(1);
        assertThat(ok(send(HttpMethod.GET, "/" + s + "/enrollment", null, coordinator(t)), 200)).isEqualTo(added);

        assertThat(audits(t, "student.enrolled")).isEqualTo(1);
        assertThat(audits(t, "student.modality_added")).isEqualTo(2);
        String trail = String.join("", adminJdbc().queryForList("select coalesce(after_data::text, '') from audit_log where organization_id = ? and action like 'student.%'",
                String.class, t.orgId()));
        assertThat(trail).doesNotContain("52998224725", "aluno@ficticia.example", "11900000000");
        assertThat(adminJdbc().queryForObject("select unit_id from audit_log where organization_id = ? and action = 'student.modality_added' limit 1", UUID.class, t.orgId())).isEqualTo(t.unitId());
    }

    @Test
    @DisplayName("CA3, CA5: turma, plano, modalidade, unidade e dia inválidos dão 422 e nada é gravado; de outra organização, 404")
    void validatesRules() throws Exception {
        Tenant t = tenant(), other = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID s = student(t, "52998224725", null);
        UUID second = unit(t, true);
        Offer elsewhere = offer(t, second, 2);
        UUID inactiveClass = klass(t, t.unitId(), bjj.modality(), "Parada", "INACTIVE");
        UUID inactivePlan = plan(t, t.unitId(), bjj.modality(), "Velho", "INACTIVE");
        UUID otherKind = klass(t, t.unitId(), modalityId(t, 3, true, t.unitId(), true), "Boxe", "ACTIVE");
        UUID foreign = offer(other, other.unitId(), 0).klass();

        for (ObjectNode bad : new ObjectNode[] {
                input(bjj, 5).put("primaryClassId", inactiveClass.toString()), input(bjj, 5).put("primaryClassId", otherKind.toString()),
                input(bjj, 5).put("primaryClassId", elsewhere.klass().toString()), input(bjj, 5).put("planId", inactivePlan.toString()),
                input(bjj, 5).put("planId", elsewhere.plan().toString()), input(bjj, 5).put("otherClassIds", (String) null).set("otherClassIds",
                        json.createArrayNode().add(bjj.klass().toString())), input(bjj, 9), input(bjj, 5).put("unitId", second.toString()),
                input(bjj, 5).put("modalityId", modalityId(t, 4, false, t.unitId(), true).toString()),
                input(bjj, 5).put("modalityId", modalityId(t, 5, true, t.unitId(), false).toString())}) {
            MockHttpServletResponse r = enroll(s, body(bad), coordinator(t));
            assertThat(r.getStatus()).as(bad + " " + r.getContentAsString()).isEqualTo(422);
        }
        assertThat(enroll(s, body(input(bjj, 5).put("primaryClassId", foreign.toString())), coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(enroll(s, body(input(bjj, 5).put("planId", UUID.randomUUID().toString())), coordinator(t)).getStatus()).isEqualTo(404);
        UUID closed = unit(t, false);
        Offer inactiveUnit = new Offer(closed, bjj.modality(), klass(t, closed, bjj.modality(), "Fechada", "ACTIVE"), plan(t, closed, bjj.modality(), "Plano P", "ACTIVE"));
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true)", t.orgId(), closed, bjj.modality());
        assertThat(enroll(s, body(input(inactiveUnit, 5)), coordinator(t)).getStatus()).isEqualTo(422);
        assertThat(enroll(s, body(input(bjj, null)), coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(enroll(s, body(), coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(count("select count(*) from enrollment")).isZero();

        // Na unidade B o dia é livre entre os dela (CA5).
        ok(enroll(s, body(input(bjj, 5)), coordinator(t)), 201);
        ok(enroll(s, body(input(elsewhere, 10)), coordinator(t)), 201);
    }

    @Test
    @DisplayName("CA3: turma principal com dois outros horários é lida de volta")
    void otherClasses() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID a = klass(t, t.unitId(), bjj.modality(), "Extra A", "ACTIVE"), b = klass(t, t.unitId(), bjj.modality(), "Extra B", "ACTIVE");
        UUID s = student(t, "52998224725", null);
        ObjectNode in = input(bjj, 5);
        in.putArray("otherClassIds").add(a.toString()).add(b.toString());
        JsonNode m = ok(enroll(s, body(in), coordinator(t)), 201).get("modalities").get(0);
        assertThat(m.get("otherClasses")).hasSize(2);
        assertThat(m.get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
    }

    @Test
    @DisplayName("CA2, CA4 (RN-MAT-01): limite de 10 ativas (inclusivo), mesma modalidade ativa repetida dá 409 e o pedido é tudo ou nada")
    void limitsAndAtomicity() throws Exception {
        Tenant t = tenant();
        UUID s = student(t, "52998224725", null);
        List<Offer> offers = new ArrayList<>();
        for (int i = 0; i < 11; i++) offers.add(offer(t, t.unitId(), i));
        ObjectNode[] nine = new ObjectNode[9];
        for (int i = 0; i < 9; i++) nine[i] = input(offers.get(i), 5);
        ok(enroll(s, body(nine), coordinator(t)), 201);
        assertThat(enroll(s, body(input(offers.get(9), 5), input(offers.get(10), 5)), coordinator(t)).getStatus()).isEqualTo(422);
        assertThat(count("select count(*) from enrollment_modality")).isEqualTo(9);
        ok(enroll(s, body(input(offers.get(9), 5)), coordinator(t)), 201);
        MockHttpServletResponse eleventh = enroll(s, body(input(offers.get(10), 5)), coordinator(t));
        assertThat(eleventh.getStatus()).isEqualTo(422);
        assertThat(json.readTree(eleventh.getContentAsString()).get("fields").get(0).get("field").asText()).isEqualTo("modalities");
        assertThat(enroll(s, body(input(offers.get(0), 5)), coordinator(t)).getStatus()).isEqualTo(409);

        UUID other = student(t, "11144477735", null);
        int before = audits(t, "student.modality_added");
        ObjectNode invalid = input(offers.get(1), 5).put("planId", offers.get(0).plan().toString());
        assertThat(enroll(other, body(input(offers.get(0), 5), invalid), coordinator(t)).getStatus()).isEqualTo(422);
        assertThat(count("select count(*) from enrollment where student_id = ?", other)).isZero();
        assertThat(audits(t, "student.modality_added")).isEqualTo(before);
        assertThat(audits(t, "student.enrolled")).isEqualTo(1);
    }

    @Test
    @DisplayName("CA1: duas chamadas simultâneas não criam duas matrículas")
    void concurrent() throws Exception {
        Tenant t = tenant();
        Offer a = offer(t, t.unitId(), 0), b = offer(t, t.unitId(), 1);
        UUID s = student(t, "52998224725", null);
        AccountPrincipal who = coordinator(t);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> one = () -> enroll(s, body(input(a, 5)), who).getStatus();
            Callable<Integer> two = () -> enroll(s, body(input(b, 5)), who).getStatus();
            var results = pool.invokeAll(List.of(one, two));
            assertThat(results.get(0).get()).isEqualTo(201);
            assertThat(results.get(1).get()).isEqualTo(201);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from enrollment where student_id = ?", s)).isEqualTo(1);
        assertThat(count("select count(*) from enrollment_modality where student_id = ?", s)).isEqualTo(2);
    }

    @Test
    @DisplayName("CA6, CA8 (RN-MAT-19): ex-aluno volta à mesma matrícula, com reopened_at, histórico mantido e auditoria da reabertura")
    void returns() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0), judo = offer(t, t.unitId(), 1);
        UUID s = student(t, "52998224725", "volta@ficticia.example");
        UUID first = UUID.fromString(ok(enroll(s, body(input(bjj, 5)), coordinator(t)), 201).get("id").asText());
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where student_id = ?", s);
        assertThat(ok(send(HttpMethod.GET, "/" + s + "/enrollment", null, coordinator(t)), 200).get("status").asText()).isEqualTo("FORMER");
        assertThat(ok(send(HttpMethod.GET, "/lookup?cpf=52998224725", null, coordinator(t)), 200).get("enrollmentStatus").asText()).isEqualTo("FORMER");

        JsonNode back = ok(enroll(s, body(input(judo, 10)), coordinator(t)), 201);
        assertThat(back.get("id").asText()).isEqualTo(first.toString());
        assertThat(back.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(back.get("reopenedAt").isNull()).isFalse();
        assertThat(back.get("modalities")).hasSize(2);
        assertThat(count("select count(*) from enrollment_modality where student_id = ? and status = 'ENDED'", s)).isEqualTo(1);
        assertThat(audits(t, "student.enrollment_reopened")).isEqualTo(1);
        assertThat(audits(t, "student.enrolled")).isEqualTo(1);
        // A mesma modalidade encerrada volta como linha nova: o índice único só vale para as ativas.
        adminJdbc().update("update enrollment_modality set status = 'ENDED', ended_at = now() where student_id = ?", s);
        ok(enroll(s, body(input(bjj, 5)), coordinator(t)), 201);
    }

    @Test
    @DisplayName("CA9 (RN-MAT-19): CPF e e-mail apagados não são achados e o cadastro novo cria outro aluno")
    void anonymised() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID s = student(t, "52998224725", "apagado@ficticia.example");
        ok(enroll(s, body(input(bjj, 5)), coordinator(t)), 201);
        adminJdbc().update("update student set cpf = null, email = null where id = ?", s);
        assertThat(send(HttpMethod.GET, "/lookup?cpf=52998224725", null, coordinator(t)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/lookup?email=apagado@ficticia.example", null, coordinator(t)).getStatus()).isEqualTo(404);
        UUID again = student(t, "52998224725", "apagado@ficticia.example");
        assertThat(again).isNotEqualTo(s);
        assertThat(ok(send(HttpMethod.GET, "/lookup?cpf=52998224725", null, coordinator(t)), 200).get("id").asText()).isEqualTo(again.toString());
    }

    @Test
    @DisplayName("CA10 (RN-ALU-06): busca por CPF ou e-mail devolve só id, nome, foto e situação; sem ou com os dois, 400; outra organização, 404")
    void lookup() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID none = student(t, "52998224725", "sem@ficticia.example"), active = student(t, "11144477735", "ativo@ficticia.example");
        ok(enroll(active, body(input(bjj, 5)), coordinator(t)), 201);
        for (String query : new String[] {"?cpf=52998224725", "?email=SEM@ficticia.example"}) {
            JsonNode found = ok(send(HttpMethod.GET, "/lookup" + query, null, admin(t, unit(t, true))), 200);
            assertThat(found.get("id").asText()).isEqualTo(none.toString());
            assertThat(found.get("enrollmentStatus").asText()).isEqualTo("NONE");
            assertThat(found.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "name", "photoUrl", "enrollmentStatus");
        }
        assertThat(ok(send(HttpMethod.GET, "/lookup?email=ativo@ficticia.example", null, coordinator(t)), 200).get("enrollmentStatus").asText()).isEqualTo("ACTIVE");
        assertThat(send(HttpMethod.GET, "/lookup", null, coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/lookup?cpf=52998224725&email=sem@ficticia.example", null, coordinator(t)).getStatus()).isEqualTo(400);
        assertThat(send(HttpMethod.GET, "/lookup?email=sem@ficticia.example", null, coordinator(tenant())).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA7: aluno de outra organização dá 404 na matrícula; unidade sem alcance do Administrador dá 403")
    void readsAndReach() throws Exception {
        Tenant t = tenant(), other = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID second = unit(t, true);
        Offer elsewhere = offer(t, second, 1);
        UUID s = student(t, "52998224725", null);
        ok(enroll(s, body(input(bjj, 5)), admin(t, t.unitId())), 201);
        assertThat(enroll(s, body(input(elsewhere, 5)), admin(t, t.unitId())).getStatus()).isEqualTo(403);
        ok(enroll(s, body(input(elsewhere, 5)), coordinator(t)), 201);
        for (JsonNode m : ok(send(HttpMethod.GET, "/" + s + "/enrollment", null, admin(t, t.unitId())), 200).get("modalities")) {
            boolean own = m.get("unit").get("id").asText().equals(t.unitId().toString());
            assertThat(m.get("canEdit").asBoolean()).as(m.toString()).isEqualTo(own);
        }
        assertThat(send(HttpMethod.GET, "/" + s + "/enrollment", null, coordinator(other)).getStatus()).isEqualTo(404);
        assertThat(enroll(s, body(input(bjj, 5)), coordinator(other)).getStatus()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/" + UUID.randomUUID() + "/enrollment", null, coordinator(t)).getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("CA11: Professor, Aluno e Responsável recebem 403, sem sessão 401")
    void profiles() throws Exception {
        Tenant t = tenant();
        Offer bjj = offer(t, t.unitId(), 0);
        UUID s = student(t, "52998224725", null);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = principal(t, role, t.unitId());
            assertThat(enroll(s, body(input(bjj, 5)), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(send(HttpMethod.GET, "/" + s + "/enrollment", null, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(enroll(s, body(input(bjj, 5)), null).getStatus()).isEqualTo(401);
        assertThat(send(HttpMethod.GET, "/" + s + "/enrollment", null, null).getStatus()).isEqualTo(401);
        assertThat(count("select count(*) from enrollment")).isZero();
    }
}
