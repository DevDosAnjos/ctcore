package br.com.ctcore.mail;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.db.PostgresIT;
import br.com.ctcore.integration.Outbox;
import br.com.ctcore.student.EmailProblems;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;

/** RN-COM-11, RN-COM-13, RN-ALU-10 (#78): registro, desligar comunicados e endereço com problema, com banco real. */
class EmailRegistryIT extends PostgresIT {
    @Autowired EmailService service;
    @Autowired EmailRetryJob job;
    @Autowired EmailProblems problems;
    @Autowired Outbox outbox;
    @Autowired MessageSource messages;

    JdbcTemplate admin = adminJdbc();
    List<UUID> orgs = new ArrayList<>();
    ListAppender<ILoggingEvent> logs = new ListAppender<>();
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void setup() {
        outbox.clear();
        logs.start();
        root.addAppender(logs);
    }

    @AfterEach
    void clean() {
        root.detachAppender(logs);
        cleanLinked("emlnot-");
        admin.update("delete from email_message where to_email like '%.example'");
        admin.update("delete from student where email like '%.example'");
        admin.update("delete from account_link where account_id in (select id from account where email like '%.example')");
        admin.update("delete from account where email like '%.example'");
        admin.execute("alter table audit_log disable trigger user");
        for (UUID org : orgs) {
            admin.update("delete from audit_log where organization_id = ?", org);
            admin.update("delete from unit where organization_id = ?", org);
            admin.update("delete from organization where id = ?", org);
        }
        admin.execute("alter table audit_log enable trigger user");
    }

    UUID org() {
        UUID id = UUID.randomUUID();
        admin.update("insert into organization (id, name) values (?, 'Org')", id);
        admin.update("insert into unit (id, organization_id, name, short_name) values (?, ?, 'U', ?)", UUID.randomUUID(), id, "u-" + id);
        orgs.add(id);
        return id;
    }

    void link(UUID account, UUID org, boolean announcements) {
        admin.update("insert into account_link (account_id, organization_id, role, status, announcement_email) values (?, ?, 'STUDENT', 'ACTIVE', ?)",
                account, org, announcements);
    }

    UUID account(String email) {
        UUID id = UUID.randomUUID();
        admin.update("insert into account (id, email, status) values (?, ?, 'ACTIVE')", id, email);
        return id;
    }

    Map<String, Object> row(UUID id) {
        return admin.queryForMap("select * from email_message where id = ?", id);
    }

