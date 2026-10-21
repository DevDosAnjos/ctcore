package br.com.ctcore.student;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Matrícula direta no banco para os testes de leitura (alcance, contagens, ficha do Professor); sem passar pela API. */
final class EnrollmentFixtures {
    record Enrolled(UUID row, UUID klass, UUID plan, UUID teacher) {}

    private EnrollmentFixtures() {}

    static UUID teacher(JdbcTemplate db, UUID org, UUID account) {
        UUID id = UUID.randomUUID();
        db.update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, 'Professor', 'professor', ?)", id, org, account);
        return id;
    }

    /** Turma ativa da modalidade na unidade, com o professor titular. */
    static UUID klass(JdbcTemplate db, UUID org, UUID unit, UUID modality, UUID teacher) {
        UUID id = UUID.randomUUID();
        db.update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", org, unit, modality);
        db.update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                id, org, unit, "T" + id, "t" + id, modality, teacher);
        return id;
    }

    static UUID modality(JdbcTemplate db, UUID org, int kind) {
        UUID id = db.queryForList("select id from modality order by name", UUID.class).get(kind);
        db.update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", org, id);
        return id;
    }

    /** Modalidade (ativa ou encerrada) do aluno na unidade, numa turma e plano novos; {@code teacher} nulo cria um professor sem conta. */
    static Enrolled enroll(JdbcTemplate db, UUID org, UUID student, UUID unit, String status, int kind, UUID teacher) {
        UUID modality = modality(db, org, kind);
        UUID head = teacher != null ? teacher : teacher(db, org, null);
        UUID klass = klass(db, org, unit, modality, head);
        return enroll(db, org, student, unit, status, modality, klass, null, head);
    }

    /** Como o outro, mas na turma dada e com outro horário opcional. */
    static Enrolled enroll(JdbcTemplate db, UUID org, UUID student, UUID unit, String status, UUID modality, UUID klass, UUID other, UUID teacher) {
        UUID plan = UUID.randomUUID(), row = UUID.randomUUID();
        db.update("insert into plan (id, organization_id, unit_id, modality_id, name, name_key, value_cents, status) values (?, ?, ?, ?, ?, ?, 15000, 'ACTIVE')",
                plan, org, unit, modality, "P" + plan, "p" + plan);
        UUID enrollment = db.queryForList("select id from enrollment where student_id = ?", UUID.class, student).stream().findFirst().orElse(null);
        if (enrollment == null) {
            enrollment = UUID.randomUUID();
            db.update("insert into enrollment (id, organization_id, student_id, enrolled_at) values (?, ?, ?, now())", enrollment, org, student);
        }
        db.update("insert into enrollment_modality (id, organization_id, enrollment_id, student_id, unit_id, modality_id, primary_class_id, plan_id, due_day, "
                + "status, started_at, ended_at) values (?, ?, ?, ?, ?, ?, ?, ?, 5, ?, now(), case when ? = 'ENDED' then now() end)",
                row, org, enrollment, student, unit, modality, klass, plan, status, status);
        if (other != null) db.update("insert into enrollment_modality_class (organization_id, modality_row_id, class_id) values (?, ?, ?)", org, row, other);
        return new Enrolled(row, klass, plan, teacher);
    }

    static void clean(JdbcTemplate db) {
        for (String table : new String[] {"benefit", "enrollment_modality_class", "enrollment_modality", "enrollment", "student_guardian", "guardian", "student", "plan",
                "training_class_assistant", "training_class_schedule", "training_class", "teacher_unit", "teacher_modality", "teacher", "unit_modality",
                "organization_modality"}) {
            db.update("delete from " + table);
        }
    }
}
