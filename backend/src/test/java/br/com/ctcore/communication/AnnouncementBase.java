package br.com.ctcore.communication;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.identity.Role;
import br.com.ctcore.modality.Modalities;
import br.com.ctcore.organization.OrganizationBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Dados sintéticos de comunicado, turma, professor e leitura direto no banco (RF-COM-01, #81). */
abstract class AnnouncementBase extends OrganizationBase {
    @Autowired protected ObjectMapper json;

    @AfterEach
    protected void cleanAnnouncements() {
        for (String table : new String[] {"announcement_read", "announcement", "no_class_day_class", "no_class_day"}) adminJdbc().update("delete from " + table);
        br.com.ctcore.student.EnrollmentFixtures.clean(adminJdbc());
        for (String table : new String[] {"training_class_assistant", "training_class_schedule", "training_class", "teacher_unit",
                "teacher_modality", "teacher", "student_guardian", "guardian", "student", "unit_modality", "organization_modality"}) {
            adminJdbc().update("delete from " + table);
        }
        adminJdbc().update("delete from email_message");
        adminJdbc().update("delete from account_link where account_id in (select id from account where email like 'ann-%@example.com' or email like 'gen-%@example.com')");
        adminJdbc().update("delete from account where email like 'ann-%@example.com' or email like 'gen-%@example.com'");
    }

    protected AccountPrincipal account(Tenant t, Role role) {
        AccountPrincipal p = who(t, role);
        adminJdbc().update("insert into account (id, email, name, status) values (?, ?, 'Autora Sintética', 'ACTIVE')", p.accountId(), "ann-" + p.accountId() + "@example.com");
        return p;
    }

    /** Principal com unidades próprias (ou nenhuma). */
    protected AccountPrincipal accountIn(Tenant t, Role role, UUID... units) {
        AccountPrincipal p = account(t, role);
        return new AccountPrincipal(p.accountId(), p.sessionId(), p.activeLinkId(), p.organizationId(), role, Set.of(units));
    }

    protected UUID secondUnit(Tenant t) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into unit (id, organization_id, name, short_name, active) values (?, ?, 'Segunda', ?, true)", id, t.orgId(), "u" + id.toString().substring(0, 12));
        adminJdbc().update("insert into unit_settings (organization_id, unit_id) values (?, ?) on conflict do nothing", t.orgId(), id);
        return id;
    }

    protected UUID modality(Tenant t) {
        UUID id = adminJdbc().queryForObject("select id from modality where name = 'Jiu-Jitsu'", UUID.class);
        adminJdbc().update("insert into organization_modality (organization_id, modality_id, active) values (?, ?, true) on conflict do nothing", t.orgId(), id);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), t.unitId(), id);
        return id;
    }

    protected UUID teacher(Tenant t, AccountPrincipal account) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into teacher (id, organization_id, name, name_key, account_id) values (?, ?, ?, ?, ?)", id, t.orgId(), "Prof " + id,
                Modalities.normalize("prof " + id), account == null ? null : account.accountId());
        adminJdbc().update("insert into teacher_unit (organization_id, teacher_id, unit_id) values (?, ?, ?)", t.orgId(), id, t.unitId());
        return id;
    }

    protected UUID klass(Tenant t, String name, UUID head) {
        UUID modality = modality(t);
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                id, t.orgId(), t.unitId(), name, Modalities.normalize(name), modality, head);
        adminJdbc().update("insert into teacher_modality (organization_id, teacher_id, modality_id, level_id, degree) values (?, ?, ?, null, 0) on conflict do nothing", t.orgId(), head, modality);
        return id;
    }

    /** Turma da unidade dada (a modalidade passa a ser oferecida nela). */
    protected UUID klassIn(Tenant t, UUID unit, String name, UUID head, String status) {
        UUID id = UUID.randomUUID(), modality = modality(t);
        adminJdbc().update("insert into unit_modality (organization_id, unit_id, modality_id, offered) values (?, ?, ?, true) on conflict do nothing", t.orgId(), unit, modality);
        adminJdbc().update("insert into training_class (id, organization_id, unit_id, name, name_key, modality_id, head_teacher_id, status) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, t.orgId(), unit, name, Modalities.normalize(name), modality, head, status);
        return id;
    }

    /** Enviado em {@code sent} ou, se nulo, agendado para {@code scheduled}. CLASS leva a turma; UNIT leva a unidade; ORGANIZATION não leva nada. */
    protected UUID announcement(Tenant t, AccountPrincipal author, String kind, UUID unit, UUID klass, String type, String title, String scheduled, String sent) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into announcement (id, organization_id, unit_id, target_kind, class_id, type, title, body, scheduled_for, sent_at, recipient_count, author_account_id) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Corpo fictício', ?::timestamptz, ?::timestamptz, ?, ?)", id, t.orgId(), unit, kind, klass, type, title, scheduled, sent,
                sent == null ? null : 7, author.accountId());
        return id;
    }

    protected UUID sentToUnit(Tenant t, AccountPrincipal author, String title, String sent) {
        return announcement(t, author, "UNIT", t.unitId(), null, "GENERAL", title, null, sent);
    }

    protected UUID scheduledToUnit(Tenant t, AccountPrincipal author, String title) {
        return announcement(t, author, "UNIT", t.unitId(), null, "GENERAL", title, "2099-01-01T12:00:00Z", null);
    }

    protected MockHttpServletResponse send(HttpMethod method, String path, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.request(method, "/api/v1" + path), who).getResponse();
    }

    protected JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    protected java.util.List<String> titles(String query, AccountPrincipal who) throws Exception {
        java.util.List<String> result = new java.util.ArrayList<>();
        ok(send(HttpMethod.GET, "/announcements" + query, who), 200).get("items").forEach(i -> result.add(i.get("title").asText()));
        return result;
    }

    protected UUID student(Tenant t, String birth) {
        return BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno Sintetico", birth);
    }

    protected MockHttpServletResponse post(String path, Object body, AccountPrincipal who) throws Exception {
        return call(MockMvcRequestBuilders.post("/api/v1" + path).contentType("application/json").content(json.writeValueAsString(body)), who).getResponse();
    }

    /** Aluno adulto (ou menor, pelo nascimento) com matrícula na turma, com e-mail opcional. */
    protected UUID enrolled(Tenant t, UUID klass, UUID teacher, String birth, String email, String status) {
        UUID student = student(t, birth);
        if (email != null) adminJdbc().update("update student set email = ? where id = ?", email, student);
        br.com.ctcore.student.EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), student, t.unitId(), status, adminJdbc().queryForObject("select modality_id from training_class where id = ?", UUID.class, klass), klass, null, teacher);
        return student;
    }

    protected int count(String sql, Object... args) {
        return adminJdbc().queryForObject(sql, Integer.class, args);
    }
}
