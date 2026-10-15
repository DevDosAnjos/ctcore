package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.Outbox;
import br.com.ctcore.integration.SendResult;
import br.com.ctcore.integration.SmsSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Grava na Caixa de saída (o código de confirmação fica visível ali); o log leva só tipo e id. */
public class SimulatedSmsSender implements SmsSender {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedSmsSender.class);
    private final Outbox outbox;

    public SimulatedSmsSender(Outbox outbox) { this.outbox = outbox; }

    @Override
    public SendResult send(String to, String text) {
        LOG.info("simulated message stored type=SMS id={}", outbox.add(Outbox.Type.SMS, to, null, text).id());
        return SendResult.SENT;
    }
}
