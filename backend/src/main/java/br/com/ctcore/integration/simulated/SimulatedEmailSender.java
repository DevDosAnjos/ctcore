package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.EmailSender;
import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Grava na Caixa de saída; o log leva só tipo e id, nunca o texto (RNF-11). */
public class SimulatedEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedEmailSender.class);
    private final Outbox outbox;

    public SimulatedEmailSender(Outbox outbox) { this.outbox = outbox; }

    @Override
    public SendResult send(String to, String subject, String body) {
        LOG.info("simulated message stored type=EMAIL id={}", outbox.add(Outbox.Type.EMAIL, to, subject, body).id());
        return SendResult.SENT;
    }
}