    String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
    }

    @Test
    @DisplayName("CA1: todo modelo tem assunto, texto e categoria; só ANNOUNCEMENT é desligável")
    void registry() {
        for (EmailTemplate t : EmailTemplate.values()) {
            assertThat(messages.getMessage(t.key + ".subject", null, Locale.of("pt", "BR"))).isNotBlank();
            assertThat(messages.getMessage(t.key + ".body", null, Locale.of("pt", "BR"))).isNotBlank();
            assertThat(t.kind() == EmailTemplate.Kind.ANNOUNCEMENT).isEqualTo(t == EmailTemplate.ANNOUNCEMENT);
        }
        assertThat(EmailTemplate.valueOf("PAYMENT_CONFIRMED").key).isEqualTo("mail.payment-confirmed");
    }

    @Test
    @DisplayName("CA9, CA10, CA16: comunicados desligados são SKIPPED por OPT_OUT, os demais modelos chegam e outra organização segue recebendo")
    void optOut() {
        UUID a = org(), b = org();
        UUID acc = account("ana@opt.example");
        link(acc, a, false);
        link(acc, b, true);

        UUID skipped = service.send(a, "ana@opt.example", EmailTemplate.ANNOUNCEMENT, "Aula suspensa");
        assertThat(row(skipped)).containsEntry("status", "SKIPPED").containsEntry("last_error", "OPT_OUT").containsEntry("organization_id", a);
        assertThat(outbox.list()).isEmpty();

        service.send(a, "ana@opt.example", EmailTemplate.CHARGE_CREATED, "Mensalidade", "R$ 150,00", "10/11/2026");
        service.send(a, "ana@opt.example", EmailTemplate.PAYMENT_CONFIRMED, "https://x/recibo/C", "R$ 150,00", "1");
        service.send(a, "ana@opt.example", EmailTemplate.PASSWORD_CHANGED);
        assertThat(outbox.list()).hasSize(3);

        UUID other = service.send(b, "ana@opt.example", EmailTemplate.ANNOUNCEMENT, "Aula suspensa");
        assertThat(row(other)).containsEntry("status", "SENT").containsEntry("organization_id", b);
        assertThat(outbox.list()).hasSize(4);
        assertThat(allLogs()).doesNotContain("ana@opt.example", "Aula suspensa");
    }

    @Test
    @DisplayName("CA14: endereço com problema recebe SKIPPED por EMAIL_PROBLEM em qualquer modelo; depois de limpar, volta a receber")
    void emailProblem() {
        UUID org = org();
        UUID unit = admin.queryForObject("select id from unit where organization_id = ?", UUID.class, org);
        UUID student = BillingFixtures.student(admin, org, unit, "Aluno Teste");
        admin.update("update student set email = 'aluno@prob.example' where id = ?", student);

        assertThat(problems.mark(org, "ALUNO@prob.example")).isEqualTo(1);
        UUID id = service.send(org, "aluno@prob.example", EmailTemplate.PAYMENT_CONFIRMED, "https://x/r", "R$ 1,00", "1");
        assertThat(row(id)).containsEntry("status", "SKIPPED").containsEntry("last_error", "EMAIL_PROBLEM");
        assertThat(outbox.list()).isEmpty();

        problems.clear(org, "aluno@prob.example");
        assertThat(row(service.send(org, "aluno@prob.example", EmailTemplate.PASSWORD_CHANGED))).containsEntry("status", "SENT");
        assertThat(outbox.list()).hasSize(1);
    }


    @Test
    @DisplayName("#84 CA6: e-mail do aluno marcado Com problema avisa o Administrador da unidade, uma vez, sem o endereço")
    void emailProblemNotifies() {
        UUID org = org();
        UUID unit = admin.queryForObject("select id from unit where organization_id = ?", UUID.class, org);
        var administrator = linked(org, br.com.ctcore.identity.Role.ADMINISTRATOR, "emlnot-", unit);
        UUID student = BillingFixtures.student(admin, org, unit, "Aluno Teste");
        admin.update("update student set email = 'aluno@notice.example' where id = ?", student);

        assertThat(problems.mark(org, "aluno@notice.example")).isEqualTo(1);
        assertThat(problems.mark(org, "aluno@notice.example")).isZero();
        var rows = admin.queryForList("select type, ref_type, ref_id, params::text as params from notification where link_id = ?", administrator.activeLinkId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("type", "EMAIL_PROBLEM").containsEntry("ref_type", "student").containsEntry("ref_id", student);
        assertThat(rows.toString()).doesNotContain("notice.example");
        problems.clear(org, "aluno@notice.example");
        assertThat(problems.mark(org, "aluno@notice.example")).isEqualTo(1);
        assertThat(admin.queryForObject("select count(*) from notification where link_id = ?", Integer.class, administrator.activeLinkId())).isEqualTo(1);
    }
    @Test
    @DisplayName("A tarefa apaga SKIPPED e BOUNCED antigos e não os reenvia")
    void cleanup() {
        for (String status : new String[]{"SKIPPED", "BOUNCED"}) {
            admin.update("insert into email_message (to_email, template, subject, status, created_at) values ('x@old.example', 'ANNOUNCEMENT', 's', ?, now() - interval '40 days')", status);
        }
        job.runOnce();
        assertThat(admin.queryForObject("select count(*) from email_message where to_email = 'x@old.example'", Long.class)).isZero();
        assertThat(outbox.list()).isEmpty();
    }
}
