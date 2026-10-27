package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-POR-02, RN-POR-04, RN-POR-05, RN-POR-09, RN-INA-08, RN-POR-01: Início do portal (cobrança, aulas, avisos, faixa, cartões), com banco real. */
class PortalHomeIT extends PortalTermBase {
    @AfterEach
    void cleanHome() {
        for (String table : new String[] {"charge", "lesson", "student_graduation", "graduation_level", "announcement_read", "announcement_class", "announcement"}) {
            adminJdbc().update("delete from " + table);
        }
    }

    private LocalDate today() {
        return adminJdbc().queryForObject("select (now() at time zone 'America/Sao_Paulo')::date", LocalDate.class);
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

    private UUID charge(Tenant t, UUID student, int dueInDays, long cents, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into charge (id, organization_id, unit_id, student_id, type, description, due_on, plan_cents, amount_cents, status) "
                + "values (?, ?, ?, ?, 'ONE_OFF', 'Kimono', ?, ?, ?, ?)", id, t.orgId(), t.unitId(), student, today().plusDays(dueInDays), cents, cents, status);
        return id;
    }

    private JsonNode home(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(portal(t, account, role), "/home" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private List<String> ids(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    @Test
    @DisplayName("CA1, CA2, CA3: cobranças que pedem ação, vencida primeiro; em conferência sem Pagar; longe só dá nextDueOn; paga e cancelada não entram")
    void charges() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        enroll(t, s, "ACTIVE", 0);
        UUID soon = charge(t, s, 2, 5000, "OPEN"), overdue = charge(t, s, -5, 15000, "OPEN"), review = charge(t, s, 20, 7000, "IN_REVIEW");
        charge(t, s, 10, 9000, "OPEN");
        charge(t, s, -2, 9000, "PAID");
        charge(t, s, -2, 9000, "CANCELED");

        JsonNode charges = home(t, acc, Role.STUDENT, "").get("charges");
        assertThat(ids(charges.get("items"))).containsExactly(overdue.toString(), soon.toString(), review.toString());
        assertThat(charges.get("items").get(0).get("situation").asText()).isEqualTo("OVERDUE");
        assertThat(charges.get("items").get(0).get("amountCents").asLong()).isEqualTo(15000);
        assertThat(charges.get("items").get(0).get("canPay").asBoolean()).isTrue();
        assertThat(charges.get("items").get(1).get("situation").asText()).isEqualTo("DUE_SOON");
        assertThat(charges.get("items").get(2).get("situation").asText()).isEqualTo("IN_REVIEW");
        assertThat(charges.get("items").get(2).get("canPay").asBoolean()).isFalse();
        assertThat(charges.get("nextDueOn").asText()).isEqualTo(today().plusDays(10).toString());

        adminJdbc().update("delete from charge where id in (?, ?, ?)", soon, overdue, review);
        JsonNode far = home(t, acc, Role.STUDENT, "").get("charges");
        assertThat(far.get("items")).isEmpty();
        assertThat(far.get("nextDueOn").asText()).isEqualTo(today().plusDays(10).toString());
        adminJdbc().update("delete from charge");
        assertThat(home(t, acc, Role.STUDENT, "").get("charges").get("nextDueOn").isNull()).isTrue();
    }

    @Test
    @DisplayName("CA10, CA11: responsável com dois filhos, total só do que paga; trocar de cartão troca o conteúdo; adulto sem filhos tem um cartão")
    void family() throws Exception {
        Tenant t = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID a = student(t, "Filho A", "2015-01-01", null), b = student(t, "Filho B", "2016-01-01", null), other = student(t, "Filho C", "2015-01-01", null);
        guardian(t, acc, "52998224725", true, a, b);
        guardian(t, account("outro@portal.example"), "11144477735", true, other);
        enroll(t, a, "ACTIVE", 0);
        enroll(t, b, "ACTIVE", 1);
        enroll(t, other, "ACTIVE", 0);
        charge(t, a, -1, 10000, "OPEN");
        UUID forB = charge(t, b, 1, 5000, "OPEN");
        charge(t, other, -1, 99900, "OPEN");

        JsonNode first = home(t, acc, Role.GUARDIAN, "");
        assertThat(first.get("students")).hasSize(2);
        assertThat(first.get("familyOpenCents").asLong()).isEqualTo(15000);
        assertThat(first.get("selectedStudentId").asText()).isEqualTo(a.toString());
        assertThat(first.get("students").get(0).get("openCents").asLong()).isEqualTo(10000);
        assertThat(first.get("charges").get("items")).hasSize(1);
        JsonNode second = home(t, acc, Role.GUARDIAN, "?studentId=" + b);
        assertThat(second.get("selectedStudentId").asText()).isEqualTo(b.toString());
        assertThat(ids(second.get("charges").get("items"))).containsExactly(forB.toString());

        UUID self = account("treina@portal.example");
        UUID mine = student(t, "Responsável Que Treina", "1985-01-01", self);
        guardian(t, self, "39053344705", true, student(t, "Filho D", "2015-01-01", null));
        enroll(t, mine, "ACTIVE", 0);
        JsonNode both = home(t, self, Role.GUARDIAN, "");
        assertThat(both.get("students").get(0).get("self").asBoolean()).isTrue();
        assertThat(both.get("selectedStudentId").asText()).isEqualTo(mine.toString());

        UUID alone = account("sozinho@portal.example");
        enroll(t, student(t, "Aluno Sozinho", "1990-01-01", alone), "ACTIVE", 0);
        assertThat(home(t, alone, Role.STUDENT, "").get("students")).hasSize(1);
    }

    @Test
    @DisplayName("CA12: portal reduzido traz só a cobrança, com Pagar")
    void reduced() throws Exception {
        Tenant t = tenant();
        UUID acc = account("ex@portal.example");
        UUID s = student(t, "Ex Aluno", "1990-01-01", acc);
        enroll(t, s, "ENDED", 0);
        charge(t, s, -3, 12000, "OPEN");
        announcement(t, "ORGANIZATION", null, null, null, null);
        JsonNode h = home(t, acc, Role.STUDENT, "");
        assertThat(h.get("reduced").asBoolean()).isTrue();
        assertThat(h.get("charges").get("items")).hasSize(1);
        assertThat(h.get("charges").get("items").get(0).get("canPay").asBoolean()).isTrue();
        assertThat(h.get("lessons")).isEmpty();
        assertThat(h.get("announcements").get("items")).isEmpty();
        assertThat(h.get("belts")).isEmpty();
    }

    @Test
    @DisplayName("CA13, CA14: studentId de outra conta ou organização é 404; perfis de gestão 403; sem sessão 401; termo pendente 403; não financeiro não vê a cobrança")
    void access() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID x = account("x@portal.example"), y = account("y@portal.example");
        UUID sx = student(a, "Aluno X", "1990-01-01", x), sy = student(a, "Aluno Y", "1990-01-01", y), sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(a, sx, "ACTIVE", 0);
        enroll(a, sy, "ACTIVE", 0);
        enroll(b, sb, "ACTIVE", 0);
        charge(a, sy, -1, 100, "OPEN");
        charge(b, sb, -1, 100, "OPEN");
        assertThat(get(portal(a, x, Role.STUDENT), "/home?studentId=" + sy).getStatus()).isEqualTo(404);
        assertThat(get(portal(a, x, Role.STUDENT), "/home?studentId=" + sb).getStatus()).isEqualTo(404);
        JsonNode own = home(a, x, Role.STUDENT, "");
        assertThat(own.get("charges").get("items")).isEmpty();
        assertThat(own.get("familyOpenCents").asLong()).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from charge", Integer.class)).isEqualTo(2);

        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            var r = get(portal(a, UUID.randomUUID(), role), "/home");
            assertThat(r.getStatus()).isEqualTo(403);
            assertThat(body(r).get("code").asText()).isEqualTo("FORBIDDEN");
        }
        assertThat(get(null, "/home").getStatus()).isEqualTo(401);

        UUID parent = account("pai@portal.example");
        UUID minor = student(a, "Menor", "2015-01-01", null);
        guardian(a, parent, "52998224725", false, minor);
        enroll(a, minor, "ACTIVE", 0);
        charge(a, minor, -1, 8000, "OPEN");
        JsonNode view = home(a, parent, Role.GUARDIAN, "");
        assertThat(view.get("charges").get("items")).isEmpty();
        assertThat(view.get("familyOpenCents").asLong()).isZero();
        assertThat(view.get("students").get(0).get("openCents").asLong()).isZero();

        adminJdbc().update("delete from term_acceptance where student_id = ?", sx);
        publish(a, "ENROLLMENT", 1, true);
        var blocked = get(portal(a, x, Role.STUDENT), "/home");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }

