package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.PushSender;
import br.com.ctcore.integration.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Grava na Caixa de saída; o log leva só tipo e id. */
public class SimulatedPushSender implements PushSender {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPushSender.class);
    private final Outbox outbox;

    public SimulatedPushSender(Outbox outbox) { this.outbox = outbox; }

    @Override
    public SendResult send(String to, String title, String body) {
        LOG.info("simulated message stored type=PUSH id={}", outbox.add(Outbox.Type.PUSH, to, title, body).id());
        return SendResult.SENT;
    }
}
