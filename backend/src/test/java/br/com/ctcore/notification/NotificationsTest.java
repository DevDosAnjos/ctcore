package br.com.ctcore.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import br.com.ctcore.isolation.OrganizationTransactions;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** RN-NOT-01, RN-NOT-03, RN-NOT-04, R4: catálogo, grupos no fuso da unidade e falha de gravação engolida. */
class NotificationsTest {
    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");

    @Test
    @DisplayName("CA1, CA2: pagamento, maioridade e e-mail com problema são da unidade; benefício alto e convite são do Coordenador; só os três são urgentes")
    void catalog() {
        for (var t : new NotificationType[] {NotificationType.PAYMENT_RECEIVED, NotificationType.STUDENT_MAJORITY, NotificationType.EMAIL_PROBLEM}) {
            assertThat(t.audience()).isEqualTo(NotificationType.Audience.UNIT_ADMINS);
            assertThat(t.urgent()).isFalse();
        }
        for (var t : new NotificationType[] {NotificationType.HIGH_BENEFIT, NotificationType.INVITE_PENDING}) {
            assertThat(t.audience()).isEqualTo(NotificationType.Audience.COORDINATORS);
            assertThat(t.urgent()).isFalse();
        }
        for (var t : new NotificationType[] {NotificationType.CLOSURE_REQUEST, NotificationType.MP_DISCONNECTED, NotificationType.SUBSCRIPTION_OVERDUE}) {
            assertThat(t.audience()).isEqualTo(NotificationType.Audience.COORDINATORS);
            assertThat(t.urgent()).isTrue();
        }
    }

    @Test
    @DisplayName("CA10: hoje, 3 dias atrás e 6 dias atrás; 7 dias atrás sai; o limite usa o fuso da unidade perto da meia-noite de Brasília")
    void groups() {
        Instant now = Instant.parse("2026-10-24T14:00:00Z"); // 11h em Brasília
        assertThat(Notifications.group(now.minusSeconds(3600), BRASILIA, now)).isEqualTo("TODAY");
        assertThat(Notifications.group(now.minusSeconds(3 * 86400), BRASILIA, now)).isEqualTo("WEEK");
        assertThat(Notifications.group(now.minusSeconds(6 * 86400), BRASILIA, now)).isEqualTo("WEEK");
        assertThat(Notifications.group(now.minusSeconds(7 * 86400), BRASILIA, now)).isNull();
        assertThat(Notifications.group(now.minusSeconds(9 * 86400), BRASILIA, now)).isNull();
        // 02h30Z de 25/10 = 23h30 de 24/10 em Brasília: ainda é 24/10 lá, embora já seja 25/10 na JVM (UTC).
        Instant lateNight = Instant.parse("2026-10-25T02:30:00Z");
        Instant sameLocalDay = Instant.parse("2026-10-24T15:00:00Z");
        assertThat(Notifications.group(sameLocalDay, BRASILIA, lateNight)).isEqualTo("TODAY");
        assertThat(Notifications.group(sameLocalDay, ZoneOffset.UTC, lateNight)).isEqualTo("WEEK");
        assertThat(Notifications.group(Instant.parse("2026-10-24T02:59:00Z"), BRASILIA, lateNight)).isEqualTo("WEEK"); // 23h59 de 23/10 em Brasília
    }

    @Test
    @DisplayName("CA8: erro ao gravar a notificação é engolido e não chega a quem a pediu")
    void failureIsSwallowed() {
        var transactions = Mockito.mock(OrganizationTransactions.class);
        Mockito.when(transactions.provision(Mockito.any(), Mockito.any())).thenThrow(new IllegalStateException("db down"));
        var notifications = new Notifications(null, transactions, null, null, null, new ObjectMapper(), Clock.systemUTC());
        assertThatCode(() -> notifications.publish(UUID.randomUUID(), UUID.randomUUID(), NotificationType.PAYMENT_RECEIVED, "charge", UUID.randomUUID(), Map.of()))
                .doesNotThrowAnyException();
    }
}
