package br.com.ctcore.trainingclass;

import br.com.ctcore.agenda.LessonGenerator;
import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.isolation.OrganizationContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * RN-AGE-02: ponto único chamado quando o conjunto de horários da turma muda. Refaz só as aulas futuras (ainda não iniciadas, no fuso da
 * unidade) que estão agendadas, sem alteração manual e sem cancelamento; as demais ficam como estão. Roda na transação da edição.
 */
@Component
public class ClassScheduleChange {
    private final JdbcClient jdbc;
    private final LessonGenerator generator;
    private final AuditService audit;
    private final Clock clock;

    public ClassScheduleChange(@Lazy JdbcClient jdbc, LessonGenerator generator, AuditService audit, Clock clock) {
        this.jdbc = jdbc;
        this.generator = generator;
        this.audit = audit;
        this.clock = clock;
    }

    public void changed(UUID classId, List<TrainingClasses.Slot> previous, List<TrainingClasses.Slot> next, AuditActor actor, String ip) {
        UUID org = OrganizationContext.organizationId();
        Instant now = clock.instant();
        var unit = jdbc.sql("select u.id, u.timezone from training_class c join unit u on u.id = c.unit_id and u.organization_id = c.organization_id "
                + "where c.id = :id and c.organization_id = :org").param("id", classId).param("org", org)
                .query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getString(2))).single();
        LocalDateTime local = LocalDateTime.ofInstant(now, ZoneId.of(unit.getValue()));
        int deleted = jdbc.sql("delete from lesson where organization_id = :org and class_id = :id and not cancelled and not manually_edited "
                + "and (lesson_date + starts_at) > :now").param("org", org).param("id", classId).param("now", local).update();
        int created = generator.generateClass(org, classId, now);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("deleted", deleted);
        after.put("created", created);
        audit.record(new AuditEntry(actor, "lesson.regenerated", "training_class", classId.toString(), unit.getKey(), null, after, null, ip));
    }
}
