package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationTransactions;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RF-POR-07, RN-POR-01, RN-POR-02, RN-POR-06 a RN-POR-09, RN-INA-08, RN-COB-03: aba Minhas informações do portal (leitura e isolamento), com banco real. */
class PortalInfoIT extends PortalTermBase {
    @Autowired OrganizationTransactions transactions;
    @Autowired JdbcClient jdbc;

    @AfterEach
    void cleanInfo() {
        adminJdbc().update("delete from student_graduation");
        adminJdbc().update("delete from graduation_level");
    }

    /** Sessão de portal com vínculo real (a preferência de e-mail lê o vínculo ativo). */
    private AccountPrincipal session(Tenant t, UUID account, Role role) {
        UUID link = adminJdbc().queryForList("select id from account_link where account_id = ? and organization_id = ? and role = ?", UUID.class, account, t.orgId(), role.name())
                .stream().findFirst().orElseGet(() -> {
                    UUID id = UUID.randomUUID();
                    adminJdbc().update("insert into account_link (id, account_id, organization_id, role, status) values (?, ?, ?, ?, 'ACTIVE')", id, account, t.orgId(), role.name());
                    return id;
                });
        return new AccountPrincipal(account, UUID.randomUUID(), link, t.orgId(), role, Set.of());
    }

    private UUID author() {
        return adminJdbc().queryForList("select id from account where email = 'autor@portal.example'", UUID.class).stream().findFirst().orElseGet(() -> account("autor@portal.example"));
    }

