package br.com.ctcore.agenda;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.identity.ScopedAudit;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-AGE-01: gera 8 semanas (56 dias, contando hoje no fuso de cada unidade) de aulas, uma por horário semanal de turma ativa. Idempotente
 * (índice único) e nunca altera aula existente. Todos os métodos rodam dentro da transação da organização (escopo e RLS já abertos).
 */
@Component
public class LessonGenerator {
    public static final int DAYS = 56;

    private static final String INSERT = """
            insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id, cancelled, cancel_reason)
            select gen_random_uuid(), c.organization_id, c.unit_id, c.id, d.day, s.starts_at, s.ends_at, c.head_teacher_id,
                   n.reason is not null, n.reason
            from training_class c
            join training_class_schedule s on s.class_id = c.id and s.organization_id = c.organization_id
            cross join (select cast(g as date) as day from generate_series(cast(:from as date), cast(:to as date), interval '1 day') g) d
            left join lateral (select x.reason from no_class_day x where x.organization_id = c.organization_id and x.unit_id = c.unit_id
                               and d.day between x.starts_on and x.ends_on order by x.created_at, x.id limit 1) n on true
            where c.organization_id = :org and c.unit_id = :unit and c.status = 'ACTIVE'
              and (cast(:class as uuid) is null or c.id = :class) and extract(isodow from d.day) = s.weekday
              and not exists (select 1 from lesson e where e.class_id = c.id and e.original_date = d.day and e.original_starts_at = s.starts_at)
            on conflict do nothing""";

    private final JdbcClient jdbc;
    private final ScopedAudit audit;

    public LessonGenerator(@Lazy JdbcClient jdbc, ScopedAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** Todas as turmas ativas das unidades ativas da organização; grava {@code lesson.generated} só se criou aulas. */
    public int forOrganization(UUID org, Instant now, AuditActor actor, String origin) {
        int created = 0;
        for (Unit u : units(org, null)) created += insert(org, u, null, now);
        return record(org, null, created, actor, origin, null);
    }

    /** As aulas de uma turma (ao criá-la, R3); turma ou unidade inativa não gera nada. */
    public int forClass(UUID org, UUID classId, Instant now, AuditActor actor, String origin, String ip) {
        int created = 0;
        for (Unit u : units(org, classId)) created += insert(org, u, classId, now);
        return record(org, classId, created, actor, origin, ip);
    }

    /** Gera sem auditar (a regeneração audita por conta própria). */
    public int generateClass(UUID org, UUID classId, Instant now) {
        int created = 0;
        for (Unit u : units(org, classId)) created += insert(org, u, classId, now);
        return created;
    }

    record Unit(UUID id, ZoneId zone) {}

    private List<Unit> units(UUID org, UUID classId) {
        return jdbc.sql("select u.id, u.timezone from unit u where u.organization_id = :org and u.active "
                + "and (cast(:class as uuid) is null or u.id = (select unit_id from training_class where id = :class and organization_id = :org)) "
                + "order by u.id").param("org", org).param("class", classId)
                .query((rs, n) -> new Unit(rs.getObject(1, UUID.class), ZoneId.of(rs.getString(2)))).list();
    }

    private int insert(UUID org, Unit unit, UUID classId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, unit.zone());
        return jdbc.sql(INSERT).param("org", org).param("unit", unit.id()).param("class", classId)
                .param("from", today).param("to", today.plusDays(DAYS - 1)).update();
    }

    private int record(UUID org, UUID classId, int created, AuditActor actor, String origin, String ip) {
        if (created == 0) return 0;
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("created", created);
        after.put("origin", origin);
        audit.record(org, new AuditEntry(actor, "lesson.generated", classId == null ? "organization" : "training_class",
                (classId == null ? org : classId).toString(), null, null, after, null, ip));
        return created;
    }
}
