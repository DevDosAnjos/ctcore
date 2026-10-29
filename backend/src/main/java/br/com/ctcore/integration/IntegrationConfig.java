package br.com.ctcore.integration;

import br.com.ctcore.integration.IntegrationProperties.Connection;
import br.com.ctcore.integration.simulated.SimulatedChallengeVerifier;
import br.com.ctcore.integration.simulated.SimulatedEmailSender;
import br.com.ctcore.integration.simulated.SimulatedMapLocator;
import br.com.ctcore.integration.simulated.SimulatedPaymentGateway;
import br.com.ctcore.integration.simulated.SimulatedPushSender;
import br.com.ctcore.integration.simulated.SimulatedSmsSender;
import br.com.ctcore.integration.simulated.SimulatedUsageMetrics;
import java.time.Clock;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Escolhe, por conexão, a versão simulada ou a desligada (RNF-40, RNF-41). Desligada: resultado neutro, sem exceção. */
@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfig {

    private static boolean on(String name, Connection c) {
        return IntegrationProperties.modeOf(name, c) == IntegrationMode.SIMULATED;
    }

    @Bean
    public Outbox outbox(Clock clock) { return new Outbox(clock); }

    @Bean
    public PaymentGateway paymentGateway(IntegrationProperties p) {
        if (on("payment", p.getPayment())) return new SimulatedPaymentGateway();
        return new PaymentGateway() {
            private PaymentCharge off() { throw new IllegalStateException("payment connection is off"); }
            @Override public boolean isEnabled() { return false; }
            @Override public PaymentCharge createCharge(String k, long a, PaymentMethod m) { return off(); }
            @Override public PaymentCharge consult(String id) { return off(); }
            @Override public PaymentCharge approve(String id) { return off(); }
            @Override public PaymentCharge refuse(String id) { return off(); }
            @Override public PaymentCharge expire(String id) { return off(); }
            @Override public PaymentCharge refund(String id) { return off(); }
            @Override public PaymentCharge dispute(String id) { return off(); }
            @Override public PaymentCharge resendNotice(String id) { return off(); }
        };
    }

    @Bean
    public EmailSender emailSender(IntegrationProperties p, Outbox outbox) {
        if (on("email", p.getEmail())) return new SimulatedEmailSender(outbox);
        return new EmailSender() {
            @Override public boolean isEnabled() { return false; }
            @Override public SendResult send(String to, String s, String b) { return SendResult.NOT_SENT; }
        };
    }

    @Bean
    public SmsSender smsSender(IntegrationProperties p, Outbox outbox) {
        if (on("sms", p.getSms())) return new SimulatedSmsSender(outbox);
        return new SmsSender() {
            @Override public boolean isEnabled() { return false; }
            @Override public SendResult send(String to, String t) { return SendResult.NOT_SENT; }
        };
    }

    @Bean
    public PushSender pushSender(IntegrationProperties p, Outbox outbox) {
        if (on("push", p.getPush())) return new SimulatedPushSender(outbox);
        return new PushSender() {
            @Override public boolean isEnabled() { return false; }
            @Override public SendResult send(String to, String t, String b) { return SendResult.NOT_SENT; }
        };
    }

    @Bean
    public ChallengeVerifier challengeVerifier(IntegrationProperties p) {
        if (on("challenge", p.getChallenge())) return new SimulatedChallengeVerifier();
        return new ChallengeVerifier() {
            @Override public boolean isEnabled() { return false; }
            @Override public boolean verify(String token) { return true; }
        };
    }

    @Bean
    public MapLocator mapLocator(IntegrationProperties p) {
        if (on("map", p.getMap())) return new SimulatedMapLocator();
        return new MapLocator() {
            @Override public boolean isEnabled() { return false; }
            @Override public Optional<Coordinates> locate(String cep) { return Optional.empty(); }
        };
    }

    @Bean
    public UsageMetrics usageMetrics(IntegrationProperties p) {
        if (on("metrics", p.getMetrics())) return new SimulatedUsageMetrics();
        return new UsageMetrics() {
            @Override public boolean isEnabled() { return false; }
            @Override public void record(String event) { }
        };
    }
}