    private EnrollmentFixtures.Enrolled enroll(Tenant t, UUID student, String status, int kind) {
        var e = EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, kind, null);
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1') on conflict do nothing",
                UUID.randomUUID(), t.orgId(), student, author());
        return e;
    }

    private UUID modalityOf(UUID row) {
        return adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, row);
    }

    private JsonNode info(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(session(t, account, role), "/info" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private UUID level(Tenant t, UUID modality, int position, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, kids) values (?, ?, ?, ?, ?, '#112233', '#445566', 4, false)",
                id, t.orgId(), modality, position, name);
        return id;
    }

    private void graduation(Tenant t, UUID student, UUID modality, UUID level, int degree, boolean origin, String achieved, String createdAgo) {
        adminJdbc().update("insert into student_graduation (id, organization_id, student_id, modality_id, level_id, degree, achieved_on, origin, has_proof, registered_by_account_id, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?::date, ?, false, ?, now() - ?::interval)", UUID.randomUUID(), t.orgId(), student, modality, level, degree, achieved, origin, author(), createdAgo);
    }

    private void benefit(Tenant t, UUID student, UUID row, String type, long value, String validUntil, String status) {
        adminJdbc().update("insert into benefit (id, organization_id, enrollment_modality_id, student_id, unit_id, type, value, reason, decided_by, granted_by_account_id, valid_until, status, ended_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Motivo de teste', 'Decisão', ?, ?::date, ?, case when ? = 'ENDED' then now() end)",
                UUID.randomUUID(), t.orgId(), row, student, t.unitId(), type, value, author(), validUntil, status, status);
    }

    private void slot(Tenant t, UUID klass, int weekday, String from, String to) {
        adminJdbc().update("insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values (?, ?, ?, ?::time, ?::time)", t.orgId(), klass, weekday, from, to);
    }

    @Test
    @DisplayName("CA1, CA7, CA8, CA9, CA10: dados com CPF mascarado, treinos com unidade e professor, mensalidade com benefício, faixa e histórico, termos e preferência")
    void info() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        adminJdbc().update("update student set cpf = '52998224725', phone = '11987654321', email = 'aluno@portal.example', emergency_name = 'Mãe', emergency_relationship = 'Mãe', emergency_phone = '11911112222' where id = ?", s);
        adminJdbc().update("update unit set phone = '1133334444', whatsapp = null, email = 'unidade@portal.example' where id = ?", t.unitId());

        var jiu = enroll(t, s, "ACTIVE", 0);
        var judo = enroll(t, s, "ACTIVE", 1);
        UUID jm = modalityOf(jiu.row());
        adminJdbc().update("update teacher set name = 'Prof. X', email = 'prof@portal.example', phone = '11999990000' where id = ?", jiu.teacher());
        slot(t, jiu.klass(), 1, "19:00", "20:00");
        slot(t, jiu.klass(), 3, "19:00", "20:00");
        UUID extra = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), jm, jiu.teacher());
        slot(t, extra, 5, "20:00", "21:00");
        adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", t.orgId(), jiu.row(), extra);
        adminJdbc().update("update plan set value_cents = 20000 where id = ?", jiu.plan());
        benefit(t, s, jiu.row(), "PERCENT", 10, null, "ACTIVE");
        UUID next = UUID.randomUUID();
        adminJdbc().update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, 'Seguinte', 'seguinte', 25000, 'ACTIVE')", next, t.orgId(), t.unitId(), jm);
        adminJdbc().update("update enrollment_modality set next_plan_id = ? where id = ?", next, jiu.row());

        UUID blue = level(t, jm, 0, "Azul");
        graduation(t, s, jm, blue, 0, true, "2024-01-10", "400 days");
        graduation(t, s, jm, blue, 1, false, "2025-03-10", "300 days");
        graduation(t, s, jm, blue, 2, false, "2026-02-10", "100 days");
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'IMAGE_USE', 'ACCEPTED', 1, ?, '127.0.0.1')",
                UUID.randomUUID(), t.orgId(), s, acc);
        session(t, acc, Role.STUDENT);
        adminJdbc().update("update account_link set announcement_email = false where account_id = ?", acc);

        JsonNode i = info(t, acc, Role.STUDENT, "");
        assertThat(i.get("reduced").asBoolean()).isFalse();
        assertThat(i.get("selectedStudentId").asText()).isEqualTo(s.toString());
        JsonNode p = i.get("personal");
        assertThat(p.get("maskedCpf").asText()).isEqualTo("***.***.***-25");
        assertThat(p.get("name").asText()).isEqualTo("Aluno Um");
        assertThat(p.get("phone").asText()).isEqualTo("11987654321");
        assertThat(p.get("address").get("postalCode").asText()).isEqualTo("01001000");
        assertThat(p.get("emergency").get("name").asText()).isEqualTo("Mãe");
        assertThat(p.get("canEdit").asBoolean()).isTrue();
        assertThat(p.get("editableFields").toString()).contains("phone", "emergencyName", "photo").doesNotContain("name\"", "cpf", "birthDate", "email");

        assertThat(i.get("trainings")).hasSize(2);
        JsonNode jt = null;
        for (JsonNode tr : i.get("trainings")) if (tr.get("modality").get("id").asText().equals(jm.toString())) jt = tr;
        assertThat(jt.get("unit").get("phone").asText()).isEqualTo("1133334444");
        assertThat(jt.get("unit").get("whatsapp").isNull()).isTrue();
        assertThat(jt.get("mainClass").get("teacherName").asText()).isEqualTo("Prof. X");
        assertThat(jt.get("mainClass").get("schedule")).hasSize(2);
        assertThat(jt.get("mainClass").get("schedule").get(0).get("weekday").asInt()).isEqualTo(1);
        assertThat(jt.get("mainClass").get("schedule").get(0).get("startsAt").asText()).isEqualTo("19:00");
        assertThat(jt.get("otherClasses")).hasSize(1);
        assertThat(jt.get("otherClasses").get(0).get("schedule").get(0).get("weekday").asInt()).isEqualTo(5);

        JsonNode plan = null;
        for (JsonNode pl : i.get("plans")) if (pl.get("modality").get("id").asText().equals(jm.toString())) plan = pl;
        assertThat(plan.get("planCents").asLong()).isEqualTo(20000);
        assertThat(plan.get("benefitCents").asLong()).isEqualTo(2000);
        assertThat(plan.get("monthlyCents").asLong()).isEqualTo(18000);
        assertThat(plan.get("benefit").get("type").asText()).isEqualTo("PERCENT");
        assertThat(plan.get("dueDay").asInt()).isEqualTo(5);
        assertThat(plan.get("nextPlan").get("valueCents").asLong()).isEqualTo(25000);
        adminJdbc().update("update plan set value_cents = 22000 where id = ?", jiu.plan());
        assertThat(info(t, acc, Role.STUDENT, "").get("plans").toString()).contains("\"monthlyCents\":19800");

        JsonNode g = null;
        for (JsonNode x : i.get("graduation")) if (x.get("modality").get("id").asText().equals(jm.toString())) g = x;
        assertThat(g.get("belt").get("degree").asInt()).isEqualTo(2);
        assertThat(g.get("history")).hasSize(3);
        assertThat(g.get("history").get(0).get("degree").asInt()).isEqualTo(2);
        assertThat(g.get("history").get(2).get("origin").asBoolean()).isTrue();
        for (JsonNode x : i.get("graduation")) if (!x.get("modality").get("id").asText().equals(jm.toString())) {
            assertThat(x.get("belt").isNull()).isTrue();
            assertThat(x.get("history")).isEmpty();
        }

        assertThat(i.get("emailAnnouncements").asBoolean()).isFalse();
        assertThat(i.get("terms").get("enrollment").get("version").asInt()).isEqualTo(1);
        assertThat(i.get("terms").get("imageUse").get("granted").asBoolean()).isTrue();
        assertThat(i.get("terms").get("imageUse").get("version").asInt()).isEqualTo(1);

        assertThat(put(session(t, acc, Role.STUDENT), s, "{\"granted\":false}").getStatus()).isEqualTo(200);
        assertThat(info(t, acc, Role.STUDENT, "").get("terms").get("imageUse").get("granted").asBoolean()).isFalse();

        String all = get(session(t, acc, Role.STUDENT), "/info").getContentAsString().toLowerCase();
        assertThat(all).doesNotContain("prof@portal.example", "11999990000", "52998224725", "attendance", "frequency", "presen", "progress", "proof");
    }

    @Test
    @DisplayName("CA1: responsável com dois filhos vê os dois, padrão o primeiro cartão; o não pagador também vê")
    void family() throws Exception {
        Tenant t = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null);
        guardian(t, acc, "52998224725", false, a, b);
        enroll(t, a, "ACTIVE", 0);
        enroll(t, b, "ACTIVE", 1);
        JsonNode first = info(t, acc, Role.GUARDIAN, "");
        assertThat(first.get("students")).hasSize(2);
        assertThat(first.get("selectedStudentId").asText()).isEqualTo(a.toString());
        assertThat(first.get("personal").get("name").asText()).isEqualTo("Filho A");
        assertThat(first.get("personal").get("maskedCpf").isNull()).isTrue();
        assertThat(info(t, acc, Role.GUARDIAN, "?studentId=" + b).get("personal").get("name").asText()).isEqualTo("Filho B");
    }

    @Test
    @DisplayName("CA8: benefício encerrado ou vencido não conta; a mensalidade é a cheia")
    void benefitNotCounted() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        adminJdbc().update("update plan set value_cents = 20000 where id = ?", e.plan());
        benefit(t, s, e.row(), "PERCENT", 10, null, "ENDED");
        JsonNode ended = info(t, acc, Role.STUDENT, "").get("plans").get(0);
        assertThat(ended.get("monthlyCents").asLong()).isEqualTo(20000);
        assertThat(ended.get("benefit").isNull()).isTrue();
        benefit(t, s, e.row(), "AMOUNT", 5000, "2020-01-01", "ACTIVE");
        JsonNode expired = info(t, acc, Role.STUDENT, "").get("plans").get(0);
        assertThat(expired.get("monthlyCents").asLong()).isEqualTo(20000);
        assertThat(expired.get("benefitCents").asLong()).isZero();
    }

    @Test
    @DisplayName("CA14: portal reduzido traz dados, contato da unidade de cadastro, termos e preferência; sem treinos, plano nem graduação")
    void reduced() throws Exception {
        Tenant t = tenant();
        UUID acc = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", acc);
        var e = enroll(t, s, "ENDED", 0);
        graduation(t, s, modalityOf(e.row()), level(t, modalityOf(e.row()), 0, "Azul"), 1, false, "2025-03-10", "10 days");
        JsonNode i = info(t, acc, Role.STUDENT, "");
        assertThat(i.get("reduced").asBoolean()).isTrue();
        assertThat(i.get("trainings")).isEmpty();
        assertThat(i.get("plans")).isEmpty();
        assertThat(i.get("graduation")).isEmpty();
        assertThat(i.get("personal").get("name").asText()).isEqualTo("Ex Aluno");
        assertThat(i.get("unitContacts")).hasSize(1);
        assertThat(i.get("unitContacts").get(0).get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(i.get("terms").get("enrollment").get("version").asInt()).isEqualTo(1);
        assertThat(i.get("emailAnnouncements").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("CA2, CA18: studentId de outra conta, de outra organização ou inexistente é 404 igual e sem dado")
    void isolationOtherAccountAndOrganization() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x), sy = student(a, "Aluno Y", "1990-01-01", y), sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(a, sx, "ACTIVE", 0);
        enroll(a, sy, "ACTIVE", 0);
        enroll(b, sb, "ACTIVE", 0);
        var other = get(session(a, x, Role.STUDENT), "/info?studentId=" + sy);
        var foreign = get(session(a, x, Role.STUDENT), "/info?studentId=" + sb);
        var missing = get(session(a, x, Role.STUDENT), "/info?studentId=" + UUID.randomUUID());
        assertThat(other.getStatus()).isEqualTo(404);
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(missing.getStatus()).isEqualTo(404);
        assertThat(body(other).get("code").asText()).isEqualTo(body(foreign).get("code").asText()).isEqualTo(body(missing).get("code").asText());
        assertThat(other.getContentAsString() + foreign.getContentAsString()).doesNotContain("Aluno Y", "Aluno B");
        assertThat(info(a, x, Role.STUDENT, "").get("personal").get("name").asText()).isEqualTo("Aluno X");
        // a conta da organização B, com o aluno da A
        var crossed = get(session(b, x, Role.STUDENT), "/info?studentId=" + sx);
        assertThat(crossed.getStatus()).isEqualTo(404);
        assertThat(crossed.getContentAsString()).doesNotContain("Aluno X");
    }

    @Test
    @DisplayName("CA2, CA18: gestão e Professor 403; sem sessão 401; termo pendente 403 TERM_PENDING")
    void isolationProfilesAndTerms() throws Exception {
        Tenant a = tenant();
        UUID x = account("x@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x);
        enroll(a, sx, "ACTIVE", 0);
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var r = get(portal(a, UUID.randomUUID(), role), "/info");
            assertThat(r.getStatus()).isEqualTo(403);
            assertThat(body(r).get("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(get(null, "/info").getStatus()).isEqualTo(401);
        adminJdbc().update("delete from term_acceptance where student_id = ?", sx);
        publish(a, "ENROLLMENT", 1, true);
        var blocked = get(session(a, x, Role.STUDENT), "/info");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }

    @Test
    @DisplayName("CA2: RLS - no contexto da organização A a consulta não devolve linha da B")
    void isolationRls() {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example");
        student(a, "Aluno X", "1990-01-01", x);
        UUID sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        try (var ignored = OrganizationContext.open(session(a, x, Role.STUDENT))) {
            assertThat(transactions.execute(() -> jdbc.sql("select count(*) from student where id = :id").param("id", sb).query(Integer.class).single())).isZero();
            assertThat(transactions.execute(() -> jdbc.sql("select count(*) from student").query(Integer.class).single())).isEqualTo(1);
        }
    }
}
