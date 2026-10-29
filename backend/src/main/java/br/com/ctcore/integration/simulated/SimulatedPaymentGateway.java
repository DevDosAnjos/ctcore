package br.com.ctcore.integration.simulated;

import br.com.ctcore.integration.PaymentCharge;
import br.com.ctcore.integration.PaymentGateway;
import br.com.ctcore.integration.PaymentMethod;
import br.com.ctcore.integration.PaymentStatus;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Simulador (RNF-40). Em memória e sem estorno parcial (D5); tudo sincronizado, pois é ambiente de teste. */
public class SimulatedPaymentGateway implements PaymentGateway {

    private final Map<String, PaymentCharge> byId = new HashMap<>();
    private final Map<String, String> idByKey = new HashMap<>();

    @Override
    public synchronized PaymentCharge createCharge(String idempotencyKey, long amountCents, PaymentMethod method) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");
        if (method == null) throw new IllegalArgumentException("method is required");
        if (amountCents <= 0) throw new IllegalArgumentException("amountCents must be positive");
        String existing = idByKey.get(idempotencyKey);
        if (existing != null) {
            PaymentCharge c = byId.get(existing);
            if (c.amountCents() != amountCents) throw new IllegalArgumentException("idempotency key reused with another amount");
            return c;
        }
        PaymentCharge c = new PaymentCharge(UUID.randomUUID().toString(), amountCents, PaymentStatus.PENDING);
        byId.put(c.id(), c);
        idByKey.put(idempotencyKey, c.id());
        return c;
    }

    @Override public PaymentCharge approve(String id) { return move(id, PaymentStatus.PENDING, PaymentStatus.APPROVED); }
    @Override public PaymentCharge refuse(String id) { return move(id, PaymentStatus.PENDING, PaymentStatus.REFUSED); }
    @Override public PaymentCharge expire(String id) { return move(id, PaymentStatus.PENDING, PaymentStatus.EXPIRED); }
    @Override public PaymentCharge refund(String id) { return move(id, PaymentStatus.APPROVED, PaymentStatus.REFUNDED); }
    @Override public PaymentCharge dispute(String id) { return move(id, PaymentStatus.APPROVED, PaymentStatus.DISPUTED); }

    @Override
    public synchronized PaymentCharge resendNotice(String id) { return get(id); }

    @Override
    public synchronized PaymentCharge consult(String id) { return get(id); }

    private synchronized PaymentCharge move(String id, PaymentStatus from, PaymentStatus to) {
        PaymentCharge c = get(id);
        if (c.status() != from) throw new IllegalStateException("cannot move charge from " + c.status() + " to " + to);
        PaymentCharge n = new PaymentCharge(c.id(), c.amountCents(), to);
        byId.put(id, n);
        return n;
    }

    private PaymentCharge get(String id) {
        PaymentCharge c = byId.get(id);
        if (c == null) throw new NoSuchElementException("charge not found");
        return c;
    }
}
