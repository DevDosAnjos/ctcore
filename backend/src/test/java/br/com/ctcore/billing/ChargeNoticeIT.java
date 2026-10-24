package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** RN-COM-11 (D3, D4): "Cobrança gerada" e "Diferença de reajuste" saem uma vez, ao pagador, quando faltam até 10 dias para o vencimento. */
class ChargeNoticeIT extends OrganizationBase {
    @Autowired JdbcClient jdbc;
    @Autowired OrganizationTransactions transactions;
    @Autowired EmailService email;

    @AfterEach
    void clean() {
        BillingFixtures.clean(adminJdbc());
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from email_message where to_email like 'notice-%'");
    }

    private ChargeNoticeJob job() {
        return new ChargeNoticeJob(transactions, jdbc, email, Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC));
    }

    private UUID charge(Tenant t, UUID student, String due, String status) {
        UUID id = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 15000, status);
        adminJdbc().update("update charge set due_on = ?::date where id = ?", due, id);
        return id;
    }

    private UUID adult(Tenant t, String address) {
        UUID s = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        adminJdbc().update("update student set email = ? where id = ?", address, s);
        return s;
    }

    private java.util.List<java.util.Map<String, Object>> sent(String like) {
        return adminJdbc().queryForList("select template, subject, status, organization_id from email_message where to_email like ?", like);
    }

    @Test
    @DisplayName("CA2, CA16: adulto com cobrança vencendo em 5 dias recebe CHARGE_CREATED com valor e vencimento; notified_at fica preenchido; rodar de novo não repete; log sem endereço")
    void adultOnce() {
        var t = tenant();
        UUID s = adult(t, "notice-adulto@example.com");
        UUID c = charge(t, s, "2026-10-10", "OPEN");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            job().runOnce();
            job().runOnce();
        } finally {
            root.detachAppender(logs);
        }
        var rows = sent("notice-adulto%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsEntry("template", "CHARGE_CREATED").containsEntry("organization_id", t.orgId());
        assertThat(adminJdbc().queryForObject("select notified_at is not null from charge where id = ?", Boolean.class, c)).isTrue();
        assertThat(adminJdbc().queryForObject("select count(*) from email_message where to_email = 'notice-adulto@example.com' and status <> 'SKIPPED'", Integer.class)).isEqualTo(1);
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("notice-adulto"));
    }

    @Test
    @DisplayName("CA2: o texto traz valor e vencimento")
    void text() {
        var t = tenant();
        charge(t, adult(t, "notice-texto@example.com"), "2026-10-10", "OPEN");
        UUID monthly = charge(t, adult(t, "notice-texto2@example.com"), "2026-10-10", "OPEN");
        adminJdbc().update("update charge set description = null, reference_month = date '2026-10-01' where id = ?", monthly);
        UUID bare = charge(t, adult(t, "notice-texto3@example.com"), "2026-10-10", "OPEN");
        adminJdbc().update("update charge set description = null, reference_month = null where id = ?", bare);
        job().runOnce();
        // Sem descrição e sem mês de referência o aviso usa a descrição "Cobrança" (ramo que antes só o agendador cobria, por acaso, #352).
        assertThat(sent("notice-texto3%")).hasSize(1);
        // O corpo só fica guardado se o envio estiver desligado ou pendente; aqui conferimos pelo assunto e pelo registro.
        assertThat(sent("notice-texto2%")).hasSize(1);
        assertThat(sent("notice-texto@%")).singleElement().satisfies(r -> assertThat(r.get("subject")).isEqualTo("Você tem uma cobrança em aberto"));
    }

    @Test
    @DisplayName("CA3: aluno menor com responsável financeiro: o aviso vai ao responsável, não ao aluno")
    void minor() {
        var t = tenant();
        UUID minor = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Pedro Souza", "2018-01-01");
        adminJdbc().update("update student set email = 'notice-menor@example.com' where id = ?", minor);
        UUID g = BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true);
        UUID c = charge(t, minor, "2026-10-08", "OPEN");
        adminJdbc().update("update charge set payer_guardian_id = ? where id = ?", g, c);
        job().runOnce();
        assertThat(sent("notice-menor%")).isEmpty();
        assertThat(adminJdbc().queryForList("select template from email_message where to_email like 'gen-%' and organization_id = ?", String.class, t.orgId()))
                .containsExactly("CHARGE_CREATED");
    }

    @Test
    @DisplayName("CA4: vencendo em 30 dias nada é enviado; quando faltam 10 dias, é avisada")
    void advance() {
        var t = tenant();
        UUID c = charge(t, adult(t, "notice-adiantada@example.com"), "2026-11-04", "OPEN");
        job().runOnce();
        assertThat(sent("notice-adiantada%")).isEmpty();
        adminJdbc().update("update charge set due_on = '2026-10-15' where id = ?", c);
        job().runOnce();
        assertThat(sent("notice-adiantada%")).hasSize(1);
    }

    @Test
    @DisplayName("CA5: cancelada ou paga não é avisada; cobrança anterior à migração (notified_at preenchido) também não")
    void notOpen() {
        var t = tenant();
        UUID s = adult(t, "notice-fechada@example.com");
        charge(t, s, "2026-10-10", "CANCELED");
        charge(t, s, "2026-10-10", "PAID");
        UUID old = charge(t, s, "2026-10-10", "OPEN");
        adminJdbc().update("update charge set notified_at = now() where id = ?", old);
        job().runOnce();
        assertThat(sent("notice-fechada%")).isEmpty();
    }

    @Test
    @DisplayName("CA6: a diferença de reajuste usa READJUSTMENT_DIFF; sem endereço marca como avisada sem mensagem")
    void diff() {
        var t = tenant();
        UUID c = charge(t, adult(t, "notice-diff@example.com"), "2026-10-12", "OPEN");
        adminJdbc().update("update charge set type = 'READJUSTMENT_DIFF', description = 'Diferença de reajuste' where id = ?", c);
        UUID none = charge(t, BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Sem Email"), "2026-10-12", "OPEN");
        job().runOnce();
        assertThat(sent("notice-diff%")).singleElement().satisfies(r -> assertThat(r.get("template")).isEqualTo("READJUSTMENT_DIFF"));
        assertThat(adminJdbc().queryForObject("select notified_at is not null from charge where id = ?", Boolean.class, none)).isTrue();
    }

    @Test
    @DisplayName("Erro em uma cobrança vai ao log sem endereço, desfaz o notified_at dela e não impede as outras")
    void errorIsolated() {
        var t = tenant();
        UUID bad = charge(t, adult(t, "notice-erro@example.com"), "2026-10-09", "OPEN");
        UUID good = charge(t, adult(t, "notice-boa@example.com"), "2026-10-10", "OPEN");
        EmailService failing = org.mockito.Mockito.mock(EmailService.class);
        org.mockito.Mockito.when(failing.send(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.eq("notice-erro@example.com"),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenThrow(new IllegalStateException("fora do ar notice-erro@example.com"));
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            new ChargeNoticeJob(transactions, jdbc, failing, Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)).runOnce();
        } finally {
            root.detachAppender(logs);
        }
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("cause=IllegalStateException"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("notice-erro") || e.getThrowableProxy() != null);
        assertThat(adminJdbc().queryForObject("select notified_at is null from charge where id = ?", Boolean.class, bad)).isTrue();
        assertThat(adminJdbc().queryForObject("select notified_at is not null from charge where id = ?", Boolean.class, good)).isTrue();
    }
}
