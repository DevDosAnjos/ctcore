package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.identity.Role;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** RN-MAT-16, RN-MAT-18 (#86): a tarefa diária expira o pedido de 30 dias e encerra a modalidade no 8º dia, pela data local da unidade, como sistema. */
class PortalRequestJobIT extends PortalRequestBase {
    @Autowired PortalRequestJob job;

    @AfterEach
    void cleanCharges() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from benefit");
    }

    private void age(String id, int days) {
        adminJdbc().update("update portal_request set created_at = now() - make_interval(days => ?) where id = ?::uuid", days, id);
    }

    private String status(String id) {
        return adminJdbc().queryForObject("select status from portal_request where id = ?::uuid", String.class, id);
    }

    private int mails(String to) {
        return adminJdbc().queryForObject("select count(*) from email_message where to_email = ? and template = 'PORTAL_REQUEST'", Integer.class, to);
    }

    @Test
    @DisplayName("CA9, CA15: expira só o pedido de 30 dias, avisa o aluno e não mexe na matrícula; rodar de novo não repete e-mail nem auditoria")
    void expires() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup a = setup(t, s, 0), b = setup(t, s, 1);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);
        String old = body(request(who, s, planChange(a))).get("id").asText();
        String recent = body(request(who, s, planChange(b))).get("id").asText();
        age(old, 30);
        age(recent, 29);

        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(status(old)).isEqualTo("EXPIRED");
        assertThat(status(recent)).isEqualTo("OPEN");
        assertThat(adminJdbc().queryForObject("select decided_at is not null and decided_by_account_id is null from portal_request where id = ?::uuid", Boolean.class, old)).isTrue();
        assertThat(adminJdbc().queryForObject("select plan_id::text || coalesce(next_plan_id::text, '') from enrollment_modality where id = ?", String.class, a.row()))
                .isEqualTo(a.plan().toString());
        assertThat(mails("aluno@portal.example")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select subject from email_message where to_email = 'aluno@portal.example'", String.class)).contains("expirou");
        assertThat(adminJdbc().queryForObject("select actor_type from audit_log where action = 'portal_request.expired' and entity_id = ?", String.class, old)).isEqualTo("SYSTEM");

        assertThat(job.runOnce()).isZero();
        assertThat(mails("aluno@portal.example")).isEqualTo(1);
        assertThat(auditsOf("portal_request.expired", old)).isEqualTo(1);
    }

    @Test
    @DisplayName("CA12, CA15: no 8º dia encerra a modalidade com a data do pedido, o benefício acaba, o que vence depois é cancelado e o anterior segue devido; com 7 dias nada acontece")
    void closesOnEighthDay() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        Setup a = setup(t, s, 0), b = setup(t, s, 1);
        accepted(t, s, acc);
        var who = portal(t, acc, Role.STUDENT);
        String due = body(request(who, s, closure(a, "SCHEDULE"))).get("id").asText();
        String notYet = body(request(who, s, closure(b, "OTHER"))).get("id").asText();
        age(due, 8);
        age(notYet, 7);
        LocalDate requested = adminJdbc().queryForObject("select (created_at at time zone 'America/Sao_Paulo')::date from portal_request where id = ?::uuid", LocalDate.class, due);
        UUID earlier = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), s, a.row(), LocalDate.parse("2026-09-01"), null, 15000, "OPEN");
        UUID later = BillingFixtures.charge(adminJdbc(), t.orgId(), t.unitId(), s, a.row(), LocalDate.parse("2026-10-01"), null, 15000, "OPEN");
        adminJdbc().update("update charge set due_on = ? where id = ?", requested.minusDays(3), earlier);
        adminJdbc().update("update charge set due_on = ? where id = ?", requested.plusDays(2), later);
        UUID benefit = BillingFixtures.benefit(adminJdbc(), t.orgId(), t.unitId(), s, a.row(), "PERCENT", 10, null, "ACTIVE");

        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(status(due)).isEqualTo("CLOSED");
        assertThat(status(notYet)).isEqualTo("OPEN");
        assertThat(adminJdbc().queryForObject("select status || ':' || end_reason || ':' || (ended_at = (select created_at from portal_request where id = ?::uuid)) from enrollment_modality where id = ?",
                String.class, due, a.row())).isEqualTo("ENDED:SCHEDULE:true");
        assertThat(adminJdbc().queryForObject("select status from enrollment_modality where id = ?", String.class, b.row())).isEqualTo("ACTIVE");
        assertThat(adminJdbc().queryForObject("select status from benefit where id = ?", String.class, benefit)).isEqualTo("ENDED");
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, later)).isEqualTo("CANCELED");
        assertThat(adminJdbc().queryForObject("select status from charge where id = ?", String.class, earlier)).isEqualTo("OPEN");
        assertThat(adminJdbc().queryForObject("select actor_type from audit_log where action = 'student.modality_ended' and organization_id = ?", String.class, t.orgId())).isEqualTo("SYSTEM");
        assertThat(auditsOf("portal_request.closed", due)).isEqualTo(1);
        assertThat(mails("aluno@portal.example")).isEqualTo(1);
        assertThat(adminJdbc().queryForObject("select subject from email_message where to_email = 'aluno@portal.example'", String.class)).contains("encerrada");

        assertThat(job.runOnce()).isZero();
        assertThat(mails("aluno@portal.example")).isEqualTo(1);
    }
}
