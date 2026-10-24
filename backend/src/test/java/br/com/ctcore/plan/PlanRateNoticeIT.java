package br.com.ctcore.plan;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.BillingFixtures;
import br.com.ctcore.organization.OrganizationBase;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.LoggerFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** RN-PLA-04 (R3): mudar o valor do plano avisa o pagador de cada aluno ativo nele; nome sozinho ou transação desfeita não envia nada. */
class PlanRateNoticeIT extends OrganizationBase {
    @Autowired ObjectMapper json;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean br.com.ctcore.mail.EmailService emailSpy;

    @AfterEach
    void clean() {
        adminJdbc().update("delete from plan_value_history");
        BillingFixtures.cleanAll(adminJdbc());
        adminJdbc().update("delete from email_message where template = 'PLAN_RATE_ADJUSTED'");
    }

    private record Setup(Tenant t, UUID plan, UUID guardian) {}

    private Setup setup() {
        var t = tenant();
        UUID[] rows = new UUID[3];
        for (int i = 0; i < 3; i++) {
            String birth = i == 2 ? "2018-01-01" : "1990-01-01";
            UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Aluno", birth);
            adminJdbc().update("update student set email = ? where id = ?", "rate-" + i + "-" + student + "@example.com", student);
            rows[i] = BillingFixtures.modality(adminJdbc(), t.orgId(), t.unitId(), student, 10, 15000);
        }
        UUID plan = adminJdbc().queryForObject("select plan_id from enrollment_modality where id = ?", UUID.class, rows[0]);
        adminJdbc().update("update enrollment_modality set plan_id = ? where id in (?, ?)", plan, rows[1], rows[2]);
        UUID minor = adminJdbc().queryForObject("select student_id from enrollment_modality where id = ?", UUID.class, rows[2]);
        return new Setup(t, plan, BillingFixtures.guardian(adminJdbc(), t.orgId(), minor, true));
    }

    private int put(Setup s, String name, long value) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("unitId", s.t().unitId());
        b.put("modalityId", adminJdbc().queryForObject("select modality_id from plan where id = ?", UUID.class, s.plan()));
        b.put("name", name != null ? name : adminJdbc().queryForObject("select name from plan where id = ?", String.class, s.plan()));
        b.put("valueCents", value);
        b.put("weeklyFrequency", 2);
        b.put("validOtherUnits", false);
        b.put("unpaidCharges", "KEEP");
        b.put("paidFutureDifference", false);
        return call(MockMvcRequestBuilders.put("/api/v1/plans/" + s.plan()).contentType("application/json").content(json.writeValueAsString(b)),
                who(s.t(), br.com.ctcore.identity.Role.COORDINATOR)).getResponse().getStatus();
    }

    private List<String> sent(Setup s) {
        return adminJdbc().queryForList("select to_email from email_message where template = 'PLAN_RATE_ADJUSTED' and organization_id = ?", String.class, s.t().orgId());
    }

    @Test
    @DisplayName("R2: falha no envio do aviso de reajuste vai ao log só com a classe da exceção, sem o endereço")
    void failureLogsOnlyClass() throws Exception {
        var s = setup();
        org.mockito.Mockito.doThrow(new IllegalStateException("falha rate-0@example.com")).when(emailSpy)
                .send(org.mockito.ArgumentMatchers.any(UUID.class), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        try {
            assertThat(put(s, null, 17000)).isEqualTo(200);
        } finally {
            root.detachAppender(logs);
            org.mockito.Mockito.reset(emailSpy);
        }
        assertThat(logs.list).anySatisfy(e -> assertThat(e.getFormattedMessage()).contains("plan rate notice failed").contains("cause=IllegalStateException"));
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains("@example.com") || e.getThrowableProxy() != null);
    }

    @Test
    @DisplayName("CA7, CA16: 3 alunos ativos (um menor): 3 mensagens aos pagadores certos; só o nome ou a transação desfeita não enviam")
    void notices() throws Exception {
        var s = setup();
        adminJdbc().update("update plan set value_cents = 15000 where id = ?", s.plan());
        String name = adminJdbc().queryForObject("select name from plan where id = ?", String.class, s.plan());

        assertThat(put(s, name + " novo", 15000)).isEqualTo(200);
        assertThat(sent(s)).isEmpty();

        UUID other = BillingFixtures.otherPlan(adminJdbc(), s.t().orgId(), s.t().unitId(),
                adminJdbc().queryForObject("select id from enrollment_modality where plan_id = ? limit 1", UUID.class, s.plan()), 9000);
        String taken = adminJdbc().queryForObject("select name from plan where id = ?", String.class, other);
        assertThat(put(s, taken, 17000)).isEqualTo(409);
        assertThat(sent(s)).isEmpty();

        assertThat(put(s, null, 17000)).isEqualTo(200);
        var to = sent(s);
        assertThat(to).hasSize(3);
        assertThat(to.stream().filter(a -> a.startsWith("gen-"))).hasSize(1);
        assertThat(to.stream().filter(a -> a.startsWith("rate-2-"))).isEmpty();
        assertThat(adminJdbc().queryForList("select subject from email_message where template = 'PLAN_RATE_ADJUSTED' and organization_id = ?", String.class, s.t().orgId()))
                .allMatch("O valor do seu plano mudou"::equals);
    }
}
