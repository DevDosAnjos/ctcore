package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-POR-04, RN-POR-01, RN-POR-02, RN-POR-05, RN-POR-06, RN-POR-08, RN-AGE-03, RN-AGE-05: agenda do portal (tipos, escopo, avisos, dias sem aula, semana, fuso), com banco real. */
class PortalScheduleIT extends PortalTermBase {
    private static final String WEEK = "2026-11-02"; // segunda-feira

    @AfterEach
    void cleanSchedule() {
        for (String table : new String[] {"lesson", "no_class_day_class", "announcement_class", "announcement", "no_class_day"}) {
            adminJdbc().update("delete from " + table);
        }
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

    private UUID lesson(Tenant t, UUID klass, UUID teacher, String date, String start, String end) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id) values (?, ?, ?, ?, ?::date, ?::time, ?::time, ?)",
                id, t.orgId(), t.unitId(), klass, date, start, end, teacher);
        return id;
    }

    private UUID adhoc(Tenant t, UUID teacher, UUID modality, UUID visibleTo, String date) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, lesson_date, starts_at, ends_at, teacher_id, title, modality_id, visibility_class_id) "
                + "values (?, ?, ?, ?::date, '10:00', '11:00', ?, 'Seminário', ?, ?)", id, t.orgId(), t.unitId(), date, teacher, modality, visibleTo);
        return id;
    }

    private UUID notice(Tenant t, String type, UUID klass, boolean sent, String sentAt) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, class_id, type, title, body, scheduled_for, sent_at, recipient_count, author_account_id) "
                + "values (?, ?, ?, 'CLASS', ?, ?, 'Aviso', 'Corpo fictício', ?::timestamptz, ?::timestamptz, ?, ?)", id, t.orgId(), t.unitId(), klass, type,
                sent ? null : "2099-01-01T12:00:00Z", sent ? sentAt : null, sent ? 3 : null, author());
        return id;
    }

    private JsonNode schedule(Tenant t, UUID account, Role role, String query) throws Exception {
        var r = get(portal(t, account, role), "/schedule" + query);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private JsonNode byId(JsonNode lessons, UUID id) {
        for (JsonNode l : lessons) if (l.get("id").asText().equals(id.toString())) return l;
        throw new AssertionError("aula ausente: " + id);
    }

    private List<String> ids(JsonNode lessons) {
        List<String> out = new ArrayList<>();
        lessons.forEach(n -> out.add(n.get("id").asText()));
        return out;
    }

    @Test
    @DisplayName("CA1, CA2: principal, outro horário e avulsa vêm com o tipo; turma alheia e avulsa de outra turma não; scope=MAIN só a principal; scope inválido é 400")
    void kindsAndScope() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        var oe = enroll(t, student(t, "Aluno Dois", "1990-01-01", account("outro@portal.example")), "ACTIVE", 0);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID second = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());
        adminJdbc().update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", t.orgId(), e.row(), second);

        UUID main = lesson(t, e.klass(), e.teacher(), "2026-11-03", "19:00", "20:00");
        UUID other = lesson(t, second, e.teacher(), "2026-11-05", "19:00", "20:00");
        UUID open = adhoc(t, e.teacher(), modality, null, "2026-11-06");
        lesson(t, oe.klass(), oe.teacher(), "2026-11-03", "08:00", "09:00");
        adhoc(t, e.teacher(), modality, oe.klass(), "2026-11-07");
        lesson(t, e.klass(), e.teacher(), "2026-11-10", "19:00", "20:00"); // outra semana

        JsonNode all = schedule(t, acc, Role.STUDENT, "?week=" + WEEK);
        assertThat(ids(all.get("lessons"))).containsExactly(main.toString(), other.toString(), open.toString());
        assertThat(byId(all.get("lessons"), main).get("kind").asText()).isEqualTo("MAIN");
        assertThat(byId(all.get("lessons"), other).get("kind").asText()).isEqualTo("OTHER");
        assertThat(byId(all.get("lessons"), open).get("kind").asText()).isEqualTo("ADHOC");
        assertThat(byId(all.get("lessons"), open).get("title").asText()).isEqualTo("Seminário");
        assertThat(all.get("scope").asText()).isEqualTo("ALL");

        assertThat(ids(schedule(t, acc, Role.STUDENT, "?week=" + WEEK + "&scope=MAIN").get("lessons"))).containsExactly(main.toString());
        assertThat(get(portal(t, acc, Role.STUDENT), "/schedule?scope=NOPE").getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("CA3, CA4, CA5, CA12: cancelada com código e texto; alterada com origem e substituto; noticeId é o mais recente enviado do tipo e da turma; sem professor")
    void cancelledChangedNotice() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        var e = enroll(t, s, "ACTIVE", 0);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID otherClass = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());

        UUID holiday = lesson(t, e.klass(), e.teacher(), "2026-11-02", "19:00", "20:00");
        adminJdbc().update("update lesson set cancelled = true, cancel_reason_code = 'HOLIDAY' where id = ?", holiday);
        UUID text = lesson(t, e.klass(), e.teacher(), "2026-11-03", "19:00", "20:00");
        adminJdbc().update("update lesson set cancelled = true, cancel_reason_code = 'OTHER', cancel_reason = 'Reforma do tatame' where id = ?", text);
        UUID moved = lesson(t, e.klass(), e.teacher(), "2026-11-04", "20:00", "21:00");
        adminJdbc().update("update lesson set manually_edited = true, original_date = '2026-11-03', original_starts_at = '19:00' where id = ?", moved);
        UUID sub = lesson(t, e.klass(), e.teacher(), "2026-11-05", "19:00", "20:00");
        adminJdbc().update("update lesson set substitute = true where id = ?", sub);
        UUID plain = lesson(t, e.klass(), e.teacher(), "2026-11-06", "19:00", "20:00");

        notice(t, "SCHEDULE_CHANGE", e.klass(), true, "2026-10-01T12:00:00Z");
        UUID latest = notice(t, "TEACHER_CHANGE", e.klass(), true, "2026-10-02T12:00:00Z");
        notice(t, "GENERAL", e.klass(), true, "2026-10-03T12:00:00Z");
        notice(t, "SCHEDULE_CHANGE", e.klass(), false, null);
        notice(t, "SCHEDULE_CHANGE", otherClass, true, "2026-10-04T12:00:00Z");
        UUID canceled = notice(t, "CANCELED_CLASS", e.klass(), true, "2026-10-05T12:00:00Z");

        JsonNode list = schedule(t, acc, Role.STUDENT, "?week=" + WEEK).get("lessons");
        JsonNode h = byId(list, holiday), o = byId(list, text);
        assertThat(h.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(h.get("cancelReasonCode").asText()).isEqualTo("HOLIDAY");
        assertThat(h.get("cancelReason").isNull()).isTrue();
        assertThat(o.get("cancelReasonCode").asText()).isEqualTo("OTHER");
        assertThat(o.get("cancelReason").asText()).isEqualTo("Reforma do tatame");
        assertThat(h.get("noticeId").asText()).isEqualTo(canceled.toString());
        JsonNode m = byId(list, moved), b = byId(list, sub), p = byId(list, plain);
        assertThat(m.get("changed").asBoolean()).isTrue();
        assertThat(m.get("originalDate").asText()).isEqualTo("2026-11-03");
        assertThat(m.get("originalStartsAt").asText()).isEqualTo("19:00");
        assertThat(m.get("substitute").asBoolean()).isFalse();
        assertThat(m.get("noticeId").asText()).isEqualTo(latest.toString());
        assertThat(b.get("substitute").asBoolean()).isTrue();
        assertThat(b.get("noticeId").asText()).isEqualTo(latest.toString());
        assertThat(p.get("changed").asBoolean()).isFalse();
        assertThat(p.get("noticeId").isNull()).isTrue();

        adminJdbc().update("delete from announcement where type <> 'GENERAL'");
        assertThat(byId(schedule(t, acc, Role.STUDENT, "?week=" + WEEK).get("lessons"), moved).get("noticeId").isNull()).isTrue();

        String all = get(portal(t, acc, Role.STUDENT), "/schedule?week=" + WEEK).getContentAsString().toLowerCase();
        assertThat(all).doesNotContain("teacher", "professor", "attendance", "frequency", "presen", "progress", "waiting", "charge", "amount");
    }

    @Test
    @DisplayName("CA5: aula cancelada por dia sem aula leva o comunicado do dia")
    void noClassDayNotice() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        var e = enroll(t, student(t, "Aluno Um", "1990-01-01", acc), "ACTIVE", 0);
        UUID day = noClassDay(t, "2026-11-03", "2026-11-03", "Feriado municipal");
        UUID l = lesson(t, e.klass(), e.teacher(), "2026-11-03", "19:00", "20:00");
        adminJdbc().update("update lesson set cancelled = true, cancel_reason_code = 'HOLIDAY', no_class_day_id = ? where id = ?", day, l);
        UUID n = UUID.randomUUID();
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, type, title, body, sent_at, recipient_count, author_account_id, no_class_day_id) "
                + "values (?, ?, ?, 'UNIT', 'NO_CLASS_DAY', 'Sem aula', 'Corpo fictício', '2026-10-05T12:00:00Z', 3, ?, ?)", n, t.orgId(), t.unitId(), author(), day);
        assertThat(byId(schedule(t, acc, Role.STUDENT, "?week=" + WEEK).get("lessons"), l).get("noticeId").asText()).isEqualTo(n.toString());
    }

    private UUID noClassDay(Tenant t, String from, String to, String reason) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?::date, ?::date, ?)", id, t.orgId(), t.unitId(), from, to, reason);
        return id;
    }

    @Test
    @DisplayName("CA6: dia sem aula da unidade vem por dia coberto; o de uma turma que o aluno não frequenta não vem")
    void noClassDays() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        var e = enroll(t, student(t, "Aluno Um", "1990-01-01", acc), "ACTIVE", 0);
        UUID modality = adminJdbc().queryForObject("select modality_id from enrollment_modality where id = ?", UUID.class, e.row());
        UUID otherClass = EnrollmentFixtures.klass(adminJdbc(), t.orgId(), t.unitId(), modality, e.teacher());
        noClassDay(t, "2026-11-02", "2026-11-03", "Feriado");
        UUID only = noClassDay(t, "2026-11-04", "2026-11-04", "Só outra turma");
        adminJdbc().update("insert into no_class_day_class (organization_id, no_class_day_id, class_id) values (?, ?, ?)", t.orgId(), only, otherClass);
        UUID mine = noClassDay(t, "2026-11-05", "2026-11-05", "Minha turma");
        adminJdbc().update("insert into no_class_day_class (organization_id, no_class_day_id, class_id) values (?, ?, ?)", t.orgId(), mine, e.klass());
        noClassDay(t, "2026-11-20", "2026-11-20", "Outra semana");

        JsonNode days = schedule(t, acc, Role.STUDENT, "?week=" + WEEK).get("noClassDays");
        assertThat(days).hasSize(3);
        assertThat(days.get(0).get("date").asText()).isEqualTo("2026-11-02");
        assertThat(days.get(1).get("date").asText()).isEqualTo("2026-11-03");
        assertThat(days.get(1).get("reason").asText()).isEqualTo("Feriado");
        assertThat(days.get(1).get("unit").get("id").asText()).isEqualTo(t.unitId().toString());
        assertThat(days.get(2).get("reason").asText()).isEqualTo("Minha turma");
    }

    @Test
    @DisplayName("CA7, CA10: week vira a segunda; domingo e segunda caem em semanas diferentes; hoje e past valem pelo fuso da unidade; week inválida é 400")
    void weekAndZone() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        var e = enroll(t, student(t, "Aluno Um", "1990-01-01", acc), "ACTIVE", 0);
        UUID sunday = lesson(t, e.klass(), e.teacher(), "2026-11-08", "19:00", "20:00");
        UUID monday = lesson(t, e.klass(), e.teacher(), "2026-11-09", "19:00", "20:00");

        JsonNode wed = schedule(t, acc, Role.STUDENT, "?week=2026-11-04");
        assertThat(wed.get("weekStart").asText()).isEqualTo("2026-11-02");
        assertThat(wed.get("weekEnd").asText()).isEqualTo("2026-11-08");
        assertThat(ids(wed.get("lessons"))).containsExactly(sunday.toString());
        assertThat(ids(schedule(t, acc, Role.STUDENT, "?week=2026-11-09").get("lessons"))).containsExactly(monday.toString());
        assertThat(get(portal(t, acc, Role.STUDENT), "/schedule?week=ontem").getStatus()).isEqualTo(400);

        adminJdbc().update("delete from lesson");
        adminJdbc().update("update unit set timezone = 'Pacific/Kiritimati' where id = ?", t.unitId());
        LocalDate zoneToday = adminJdbc().queryForObject("select (now() at time zone 'Pacific/Kiritimati')::date", LocalDate.class);
        UUID done = lesson(t, e.klass(), e.teacher(), zoneToday.toString(), "00:00", "00:01");
        UUID ahead = lesson(t, e.klass(), e.teacher(), zoneToday.toString(), "23:58", "23:59");
        JsonNode now = schedule(t, acc, Role.STUDENT, "");
        assertThat(now.get("timezone").asText()).isEqualTo("Pacific/Kiritimati");
        assertThat(now.get("today").asText()).isEqualTo(zoneToday.toString());
        assertThat(now.get("weekStart").asText()).isEqualTo(zoneToday.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
        assertThat(byId(now.get("lessons"), done).get("past").asBoolean()).isTrue();
        assertThat(byId(now.get("lessons"), ahead).get("past").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("CA8, CA9, CA11: responsável com dois filhos; outra conta ou organização é 404; gestão 403; sem sessão 401; termo pendente 403; reduzido 200 vazio")
    void access() throws Exception {
        Tenant a = tenant(), b = tenant();
        UUID acc = account("responsavel@portal.example");
        UUID x = student(a, "Filho A", "2015-01-01", null), y = student(a, "Filho B", "2016-01-01", null), other = student(a, "Filho C", "2015-01-01", null);
        guardian(a, acc, "52998224725", false, x, y);
        guardian(a, account("outro@portal.example"), "11144477735", true, other);
        var ex = enroll(a, x, "ACTIVE", 0);
        var ey = enroll(a, y, "ACTIVE", 1);
        enroll(a, other, "ACTIVE", 0);
        UUID sb = student(b, "Aluno B", "1990-01-01", account("b@portal.example"));
        enroll(b, sb, "ACTIVE", 0);
        UUID lx = lesson(a, ex.klass(), ex.teacher(), "2026-11-03", "19:00", "20:00");
        UUID ly = lesson(a, ey.klass(), ey.teacher(), "2026-11-04", "19:00", "20:00");

        JsonNode first = schedule(a, acc, Role.GUARDIAN, "?week=" + WEEK);
        assertThat(first.get("students")).hasSize(2);
        assertThat(first.get("studentId").asText()).isEqualTo(x.toString());
        assertThat(ids(first.get("lessons"))).containsExactly(lx.toString());
        assertThat(ids(schedule(a, acc, Role.GUARDIAN, "?week=" + WEEK + "&studentId=" + y).get("lessons"))).containsExactly(ly.toString());

        var foreign = get(portal(a, acc, Role.GUARDIAN), "/schedule?studentId=" + other);
        var crossOrg = get(portal(a, acc, Role.GUARDIAN), "/schedule?studentId=" + sb);
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(crossOrg.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString() + crossOrg.getContentAsString()).doesNotContain("Filho C", "Aluno B");
        for (Role role : new Role[] {Role.ADMINISTRATOR, Role.COORDINATOR, Role.TEACHER}) {
            assertThat(get(portal(a, UUID.randomUUID(), role), "/schedule").getStatus()).isEqualTo(403);
        }
        assertThat(get(null, "/schedule").getStatus()).isEqualTo(401);

        UUID acc2 = account("ex@portal.example");
        enroll(a, student(a, "Ex Aluno", "1990-01-01", acc2), "ENDED", 0);
        JsonNode reduced = schedule(a, acc2, Role.STUDENT, "?week=" + WEEK);
        assertThat(reduced.get("reduced").asBoolean()).isTrue();
        assertThat(reduced.get("lessons")).isEmpty();
        assertThat(reduced.get("noClassDays")).isEmpty();

        UUID acc3 = account("pendente@portal.example");
        enroll(a, student(a, "Aluno Pendente", "1990-01-01", acc3), "ACTIVE", 0);
        adminJdbc().update("delete from term_acceptance");
        publish(a, "ENROLLMENT", 1, true);
        var blocked = get(portal(a, acc3, Role.STUDENT), "/schedule");
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(body(blocked).get("code").asText()).isEqualTo("TERM_PENDING");
    }
}
