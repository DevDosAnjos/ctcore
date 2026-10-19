package br.com.ctcore.agenda;

import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;

/** Dados sintéticos de turma e aula, com banco real, para os testes de #49. */
abstract class AgendaBase extends OrganizationBase {
    @AfterEach
    void clean() {
        adminJdbc().update("delete from lesson");
        adminJdbc().update("delete from no_class_day");
        adminJdbc().update("delete from training_class_assistant");
        adminJdbc().update("delete from training_class_schedule");
        adminJdbc().update("delete from training_class");
        adminJdbc().update("delete from teacher_unit");
        adminJdbc().update("delete from teacher");
        adminJdbc().update("delete from unit_modality");
        adminJdbc().update("delete from organization_modality");
        adminJdbc().update("delete from account where email like 'agenda-%'");
    }

    protected UUID modality(Tenant t, String name) {
        UUID id = adminJdbc().queryForObject("select id from modality where name = ?", UUID.class, name);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), id);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing",
                t.orgId(), t.unitId(), id);
        return id;
    }

    protected UUID teacher(Tenant t, String name, UUID accountId) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, ?, ?, ?)", id, t.orgId(), name,
                Modalities.normalize(name), accountId);
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, t.unitId());
        return id;
    }

    protected UUID account(String label) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", id, "agenda-" + label + "-" + id + "@example.com");
        return id;
    }

    protected UUID klass(Tenant t, String name, UUID modality, UUID head, String status) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), t.unitId(), name, Modalities.normalize(name), modality, head, status);
        return id;
    }

    protected void slot(Tenant t, UUID klass, int weekday, String from, String to) {
        adminJdbc().update("insert into training_class_schedule (organization_id, class_id, weekday, starts_at, ends_at) values (?, ?, ?, ?, ?)",
                t.orgId(), klass, weekday, LocalTime.parse(from), LocalTime.parse(to));
    }

    protected UUID lesson(Tenant t, UUID klass, UUID teacher, LocalDate date, String from, String to, boolean cancelled, boolean edited) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into lesson (id, organization_id, unit_id, class_id, lesson_date, starts_at, ends_at, teacher_id, cancelled, cancel_reason, manually_edited) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", id, t.orgId(), t.unitId(), klass, date, LocalTime.parse(from), LocalTime.parse(to), teacher,
                cancelled, cancelled ? "Motivo" : null, edited);
        return id;
    }

    protected void timezone(Tenant t, String zone) {
        adminJdbc().update("update unit set timezone = ? where id = ?", zone, t.unitId());
    }

    protected void allWeekdays(Tenant t, UUID klass) {
        for (int d = 1; d <= 7; d++) slot(t, klass, d, "19:00", "20:00");
    }
}
