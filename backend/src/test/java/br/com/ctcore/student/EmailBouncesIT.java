package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.integration.EmailSender;
import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.SendResult;
import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-COM-14 e RN-ALU-10 (#78): devolução marca o endereço; falha de envio não; destinatário da cobrança. */
class EmailBouncesIT extends PostgresIT {
    @Autowired EmailService service;
    @Autowired EmailBounces bounces;
    @Autowired PayerEmail payer;
    @Autowired Outbox outbox;
    @Autowired JdbcClient jdbc;
    @Autowired MessageSource messages;

    JdbcTemplate admin = adminJdbc();
    UUID org = UUID.randomUUID();
    UUID unit = UUID.randomUUID();

    UUID setup() {
        admin.update("insert into organization (id, name) values (?, 'Org')", org);
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", unit, org, "u-" + org);
        return BillingFixtures.student(admin, org, unit, "Aluno Teste");
    }

    @AfterEach
    void clean() {
        admin.update("delete from email_message where to_email like '%.example'");
        admin.update("delete from student_guardian");
        admin.update("delete from guardian");
        admin.update("delete from student");
        admin.update("delete from account where email = 'resp@pagador.example'");
        admin.execute("alter table audit_log disable trigger user");
        admin.update("delete from audit_log where organization_id = ?", org);
        admin.execute("alter table audit_log enable trigger user");
        admin.update("delete from unit where organization_id = ?", org);
        admin.update("delete from organization where id = ?", org);
    }

    @Test
    @DisplayName("CA12: bounce deixa a mensagem BOUNCED, marca o aluno, audita uma vez e não duplica")
    void bounce() {
        UUID s = setup();
        admin.update("update student set email = 'aluno@bounce.example' where id = ?", s);
        UUID msg = service.send(org, "aluno@bounce.example", EmailTemplate.CHARGE_CREATED, "Mensalidade", "R$ 1,00", "10/11/2026");
        assertThat(admin.queryForObject("select status from email_message where id = ?", String.class, msg)).isEqualTo("SENT");

        bounces.register(msg);
        bounces.register(msg);
        assertThat(admin.queryForObject("select status from email_message where id = ?", String.class, msg)).isEqualTo("BOUNCED");
        assertThat(admin.queryForObject("select email_problem_at is not null from student where id = ?", Boolean.class, s)).isTrue();
        assertThat(admin.queryForObject("select count(*) from audit_log where action = 'student.email_problem_marked' and organization_id = ?", Long.class, org)).isEqualTo(1);
        assertThat(admin.queryForObject("select count(*) from audit_log where organization_id = ? and (after_data::text like '%bounce.example%')", Long.class, org)).isZero();
    }

    @Test
    @DisplayName("CA13: exceção do envio só soma tentativas e nunca marca o endereço")
    void failureIsNotBounce() {
        UUID s = setup();
        admin.update("update student set email = 'aluno@falha.example' where id = ?", s);
        EmailSender failing = new EmailSender() {
            @Override public boolean isEnabled() { return true; }
            @Override public SendResult send(String to, String subject, String body) { throw new IllegalStateException("down"); }
        };
        var svc = new EmailService(jdbc, failing, messages, java.time.Clock.systemUTC(), new br.com.ctcore.monitoring.OperationalMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()), 5, java.util.List.of(java.time.Duration.ofMinutes(1)));
        UUID msg = svc.send("aluno@falha.example", EmailTemplate.PASSWORD_CHANGED);
        assertThat(admin.queryForObject("select attempts from email_message where id = ?", Integer.class, msg)).isEqualTo(1);
        assertThat(admin.queryForObject("select email_problem_at is null from student where id = ?", Boolean.class, s)).isTrue();
        bounces.register(msg);
        assertThat(admin.queryForObject("select status from email_message where id = ?", String.class, msg)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("Pagador: aluno adulto recebe no próprio e-mail; menor, no do responsável financeiro; sem endereço, vazio")
    void payerEmail() {
        UUID adult = setup();
        admin.update("update student set email = 'adulto@pagador.example' where id = ?", adult);
        assertThat(payer.forStudent(org, adult)).contains("adulto@pagador.example");

        UUID minor = BillingFixtures.student(admin, org, unit, "Aluno Menor", java.time.LocalDate.now().minusYears(10).toString());
        admin.update("update student set email = 'menor@pagador.example' where id = ?", minor);
        assertThat(payer.forStudent(org, minor)).isEmpty();

        UUID acc = UUID.randomUUID(), g = UUID.randomUUID();
        admin.update("insert into account (id, email, status) values (?, 'resp@pagador.example', 'ACTIVE')", acc);
        admin.update("insert into guardian (id, organization_id, account_id, name, cpf, phone) values (?, ?, ?, 'Resp Um', '52998224725', '11987654321')", g, org, acc);
        admin.update("insert into student_guardian (organization_id, student_id, guardian_id, financial) values (?, ?, ?, true)", org, minor, g);
        assertThat(payer.forStudent(org, minor)).contains("resp@pagador.example");
        assertThat(payer.forStudent(UUID.randomUUID(), minor)).isEmpty();
    }
}
