package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * RN-MAT-15, RN-MAT-20, RN-ORG-12 e RN-ALU-07 (R9 a R11, R15): troca de turma (na hora), troca de plano (agendada), transferência entre unidades,
 * alcance por unidade, perfis, isolamento entre organizações e auditoria, com banco real.
 */
class EnrollmentChangesIT extends OrganizationBase {
    @Autowired ObjectMapper json;

    record Offer(UUID unit, UUID modality, UUID klass, UUID plan) {}

    @AfterEach
    void clean() {
        for (String table : new String[] {"enrollment_modality_class", "enrollment_modality", "enrollment", "student_guardian", "guardian", "student", "plan",
                "training_class_schedule", "training_class", "teacher_unit", "teacher_modality", "teacher", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from account_link_unit where link_id in (select id from account_link where account_id in (select id from account where email like '%.example'))");
        adminJdbc().update("delete from account_link where account_id in (select id from account where email like '%.example')");
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private AccountPrincipal principal(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = new AccountPrincipal(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), t.orgId(), role, Set.of(units));
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", p.accountId(), p.accountId() + "@gestao.example");
        return p;
    }

    private AccountPrincipal coordinator(Tenant t) {
        return principal(t, Role.COORDINATOR, t.unitId());
    }

    private AccountPrincipal admin(Tenant t, UUID... units) {
        return principal(t, Role.ADMINISTRATOR, units);
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

    private UUID modality(Tenant t, UUID unit, int index) {
        UUID id = adminJdbc().queryForList("select id from modality order by name", UUID.class).get(index);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), id);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, id);
        return id;
    }

    /** Turma e plano ativos de uma modalidade já cadastrada, numa unidade. */
    private Offer offerOf(Tenant t, UUID unit, UUID modality, String tag) {
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        return new Offer(unit, modality, klass(t, unit, modality, "Turma " + tag, "ACTIVE"), plan(t, unit, modality, "Plano " + tag, "ACTIVE"));
    }

