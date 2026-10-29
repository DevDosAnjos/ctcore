package br.com.ctcore.student;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** CA3 (#92): o ano padrão do histórico é o do fuso da unidade de cadastro, não o da JVM (UTC). */
@Import(PortalFinanceYearIT.NewYearClock.class)
class PortalFinanceYearIT extends PortalTermBase {
    /** 31/12/2025 11:00 UTC: já é 01/01/2026 em Kiritimati (UTC+14) e ainda 31/12/2025 em Brasília. */
    @TestConfiguration
    static class NewYearClock {
        @Bean
        @Primary
        Clock newYearClock() {
            return Clock.fixed(Instant.parse("2025-12-31T11:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("CA3: na virada do ano, Kiritimati já está em 2026 e Brasília ainda em 2025")
    void defaultYearFollowsUnitZone() throws Exception {
        Tenant t = tenant();
        UUID acc = account("aluno@portal.example");
        UUID s = student(t, "Aluno Um", "1990-01-01", acc);
        EnrollmentFixtures.enroll(adminJdbc(), t.orgId(), s, t.unitId(), "ACTIVE", 0, null);
        adminJdbc().update("insert into term_acceptance (id, organization_id, student_id, kind, event, version, account_id, ip) values (?, ?, ?, 'ENROLLMENT', 'ACCEPTED', 1, ?, '127.0.0.1')",
                UUID.randomUUID(), t.orgId(), s, acc);

        assertThat(body(get(portal(t, acc, Role.STUDENT), "/finance")).get("year").asInt()).isEqualTo(2025);
        adminJdbc().update("update unit set timezone = 'Pacific/Kiritimati' where id = ?", t.unitId());
        assertThat(body(get(portal(t, acc, Role.STUDENT), "/finance")).get("year").asInt()).isEqualTo(2026);
    }
}