    private UUID lesson(Tenant t, UUID klass, UUID teacher, int dayOffset, String start, String end, boolean cancelled, boolean substitute) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id, substitute, cancelled, cancel_reason) "
                + "values (?, ?, ?, ?, ?, ?::time, ?::time, ?, ?, ?, ?)", id, t.orgId(), t.unitId(), klass, today().plusDays(dayOffset), start, end, teacher, substitute, cancelled,
                cancelled ? "Feriado" : null);
        return id;
    }

    private UUID adhoc(Tenant t, UUID teacher, UUID modality, UUID visibleTo, int dayOffset) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, lesson_date, starts_at, ends_at, teacher_id, title, modality_id, visibility_class_id) "
                + "values (?, ?, ?, ?, '10:00', '11:00', ?, 'Seminário', ?, ?)", id, t.orgId(), t.unitId(), today().plusDays(dayOffset), teacher, modality, visibleTo);
        return id;
    }

    @Test
    @DisplayName("CA4, CA5: as 3 próximas aulas ainda não terminadas, cancelada e alterada marcadas; avulsa só para a turma ou unidade; fuso da unidade")
    void lessons() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example"), other = account("outro@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc), o = student(t, "Aluno Dois", "1990-01-01", other);
        var e = enroll(t, s, "ACTIVE", 0);
        var oe = enroll(t, o, "ACTIVE", 0);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID second = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());
        adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", t.orgId(), e.row(), second);

        lesson(t, e.klass(), e.teacher(), -1, "18:00", "19:00", false, false); // terminada
        UUID cancelled = lesson(t, e.klass(), e.teacher(), 1, "18:00", "19:00", true, false);
        UUID changed = lesson(t, second, e.teacher(), 2, "19:00", "20:00", false, true);
        UUID plain = lesson(t, e.klass(), e.teacher(), 3, "18:00", "19:00", false, false);
        lesson(t, e.klass(), e.teacher(), 4, "18:00", "19:00", false, false); // a quarta não entra
        lesson(t, oe.klass(), oe.teacher(), 1, "08:00", "09:00", false, false); // turma de outro aluno

        JsonNode list = home(t, acc, Role.STUDENT, "").get("lessons");
        assertThat(ids(list)).containsExactly(cancelled.toString(), changed.toString(), plain.toString());
        assertThat(list.get(0).get("status").asText()).isEqualTo("CANCELLED");
        assertThat(list.get(0).get("cancelReason").asText()).isEqualTo("Feriado");
        assertThat(list.get(1).get("changed").asBoolean()).isTrue();
        assertThat(list.get(2).get("changed").asBoolean()).isFalse();
        assertThat(list.get(0).get("title").asText()).startsWith("T");
        assertThat(list.get(0).get("modality").get("id").asText()).isEqualTo(modality.toString());

        adminJdbc().update("delete from lesson");
        UUID forClass = adhoc(t, e.teacher(), modality, e.klass(), 1), forAll = adhoc(t, e.teacher(), modality, null, 2);
        assertThat(ids(home(t, acc, Role.STUDENT, "").get("lessons"))).containsExactly(forClass.toString(), forAll.toString());
        JsonNode theirs = home(t, other, Role.STUDENT, "").get("lessons");
        assertThat(ids(theirs)).containsExactly(forAll.toString());
        assertThat(theirs.get(0).get("adhoc").asBoolean()).isTrue();
        assertThat(theirs.get(0).get("title").asText()).isEqualTo("Seminário");

        // Fuso da unidade: aula de hoje (data da unidade) que já terminou não entra, mesmo com a data da JVM (UTC) diferente.
        adminJdbc().update("delete from lesson");
        adminJdbc().update("update unit set timezone = 'Pacific/Kiritimati' where id = ?", t.unitId());
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id) "
                + "values (?, ?, ?, ?, (now() at time zone 'Pacific/Kiritimati')::date, '00:00', '00:01', ?)", UUID.randomUUID(), t.orgId(), t.unitId(), e.klass(), e.teacher());
        assertThat(home(t, acc, Role.STUDENT, "").get("lessons")).isEmpty();
    }

    private UUID announcement(Tenant t, String kind, UUID unit, UUID klass, UUID modality, LocalDate pinnedUntil) {
        return announcement(t, kind, unit, klass, modality, pinnedUntil, true);
    }

    private UUID announcement(Tenant t, String kind, UUID unit, UUID klass, UUID modality, LocalDate pinnedUntil, boolean sent) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, class_id, modality_id, type, title, body, scheduled_for, sent_at, recipient_count, "
                + "author_account_id, pinned_until) values (?, ?, ?, ?, ?, ?, 'GENERAL', ?, 'Corpo fictício', ?::timestamptz, ?::timestamptz, ?, ?, ?)", id, t.orgId(), unit, kind, klass,
                modality, "Aviso " + id, sent ? null : "2099-01-01T12:00:00Z", sent ? "2026-01-01T12:00:00Z" : null, sent ? 3 : null, author(), pinnedUntil);
        return id;
    }

    @Test
    @DisplayName("CA6: só o não lido e o fixado, fixado primeiro; lido sem fixar, agendado e de turma alheia ficam de fora; unreadCount conta os não lidos")
    void announcements() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID foreign = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());

        UUID unread = announcement(t, "CLASS", t.unitId(), e.klass(), null, null);
        UUID pinned = announcement(t, "ORGANIZATION", null, null, null, today().plusDays(1));
        UUID readPlain = announcement(t, "ORGANIZATION", null, null, null, null);
        UUID expired = announcement(t, "ORGANIZATION", null, null, null, today().minusDays(1));
        announcement(t, "ORGANIZATION", null, null, null, null, false);
        announcement(t, "CLASS", t.unitId(), foreign, null, null);
        for (UUID read : new UUID[] {pinned, readPlain, expired}) {
            adminJdbc().update("insert into announcement_read (organization_id, announcement_id, student_id, account_id) values (?, ?, ?, ?)", t.orgId(), read, s, acc);
        }

        JsonNode list = home(t, acc, Role.STUDENT, "").get("announcements");
        assertThat(ids(list.get("items"))).containsExactly(pinned.toString(), unread.toString());
        assertThat(list.get("unreadCount").asInt()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("pinned").asBoolean()).isTrue();
        assertThat(list.get("items").get(0).get("unread").asBoolean()).isFalse();
        assertThat(list.get("items").get(1).get("unread").asBoolean()).isTrue();
    }

    private void graduation(Tenant t, UUID student, UUID modality, UUID level, int degree, boolean origin, String createdAgo, String achieved) {
        adminJdbc().update("insert into student_graduation (id, organization_id, student_id, modality_id, level_id, degree, achieved_on, origin, has_proof, registered_by_account_id, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?::date, ?, false, ?, now() - ?::interval)", UUID.randomUUID(), t.orgId(), student, modality, level, degree, achieved, origin, author(), createdAgo);
    }

    private UUID level(Tenant t, UUID modality, int position, String name) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into graduation_level (id, organization_id, modality_id, position, name, color, secondary_color, max_degree, kids) values (?, ?, ?, ?, ?, '#112233', '#445566', 4, false)",
                id, t.orgId(), modality, position, name);
        return id;
    }

    @Test
    @DisplayName("CA7, CA9: uma faixa por modalidade, a mais recente; Nova só se não é de origem e tem até 14 dias; nada de presença nem progresso")
    void belts() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var jiu = enroll(t, s, "ACTIVE", 0);
        var judo = enroll(t, s, "ACTIVE", 1);
        UUID jm = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, jiu.row());
        UUID dm = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, judo.row());
        UUID white = level(t, jm, 0, "Branca"), blue = level(t, jm, 1, "Azul"), yellow = level(t, dm, 0, "Amarela");
        graduation(t, s, jm, white, 0, true, "100 days", null);
        graduation(t, s, jm, blue, 2, false, "3 days", today().minusDays(3).toString());
        graduation(t, s, dm, yellow, 0, true, "1 day", null);

        JsonNode belts = home(t, acc, Role.STUDENT, "").get("belts");
        assertThat(belts).hasSize(2);
        JsonNode jiuBelt = belts.get(0).get("modality").get("id").asText().equals(jm.toString()) ? belts.get(0) : belts.get(1);
        JsonNode judoBelt = jiuBelt == belts.get(0) ? belts.get(1) : belts.get(0);
        assertThat(jiuBelt.get("levelName").asText()).isEqualTo("Azul");
        assertThat(jiuBelt.get("degree").asInt()).isEqualTo(2);
        assertThat(jiuBelt.get("isNew").asBoolean()).isTrue();
        assertThat(judoBelt.get("levelName").asText()).isEqualTo("Amarela");
        assertThat(judoBelt.get("isNew").asBoolean()).isFalse();

        adminJdbc().update("update student_graduation set created_at = now() - interval '20 days' where level_id = ?", blue);
        assertThat(home(t, acc, Role.STUDENT, "").get("belts").toString()).contains("\"isNew\":false").doesNotContain("\"isNew\":true");

        String all = get(portal(t, acc, Role.STUDENT), "/home").getContentAsString().toLowerCase();
        assertThat(all).doesNotContain("attendance", "frequency", "presen", "progress");
    }
}