    private UUID student(Tenant t, String cpf) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, cpf, phone, postal_code, street, number, "
                + "district, city, state) values (?, ?, ?, 'Aluno Fictício', 'aluno ficticio', date '1990-01-01', ?, '11900000000', '01001000', 'Rua', '1', "
                + "'Centro', 'Cidade', 'SP')", id, t.orgId(), t.unitId(), cpf);
        return id;
    }

    private ObjectNode input(Offer o, Integer dueDay) {
        ObjectNode n = json.createObjectNode().put("unitId", o.unit().toString()).put("modalityId", o.modality().toString())
                .put("primaryClassId", o.klass().toString()).put("planId", o.plan().toString());
        if (dueDay != null) n.put("dueDay", dueDay);
        return n;
    }

    private MockHttpServletResponse send(HttpMethod method, String path, JsonNode body, AccountPrincipal who) throws Exception {
        var request = MockMvcRequestBuilders.request(method, "/api/v1/students" + path).contentType("application/json").content(body == null ? "" : body.toString());
        return call(request, who).getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    /** Matricula o aluno na oferta e devolve o id da linha da modalidade. */
    private UUID enroll(UUID student, Offer o, int dueDay, AccountPrincipal who) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.putArray("modalities").add(input(o, dueDay));
        JsonNode e = ok(send(HttpMethod.POST, "/" + student + "/modalities", body, who), 201);
        for (JsonNode m : e.get("modalities")) if (m.get("modality").get("id").asText().equals(o.modality().toString())) return UUID.fromString(m.get("id").asText());
        throw new AssertionError(e.toString());
    }

    private MockHttpServletResponse changeClass(UUID s, UUID row, UUID primary, List<UUID> others, AccountPrincipal who) throws Exception {
        ObjectNode b = json.createObjectNode().put("primaryClassId", primary.toString());
        var array = b.putArray("otherClassIds");
        for (UUID o : others) array.add(o.toString());
        return send(HttpMethod.PUT, "/" + s + "/modalities/" + row + "/class", b, who);
    }

    private MockHttpServletResponse changePlan(UUID s, UUID row, UUID plan, AccountPrincipal who) throws Exception {
        return send(HttpMethod.PUT, "/" + s + "/modalities/" + row + "/plan", json.createObjectNode().put("planId", plan.toString()), who);
    }

    private MockHttpServletResponse transfer(UUID s, UUID row, Offer to, Integer dueDay, AccountPrincipal who) throws Exception {
        ObjectNode b = json.createObjectNode().put("unitId", to.unit().toString()).put("primaryClassId", to.klass().toString()).put("planId", to.plan().toString());
        if (dueDay != null) b.put("dueDay", dueDay);
        return send(HttpMethod.POST, "/" + s + "/modalities/" + row + "/transfer", b, who);
    }

    private JsonNode modalityOf(JsonNode enrollment, UUID row) {
        for (JsonNode m : enrollment.get("modalities")) if (m.get("id").asText().equals(row.toString())) return m;
        throw new AssertionError(enrollment.toString());
    }

    private int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }

    private int audits(Tenant t, String action) {
        return count("select count(*) from audit_log where organization_id = ? and action = ?", t.orgId(), action);
    }

    private JsonNode read(UUID s, AccountPrincipal who) throws Exception {
        return ok(send(HttpMethod.GET, "/" + s + "/enrollment", null, who), 200);
    }

    @Test
    @DisplayName("CA13 (RN-MAT-15): a turma principal troca na hora, os outros horários são substituídos, turma inválida dá 422 e nada muda")
    void changesClass() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID bjj = modality(t, t.unitId(), 0);
        Offer o = offerOf(t, t.unitId(), bjj, "X");
        UUID y = klass(t, t.unitId(), bjj, "Turma Y", "ACTIVE"), z = klass(t, t.unitId(), bjj, "Turma Z", "ACTIVE");
        UUID s = student(t, "52998224725");
        AccountPrincipal gestor = coordinator(t);
        UUID row = enroll(s, o, 5, gestor);

        JsonNode after = ok(changeClass(s, row, y, List.of(o.klass(), z), gestor), 200);
        JsonNode m = modalityOf(after, row);
        assertThat(m.get("primaryClass").get("id").asText()).isEqualTo(y.toString());
        assertThat(m.get("otherClasses")).hasSize(2);
        assertThat(modalityOf(read(s, gestor), row)).isEqualTo(m);
        assertThat(count("select count(*) from enrollment_modality where primary_class_id = ?", o.klass())).isZero();
        // Outros horários são substituídos pelo que foi enviado.
        JsonNode replaced = ok(changeClass(s, row, y, List.of(), gestor), 200);
        assertThat(modalityOf(replaced, row).get("otherClasses")).isEmpty();
        assertThat(audits(t, "student.class_changed")).isEqualTo(2);
        String trail = adminJdbc().queryForObject("select before_data::text || after_data::text from audit_log where organization_id = ? and action = 'student.class_changed' "
                + "order by occurred_at limit 1", String.class, t.orgId());
        assertThat(trail).contains(o.klass().toString(), y.toString()).doesNotContain("52998224725", "11900000000");

        UUID inactive = klass(t, t.unitId(), bjj, "Parada", "INACTIVE");
        UUID otherKind = klass(t, t.unitId(), modality(t, t.unitId(), 1), "Judô", "ACTIVE");
        UUID elsewhere = klass(t, unit(t, true), bjj, "Longe", "ACTIVE");
        for (UUID bad : new UUID[] {inactive, otherKind, elsewhere}) assertThat(changeClass(s, row, bad, List.of(), gestor).getStatus()).isEqualTo(422);
        assertThat(changeClass(s, row, y, List.of(y), gestor).getStatus()).isEqualTo(422);
        assertThat(changeClass(s, row, z, List.of(inactive), gestor).getStatus()).isEqualTo(422);
        assertThat(changeClass(s, row, UUID.randomUUID(), List.of(), gestor).getStatus()).isEqualTo(404);
        assertThat(changeClass(s, row, offerOf(other, other.unitId(), modality(other, other.unitId(), 0), "F").klass(), List.of(), gestor).getStatus()).isEqualTo(404);
        assertThat(modalityOf(read(s, gestor), row).get("primaryClass").get("id").asText()).isEqualTo(y.toString());
        assertThat(audits(t, "student.class_changed")).isEqualTo(2);
    }

    @Test
    @DisplayName("CA14 (RN-MAT-15): o plano novo fica agendado, o atual não muda, pedir o atual limpa e plano inválido dá 422")
    void changesPlan() throws Exception {
        Tenant t = tenant();
        UUID bjj = modality(t, t.unitId(), 0);
        Offer o = offerOf(t, t.unitId(), bjj, "P");
        UUID p2 = plan(t, t.unitId(), bjj, "Plano 2", "ACTIVE"), p3 = plan(t, t.unitId(), bjj, "Plano 3", "ACTIVE");
        UUID s = student(t, "52998224725");
        AccountPrincipal gestor = coordinator(t);
        UUID row = enroll(s, o, 5, gestor);

        assertThat(changePlan(s, row, o.plan(), gestor).getStatus()).as("sem agendamento, o atual é 422").isEqualTo(422);
        JsonNode m = modalityOf(ok(changePlan(s, row, p2, gestor), 200), row);
        assertThat(m.get("plan").get("id").asText()).isEqualTo(o.plan().toString());
        assertThat(m.get("nextPlan").get("id").asText()).isEqualTo(p2.toString());
        // Trocar de novo antes substitui o agendamento.
        assertThat(modalityOf(ok(changePlan(s, row, p3, gestor), 200), row).get("nextPlan").get("id").asText()).isEqualTo(p3.toString());
        // Pedir o plano atual limpa o agendamento.
        assertThat(modalityOf(ok(changePlan(s, row, o.plan(), gestor), 200), row).get("nextPlan").isNull()).isTrue();
        assertThat(audits(t, "student.plan_changed")).isEqualTo(3);

        UUID inactive = plan(t, t.unitId(), bjj, "Velho", "INACTIVE");
        UUID otherUnit = plan(t, unit(t, true), bjj, "Longe", "ACTIVE");
        UUID otherKind = plan(t, t.unitId(), modality(t, t.unitId(), 1), "Judô", "ACTIVE");
        for (UUID bad : new UUID[] {inactive, otherUnit, otherKind}) assertThat(changePlan(s, row, bad, gestor).getStatus()).isEqualTo(422);
        assertThat(changePlan(s, row, UUID.randomUUID(), gestor).getStatus()).isEqualTo(404);
        assertThat(modalityOf(read(s, gestor), row).get("nextPlan").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA15, CA16, CA17 (RN-MAT-20, RN-ORG-12): a transferência mantém a mesma linha, troca unidade, turma, plano e vencimento, e só toca a matrícula")
    void transfers() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID bjj = modality(t, t.unitId(), 0);
        UUID b = unit(t, true);
        Offer here = offerOf(t, t.unitId(), bjj, "A"), there = offerOf(t, b, bjj, "B");
        UUID extra = klass(t, b, bjj, "Extra B", "ACTIVE");
        UUID s = student(t, "52998224725");
        AccountPrincipal gestor = coordinator(t);
        UUID row = enroll(s, here, 5, gestor);
        adminJdbc().update("update enrollment_modality set next_plan_id = ? where id = ?", plan(t, t.unitId(), bjj, "Agendado", "ACTIVE"), row);
        OffsetStarted before = new OffsetStarted(adminJdbc().queryForObject("select started_at::text from enrollment_modality where id = ?", String.class, row),
                adminJdbc().queryForObject("select enrollment_id from enrollment_modality where id = ?", UUID.class, row));

        // Recusas: nada muda.
        assertThat(transfer(s, row, here, 5, gestor).getStatus()).as("mesma unidade").isEqualTo(422);
        Offer closed = offerOf(t, unit(t, false), bjj, "Fechada");
        assertThat(transfer(s, row, closed, 5, gestor).getStatus()).as("unidade inativa").isEqualTo(422);
        ObjectNode oldClass = json.createObjectNode().put("unitId", b.toString()).put("primaryClassId", here.klass().toString()).put("planId", there.plan().toString()).put("dueDay", 10);
        assertThat(send(HttpMethod.POST, "/" + s + "/modalities/" + row + "/transfer", oldClass, gestor).getStatus()).as("turma da unidade antiga").isEqualTo(422);
        ObjectNode oldPlan = oldClass.deepCopy().put("primaryClassId", there.klass().toString()).put("planId", here.plan().toString());
        assertThat(send(HttpMethod.POST, "/" + s + "/modalities/" + row + "/transfer", oldPlan, gestor).getStatus()).as("plano da unidade antiga").isEqualTo(422);
        assertThat(transfer(s, row, there, 9, gestor).getStatus()).as("vencimento fora da unidade nova").isEqualTo(422);
        assertThat(transfer(s, row, there, null, gestor).getStatus()).as("vencimento obrigatório na unidade sem outra modalidade").isEqualTo(400);
        Offer foreign = offerOf(other, other.unitId(), modality(other, other.unitId(), 0), "F");
        assertThat(transfer(s, row, foreign, 5, gestor).getStatus()).as("unidade de outra organização").isEqualTo(404);
        assertThat(transfer(s, UUID.randomUUID(), there, 10, gestor).getStatus()).as("modalidade inexistente").isEqualTo(404);
        assertThat(transfer(student(t, "11144477735"), row, there, 10, gestor).getStatus()).as("modalidade de outro aluno").isEqualTo(404);
        assertThat(count("select count(*) from enrollment_modality where id = ? and unit_id = ?", row, t.unitId())).isEqualTo(1);
        assertThat(audits(t, "student.modality_transferred")).isZero();

        int plans = count("select count(*) from plan"), classes = count("select count(*) from training_class"), students = count("select count(*) from student");
        ObjectNode ok = oldClass.deepCopy().put("primaryClassId", there.klass().toString());
        ok.putArray("otherClassIds").add(extra.toString());
        JsonNode m = modalityOf(ok(send(HttpMethod.POST, "/" + s + "/modalities/" + row + "/transfer", ok, gestor), 200), row);
        assertThat(m.get("unit").get("id").asText()).isEqualTo(b.toString());
        assertThat(m.get("primaryClass").get("id").asText()).isEqualTo(there.klass().toString());
        assertThat(m.get("plan").get("id").asText()).isEqualTo(there.plan().toString());
        assertThat(m.get("nextPlan").isNull()).isTrue();
        assertThat(m.get("otherClasses")).hasSize(1);
        assertThat(m.get("dueDay").asInt()).isEqualTo(10);
        assertThat(adminJdbc().queryForObject("select started_at::text from enrollment_modality where id = ?", String.class, row)).isEqualTo(before.startedAt());
        assertThat(adminJdbc().queryForObject("select enrollment_id from enrollment_modality where id = ?", UUID.class, row)).isEqualTo(before.enrollmentId());
        assertThat(count("select count(*) from enrollment_modality where student_id = ?", s)).isEqualTo(1);
        // CA17: nada além de enrollment_modality e suas turmas foi escrito.
        assertThat(count("select count(*) from plan")).isEqualTo(plans);
        assertThat(count("select count(*) from training_class")).isEqualTo(classes);
        assertThat(count("select count(*) from student")).isEqualTo(students);
        assertThat(count("select count(*) from enrollment where student_id = ?", s)).isEqualTo(1);

        assertThat(audits(t, "student.modality_transferred")).isEqualTo(1);
        var log = adminJdbc().queryForMap("select unit_id, before_data::text as b, after_data::text as a from audit_log where organization_id = ? and action = 'student.modality_transferred'", t.orgId());
        assertThat(log.get("unit_id")).isEqualTo(b);
        assertThat((String) log.get("b")).contains(t.unitId().toString());
        assertThat((String) log.get("a")).contains(b.toString()).doesNotContain("52998224725", "11900000000");

        // R4 na unidade nova: a segunda modalidade herda o dia dela, e uma transferida para lá recusa dia diferente.
        UUID judo = modality(t, t.unitId(), 1);
        Offer judoHere = offerOf(t, t.unitId(), judo, "J"), judoThere = offerOf(t, b, judo, "JB");
        UUID row2 = enroll(s, judoHere, 5, gestor);
        assertThat(transfer(s, row2, judoThere, 5, gestor).getStatus()).isEqualTo(422);
        assertThat(modalityOf(ok(transfer(s, row2, judoThere, null, gestor), 200), row2).get("dueDay").asInt()).isEqualTo(10);
    }

    private record OffsetStarted(String startedAt, UUID enrollmentId) {}

    @Test
    @DisplayName("CA18 (RN-ALU-07, R15): Administrador só altera a modalidade da sua unidade e só transfere entre unidades que alcança; Coordenador pode")
    void reachAndProfiles() throws Exception {
        Tenant t = tenant(), other = tenant();
        UUID bjj = modality(t, t.unitId(), 0);
        UUID b = unit(t, true);
        Offer inA = offerOf(t, t.unitId(), bjj, "A"), inB = offerOf(t, b, bjj, "B");
        UUID judo = modality(t, t.unitId(), 1);
        Offer judoB = offerOf(t, b, judo, "JB"), judoA = offerOf(t, t.unitId(), judo, "JA");
        UUID s = student(t, "52998224725");
        AccountPrincipal gestor = coordinator(t), adminA = admin(t, t.unitId());
        UUID rowA = enroll(s, inA, 5, gestor), rowB = enroll(s, judoB, 5, gestor);
        UUID spare = klass(t, b, judo, "Reserva", "ACTIVE");

        // A modalidade da B é visível ao Administrador da A, mas não alterável.
        assertThat(modalityOf(read(s, adminA), rowB).get("canEdit").asBoolean()).isFalse();
        assertThat(changeClass(s, rowB, spare, List.of(), adminA).getStatus()).isEqualTo(403);
        assertThat(changePlan(s, rowB, plan(t, b, judo, "Novo A", "ACTIVE"), adminA).getStatus()).isEqualTo(403);
        assertThat(transfer(s, rowB, judoA, 5, adminA).getStatus()).isEqualTo(403);
        // Da A para a B, quando ele só alcança a A: 403.
        assertThat(transfer(s, rowA, inB, 10, adminA).getStatus()).isEqualTo(403);
        assertThat(count("select count(*) from enrollment_modality where id = ? and unit_id = ?", rowA, t.unitId())).isEqualTo(1);
        assertThat(count("select count(*) from enrollment_modality where id = ? and primary_class_id = ?", rowB, spare)).isZero();
        // Na unidade dele, pode.
        UUID extraA = klass(t, t.unitId(), bjj, "Extra A", "ACTIVE");
        ok(changeClass(s, rowA, extraA, List.of(), adminA), 200);
        // Coordenador pode as três.
        ok(changeClass(s, rowB, spare, List.of(), gestor), 200);
        ok(changePlan(s, rowB, plan(t, b, judo, "Novo B", "ACTIVE"), gestor), 200);
        ok(transfer(s, rowA, inB, 5, gestor), 200);

        UUID home = unit(t, true);
        for (Role role : new Role[] {Role.TEACHER, Role.STUDENT, Role.GUARDIAN}) {
            AccountPrincipal who = principal(t, role, t.unitId(), b, home);
            assertThat(changeClass(s, rowB, spare, List.of(), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(changePlan(s, rowB, inB.plan(), who).getStatus()).as(role.name()).isEqualTo(403);
            assertThat(transfer(s, rowB, judoA, 5, who).getStatus()).as(role.name()).isEqualTo(403);
        }
        assertThat(changeClass(s, rowB, spare, List.of(), null).getStatus()).isEqualTo(401);
        assertThat(changePlan(s, rowB, inB.plan(), null).getStatus()).isEqualTo(401);
        assertThat(transfer(s, rowB, judoA, 5, null).getStatus()).isEqualTo(401);

        // Isolamento: outra organização não enxerga o aluno nem a modalidade.
        AccountPrincipal foreign = coordinator(other);
        assertThat(changeClass(s, rowB, spare, List.of(), foreign).getStatus()).isEqualTo(404);
        assertThat(changePlan(s, rowB, inB.plan(), foreign).getStatus()).isEqualTo(404);
        assertThat(transfer(s, rowB, judoA, 5, foreign).getStatus()).isEqualTo(404);
    }
}
