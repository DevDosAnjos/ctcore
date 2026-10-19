package br.com.ctcore.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.identity.Role;
import br.com.ctcore.isolation.OrganizationTransactions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-AGE-01, RN-AGE-02: geração e regeneração de aulas, com banco real. */
class LessonGenerationIT extends AgendaBase {
    static final Instant NOW = Instant.parse("2026-03-10T15:00:00Z"); // terça, 12:00 em São Paulo
    static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);

    @Autowired LessonGenerator generator;
    @Autowired LessonGenerationJob job;
    @Autowired OrganizationTransactions transactions;
    @Autowired ObjectMapper json;

    private int generate(Tenant t, Instant now) {
        return transactions.provision(t.orgId(), () -> generator.forOrganization(t.orgId(), now, SYSTEM, "DAILY_JOB"));
    }

    private int lessons(UUID klass) {
        return adminJdbc().queryForObject("select count(*) from lesson where class_id = ?", Integer.class, klass);
    }

    private static int expected(LocalDate today, int... weekdays) {
        int n = 0;
        for (int i = 0; i < LessonGenerator.DAYS; i++) {
            for (int w : weekdays) if (today.plusDays(i).getDayOfWeek().getValue() == w) n++;
        }
        return n;
    }

    @Test
    @DisplayName("CA1, CA11: 56 dias por horário, só turma ativa, idempotente, dia seguinte só acrescenta o dia novo, auditoria só quando cria")
    void generates() {
        var t = tenant();
        UUID head = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        UUID active = klass(t, "Judô Adulto", judo, head, "ACTIVE");
        slot(t, active, 1, "19:00", "20:00");
        slot(t, active, 3, "19:00", "20:00");
        UUID off = klass(t, "Judô Antigo", judo, head, "INACTIVE");
        slot(t, off, 1, "19:00", "20:00");

        LocalDate today = LocalDate.of(2026, 3, 10);
        int created = generate(t, NOW);
        assertThat(created).isEqualTo(expected(today, 1, 3)).isEqualTo(16);
        assertThat(lessons(active)).isEqualTo(16);
        assertThat(lessons(off)).isZero();
        assertThat(adminJdbc().queryForObject("select min(lesson_date) from lesson where class_id = ?", LocalDate.class, active)).isEqualTo(LocalDate.of(2026, 3, 11));
        assertThat(adminJdbc().queryForObject("select max(lesson_date) from lesson where class_id = ?", LocalDate.class, active)).isEqualTo(LocalDate.of(2026, 5, 4));
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where teacher_id = ? and not cancelled and not substitute", Integer.class, head)).isEqualTo(16);

        assertThat(generate(t, NOW)).isZero();
        assertThat(lessons(active)).isEqualTo(16);

        // 11/03: a janela termina em 05/05 (terça), dia sem horário da turma: nada novo; em 12/03 entra 06/05 (quarta).
        assertThat(generate(t, NOW.plusSeconds(86400))).isZero();
        assertThat(generate(t, NOW.plusSeconds(2 * 86400))).isEqualTo(1);
        assertThat(lessons(active)).isEqualTo(17);

        // Um registro por execução que criou aulas; as que não criaram nada não gravam.
        List<Map<String, Object>> audits = adminJdbc().queryForList("select actor_type, after_data::text as after from audit_log where organization_id = ? and action = 'lesson.generated' order by occurred_at", t.orgId());
        assertThat(audits).hasSize(2);
        assertThat(audits.get(0).get("actor_type")).isEqualTo("SYSTEM");
        assertThat(audits.get(0).get("after").toString()).contains("\"created\": 16").contains("DAILY_JOB");
    }

    @Test
    @DisplayName("CA2, CA10, CA11: cada unidade usa o seu fuso, cada aula fica na organização certa, e o papel da aplicação só vê a sua")
    void timezonesAndIsolation() throws Exception {
        var a = tenant();
        var b = tenant();
        timezone(a, "Pacific/Kiritimati"); // UTC+14
        timezone(b, "Pacific/Pago_Pago"); // UTC-11
        UUID ca = klass(a, "Judô A", modality(a, "Judô"), teacher(a, "Ana", null), "ACTIVE");
        UUID cb = klass(b, "Judô B", modality(b, "Judô"), teacher(b, "Beto", null), "ACTIVE");
        allWeekdays(a, ca);
        allWeekdays(b, cb);

        assertThat(job.runOnce()).isGreaterThanOrEqualTo(112);
        assertThat(lessons(ca)).isEqualTo(56);
        assertThat(lessons(cb)).isEqualTo(56);
        LocalDate minA = adminJdbc().queryForObject("select min(lesson_date) from lesson where class_id = ?", LocalDate.class, ca);
        LocalDate minB = adminJdbc().queryForObject("select min(lesson_date) from lesson where class_id = ?", LocalDate.class, cb);
        assertThat(minA).isEqualTo(LocalDate.now(ZoneId.of("Pacific/Kiritimati")));
        assertThat(minB).isEqualTo(LocalDate.now(ZoneId.of("Pacific/Pago_Pago")));
        assertThat(minA).isAfter(minB);
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where organization_id = ? and class_id = ?", Integer.class, a.orgId(), cb)).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where action = 'lesson.generated' and organization_id in (?, ?)", Integer.class, a.orgId(), b.orgId())).isEqualTo(2);

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            for (String table : new String[] {"lesson", "no_class_day"}) {
                sql.execute("select set_config('app.organization_id', '', true)");
                try (var rows = sql.executeQuery("select 1 from " + table)) {
                    assertThat(rows.next()).as(table).isFalse();
                }
            }
            sql.execute("select set_config('app.organization_id', '" + a.orgId() + "', true)");
            try (var rows = sql.executeQuery("select count(distinct organization_id), min(organization_id::text) from lesson")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isEqualTo(a.orgId().toString());
            }
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> sql.executeUpdate("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (gen_random_uuid(), '"
                    + b.orgId() + "', '" + b.unitId() + "', current_date, current_date, 'x')"))).hasMessageContaining("row-level security");
            app.rollback();
            // A função da tarefa diária enxerga as organizações sem escopo (o dono ignora a RLS), e só devolve ids.
            sql.execute("select set_config('app.organization_id', '', true)");
            try (var rows = sql.executeQuery("select count(*) from active_organization_ids() o where o in ('" + a.orgId() + "', '" + b.orgId() + "')")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA3: dia sem aula nasce cancelada com o motivo do dia; as demais datas ficam agendadas")
    void noClassDay() {
        var t = tenant();
        UUID c = klass(t, "Judô", modality(t, "Judô"), teacher(t, "Ana", null), "ACTIVE");
        allWeekdays(t, c);
        adminJdbc().update("insert into no_class_day (id, organization_id, unit_id, starts_on, ends_on, reason) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), t.orgId(), t.unitId(), LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 16), "Feriado local");
        generate(t, NOW);
        assertThat(adminJdbc().queryForList("select lesson_date::text, cancel_reason from lesson where class_id = ? and cancelled order by lesson_date", c).stream().map(Object::toString).toList())
                .containsExactly("{lesson_date=2026-03-15, cancel_reason=Feriado local}", "{lesson_date=2026-03-16, cancel_reason=Feriado local}");
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where class_id = ? and not cancelled", Integer.class, c)).isEqualTo(54);
    }

    private JsonNode create(Tenant t, UUID head, UUID modality, int weekday) throws Exception {
        var r = call(MockMvcRequestBuilders.post("/api/v1/classes").contentType("application/json").content(json.writeValueAsString(body(t, head, modality, weekday))),
                who(t, Role.COORDINATOR)).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return json.readTree(r.getContentAsString());
    }

    @Test
    @DisplayName("CA4, CA5, CA11: criar turma gera as 8 semanas; mudar o horário refaz só as futuras agendadas; mesmo horário não mexe")
    void createAndRegenerate() throws Exception {
        var t = tenant();
        UUID head = teacher(t, "Ana", null);
        UUID judo = modality(t, "Judô");
        int weekday = LocalDate.now(ZoneId.of("UTC")).plusDays(3).getDayOfWeek().getValue(); // nunca é hoje, em nenhum fuso
        UUID id = UUID.fromString(create(t, head, judo, weekday).get("id").asText());
        assertThat(lessons(id)).isEqualTo(8);
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'lesson.generated' and entity_id = ?", Integer.class, t.orgId(), id.toString())).isEqualTo(1);

        List<UUID> future = adminJdbc().queryForList("select id from lesson where class_id = ? order by lesson_date", UUID.class, id);
        adminJdbc().update("update lesson set manually_edited = true where id = ?", future.get(2));
        adminJdbc().update("update lesson set cancelled = true, cancel_reason = 'Chuva' where id = ?", future.get(3));
        UUID past = lesson(t, id, head, LocalDate.now().minusDays(20), "19:00", "20:00", false, false);

        // PUT com os mesmos horários não mexe em nenhuma aula.
        var same = body(t, head, judo, weekday);
        var r = call(MockMvcRequestBuilders.put("/api/v1/classes/" + id).contentType("application/json").content(json.writeValueAsString(same)), who(t, Role.COORDINATOR)).getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(adminJdbc().queryForList("select id from lesson where class_id = ? and lesson_date > current_date order by lesson_date", UUID.class, id)).isEqualTo(future);

        int other = weekday % 7 + 1;
        r = call(MockMvcRequestBuilders.put("/api/v1/classes/" + id).contentType("application/json").content(json.writeValueAsString(body(t, head, judo, other))), who(t, Role.COORDINATOR)).getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where class_id = ? and extract(isodow from lesson_date) = ? and not cancelled and not manually_edited", Integer.class, id, weekday)).isZero();
        assertThat(adminJdbc().queryForList("select id from lesson where class_id = ? and extract(isodow from lesson_date) = ? and (cancelled or manually_edited)", UUID.class, id, weekday))
                .containsExactlyInAnyOrder(future.get(2), future.get(3));
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where class_id = ? and extract(isodow from lesson_date) = ?", Integer.class, id, other)).isEqualTo(8);
        assertThat(adminJdbc().queryForObject("select count(*) from lesson where id = ?", Integer.class, past)).isEqualTo(1);
        List<String> audit = adminJdbc().queryForList("select after_data::text from audit_log where organization_id = ? and action = 'lesson.regenerated'", String.class, t.orgId());
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0)).contains("\"deleted\": 6").contains("\"created\": 8");
    }

    private static Map<String, Object> body(Tenant t, UUID head, UUID modality, int weekday) {
        Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("weekday", weekday);
        slot.put("startsAt", "19:00");
        slot.put("endsAt", "20:00");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unitId", t.unitId());
        body.put("name", "Judô Novo");
        body.put("modalityId", modality);
        body.put("schedule", List.of(slot));
        body.put("headTeacherId", head);
        body.put("notifyClass", false);
        return body;
    }
}
