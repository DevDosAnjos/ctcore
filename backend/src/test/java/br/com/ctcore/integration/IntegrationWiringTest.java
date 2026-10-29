package br.com.ctcore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.config.ClockConfig;
import br.com.ctcore.integration.simulated.SimulatedChallengeVerifier;
import br.com.ctcore.integration.simulated.SimulatedEmailSender;
import br.com.ctcore.integration.simulated.SimulatedMapLocator;
import br.com.ctcore.integration.simulated.SimulatedPaymentGateway;
import br.com.ctcore.integration.simulated.SimulatedPushSender;
import br.com.ctcore.integration.simulated.SimulatedSmsSender;
import br.com.ctcore.integration.simulated.SimulatedUsageMetrics;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IntegrationWiringTest {

    private static final List<String> NAMES = List.of("payment", "email", "sms", "push", "challenge", "map", "metrics");

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ClockConfig.class, IntegrationConfig.class);

    private static String[] allOff() {
        return NAMES.stream().map(n -> "ctcore.integrations." + n + ".mode=off").toArray(String[]::new);
    }

    @Test
    void defaultIsOneSimulatedBeanPerPort() {
        runner.run(c -> {
            assertThat(c).hasSingleBean(PaymentGateway.class).hasSingleBean(EmailSender.class)
                    .hasSingleBean(SmsSender.class).hasSingleBean(PushSender.class)
                    .hasSingleBean(ChallengeVerifier.class).hasSingleBean(MapLocator.class)
                    .hasSingleBean(UsageMetrics.class);
            assertThat(c.getBean(PaymentGateway.class)).isInstanceOf(SimulatedPaymentGateway.class);
            assertThat(c.getBean(EmailSender.class)).isInstanceOf(SimulatedEmailSender.class);
            assertThat(c.getBean(SmsSender.class)).isInstanceOf(SimulatedSmsSender.class);
            assertThat(c.getBean(PushSender.class)).isInstanceOf(SimulatedPushSender.class);
            assertThat(c.getBean(ChallengeVerifier.class)).isInstanceOf(SimulatedChallengeVerifier.class);
            assertThat(c.getBean(MapLocator.class)).isInstanceOf(SimulatedMapLocator.class);
            assertThat(c.getBean(UsageMetrics.class)).isInstanceOf(SimulatedUsageMetrics.class);
        });
    }

    @Test
    void allOffIsNeutralAndNeverThrows() {
        runner.withPropertyValues(allOff()).run(c -> {
            assertThat(c.getBean(PaymentGateway.class).isEnabled()).isFalse();
            assertThat(c.getBean(EmailSender.class).isEnabled()).isFalse();
            assertThat(c.getBean(SmsSender.class).isEnabled()).isFalse();
            assertThat(c.getBean(PushSender.class).isEnabled()).isFalse();
            assertThat(c.getBean(ChallengeVerifier.class).isEnabled()).isFalse();
            assertThat(c.getBean(MapLocator.class).isEnabled()).isFalse();
            assertThat(c.getBean(UsageMetrics.class).isEnabled()).isFalse();

            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> c.getBean(PaymentGateway.class).consult("x"));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> c.getBean(PaymentGateway.class).createCharge("k", 1, PaymentMethod.PIX));
            assertThat(c.getBean(EmailSender.class).send("a@b.c", "s", "b")).isEqualTo(SendResult.NOT_SENT);
            assertThat(c.getBean(SmsSender.class).send("1", "t")).isEqualTo(SendResult.NOT_SENT);
            assertThat(c.getBean(PushSender.class).send("1", "t", "b")).isEqualTo(SendResult.NOT_SENT);
            assertThat(c.getBean(ChallengeVerifier.class).verify("x")).isTrue();
            assertThat(c.getBean(MapLocator.class).locate("01001000")).isEmpty();
            c.getBean(UsageMetrics.class).record("e");
            assertThat(c.getBean(Outbox.class).list()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "email", "sms", "push", "challenge", "map", "metrics"})
    void onlyOneOffKeepsTheOthersSimulated(String off) {
        runner.withPropertyValues("ctcore.integrations." + off + ".mode=off").run(c -> {
            boolean[] enabled = {
                    c.getBean(PaymentGateway.class).isEnabled(), c.getBean(EmailSender.class).isEnabled(),
                    c.getBean(SmsSender.class).isEnabled(), c.getBean(PushSender.class).isEnabled(),
                    c.getBean(ChallengeVerifier.class).isEnabled(), c.getBean(MapLocator.class).isEnabled(),
                    c.getBean(UsageMetrics.class).isEnabled()};
            for (int i = 0; i < NAMES.size(); i++) {
                assertThat(enabled[i]).as(NAMES.get(i)).isEqualTo(!NAMES.get(i).equals(off));
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "email", "sms", "push", "challenge", "map", "metrics"})
    void realAndInvalidModesFailStartupNamingKeyAndValues(String name) {
        for (String bad : List.of("real", "xyz")) {
            runner.withPropertyValues("ctcore.integrations." + name + ".mode=" + bad).run(c -> {
                assertThat(c).hasFailed();
                assertThat(c.getStartupFailure()).hasStackTraceContaining("ctcore.integrations." + name + ".mode")
                        .hasStackTraceContaining("simulated, off");
            });
        }
    }
}
