package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.ScopedAudit;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.organization.OrganizationBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-RSP-07: aviso dos 18 anos do aluno cadastrado menor, com relógio fixo e banco real. */
class MajorityNoticeJobIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired br.com.ctcore.notification.Notifications notifications;
    @Autowired EmailService emails;
    @Autowired ScopedAudit audit;

    final Clock clock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    @AfterEach
    void clean() {
        cleanLinked("majnot-");
        adminJdbc().update("delete from email_message where to_email like '%.example'");
        adminJdbc().update("delete from student_guardian");
        adminJdbc().update("delete from guardian");
        adminJdbc().update("delete from student");
        adminJdbc().update("delete from account where email like '%.example'");
    }

    private UUID student(Tenant t, String name, String birth, String createdAt) {
        UUID id = UUID.randomUUID();
        adminJdbc().update("insert into student (id, organization_id, registered_unit_id, name, name_key, birth_date, postal_code, street, number, district, city, state, created_at) "
                + "values (?, ?, ?, ?, ?, ?::date, '01001000', 'Rua', '1', 'Bairro', 'Cidade', 'SP', ?::timestamptz)", id, t.orgId(), t.unitId(), name, name.toLowerCase(), birth, createdAt);
        return id;
    }

    private void guardian(Tenant t, UUID student, String email, boolean financial, boolean problem) {
        UUID account = UUID.randomUUID(), guardian = UUID.randomUUID();
        adminJdbc().update("insert into account (id, email, status) values (?, ?, 'INVITED')", account, email);
        adminJdbc().update("insert into guardian (id, organization_id, account_id, name, cpf, phone, email_problem_at) values (?, ?, ?, 'Responsável', ?, '11987654321', ?)",
                guardian, t.orgId(), account, String.valueOf(10000000000L + Math.abs(email.hashCode()) % 1000000000L), problem ? java.sql.Timestamp.from(Instant.now()) : null);
        adminJdbc().update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, ?)", t.orgId(), student, guardian, financial);
    }

    @Test
    @DisplayName("CA16: avisa uma só vez o aluno cadastrado menor que fez 18 anos, manda e-mail aos responsáveis, audita e não muda o vínculo")
    void notifiesOnce() {
        Tenant t = tenant();
        UUID due = student(t, "Aluno Dezoito", "2008-06-15", "2026-01-01T00:00:00Z");
        UUID late = student(t, "Aluno Atrasado", "2008-06-01", "2026-02-01T00:00:00Z");
        UUID adultAtSignup = student(t, "Aluno Cadastrado Adulto", "2008-01-01", "2026-03-01T00:00:00Z");
        UUID tomorrow = student(t, "Aluno Amanha", "2008-06-16", "2026-01-01T00:00:00Z");
        guardian(t, due, "g1@majority.example", true, false);
        guardian(t, due, "g2@majority.example", false, true);
        guardian(t, late, "g3@majority.example", true, false);
        guardian(t, adultAtSignup, "g4@majority.example", true, false);
        guardian(t, tomorrow, "g5@majority.example", true, false);
        var job = new MajorityNoticeJob(transactions, jdbc, emails, audit, clock, notifications);

        assertThat(job.runOnce()).isEqualTo(2);
        assertThat(adminJdbc().queryForList("select id from student where majority_notice_at is not null", UUID.class)).containsExactlyInAnyOrder(due, late);
        assertThat(adminJdbc().queryForList("select to_email from email_message where template = 'STUDENT_MAJORITY' and to_email like '%majority.example'", String.class))
                .containsExactlyInAnyOrder("g1@majority.example", "g3@majority.example");
        assertThat(adminJdbc().queryForObject("select count(*) from audit_log where organization_id = ? and action = 'student.majority_notice' and actor_type = 'SYSTEM'",
                Long.class, t.orgId())).isEqualTo(2);
        assertThat(adminJdbc().queryForList("select financial from student_guardian where student_id = ? order by financial desc", Boolean.class, due)).containsExactly(true, false);

        assertThat(job.runOnce()).isZero();
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where template = 'STUDENT_MAJORITY' and to_email like '%majority.example'", Long.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("#84 CA5: o aviso dos 18 anos gera STUDENT_MAJORITY ao Administrador da unidade de cadastro, uma vez")
    void notifiesAdministrator() {
        Tenant t = tenant();
        var admin = linked(t.orgId(), br.com.ctcore.identity.Role.ADMINISTRATOR, "majnot-", t.unitId());
        UUID due = student(t, "Aluno Dezoito", "2008-06-15", "2026-01-01T00:00:00Z");
        var job = new MajorityNoticeJob(transactions, jdbc, emails, audit, clock, notifications);
        assertThat(job.runOnce()).isEqualTo(1);
        assertThat(job.runOnce()).isZero();
        var rows = adminJdbc().queryForList("select type, ref_type, ref_id, params::text as params from notification where link_id = ?", admin.activeLinkId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "STUDENT_MAJORITY").containsEntry("ref_type", "student").containsEntry("ref_id", due);
        assertThat((String) rows.get(0).get("params")).contains("Aluno Dezoito");
    }
}
