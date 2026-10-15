package br.com.ctcore.integration;

import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Modo de cada conexão externa (RNF-40, RNF-41). {@code real} só existe quando as Sprints 22 e 23 criarem o adaptador. */
@ConfigurationProperties("ctcore.integrations")
public class IntegrationProperties implements InitializingBean {

    public static class Connection {
        private String mode = "simulated";
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    private final Connection payment = new Connection();
    private final Connection email = new Connection();
    private final Connection sms = new Connection();
    private final Connection push = new Connection();
    private final Connection challenge = new Connection();
    private final Connection map = new Connection();
    private final Connection metrics = new Connection();

    public Connection getPayment() { return payment; }
    public Connection getEmail() { return email; }
    public Connection getSms() { return sms; }
    public Connection getPush() { return push; }
    public Connection getChallenge() { return challenge; }
    public Connection getMap() { return map; }
    public Connection getMetrics() { return metrics; }

    @Override
    public void afterPropertiesSet() {
        modeOf("payment", payment);
        modeOf("email", email);
        modeOf("sms", sms);
        modeOf("push", push);
        modeOf("challenge", challenge);
        modeOf("map", map);
        modeOf("metrics", metrics);
    }

    /** Lança IllegalStateException citando a chave e os valores aceitos. */
    public static IntegrationMode modeOf(String name, Connection c) {
        String v = c.getMode() == null ? "" : c.getMode().trim().toUpperCase(Locale.ROOT);
        for (IntegrationMode m : IntegrationMode.values()) {
            if (m.name().equals(v)) return m;
        }
        throw new IllegalStateException("Invalid ctcore.integrations." + name + ".mode=" + c.getMode()
                + "; accepted values: simulated, off (real does not exist yet)");
    }
}
