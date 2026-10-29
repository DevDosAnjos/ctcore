package br.com.ctcore.payment;

import br.com.ctcore.audit.AuditActor;
import br.com.ctcore.audit.AuditEntry;
import br.com.ctcore.audit.AuditService;
import br.com.ctcore.integration.PaymentCharge;
import br.com.ctcore.integration.PaymentGateway;
import br.com.ctcore.integration.PaymentStatus;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.isolation.OrganizationTransactions;
import br.com.ctcore.notification.NotificationType;
import br.com.ctcore.notification.Notifications;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-PAG-10, RN-PAG-11, RN-COB-15: aplica ao nosso registro o que o provedor diz de uma tentativa. O aviso e a conciliação só apontam a tentativa; o
 * estado vem da consulta ao provedor. Idempotente por estado: a transição acontece uma vez, sob a trava da linha da tentativa (D7).
 * ponytail: a consulta ao provedor fica dentro da transação (trava a tentativa); rever no adaptador real, que pode demorar.
 */
@Service
public class PaymentConfirmations {
    public enum Result { APPLIED, DUPLICATE, IGNORED, UNKNOWN }

    private static final AuditActor SYSTEM = new AuditActor(AuditActor.Type.SYSTEM, null, null);
    /** Estados de partida permitidos para cada estado novo. */
    private static final Map<String, Set<String>> FROM = Map.of(
            "APPROVED", Set.of("CREATED"), "REFUSED", Set.of("CREATED"), "EXPIRED", Set.of("CREATED"),
            "REFUNDED", Set.of("APPROVED"), "DISPUTED", Set.of("APPROVED"));

    private record Attempt(UUID id, UUID unitId, String status, long amountCents, String method) {}

    private final JdbcClient jdbc;
    private final OrganizationTransactions transactions;
    private final PaymentGateway gateway;
    private final Payments payments;
    private final AuditService audit;
    private final Notifications notifications;
    private final Clock clock;

    public PaymentConfirmations(@Lazy JdbcClient jdbc, @Lazy OrganizationTransactions transactions, PaymentGateway gateway, Payments payments, AuditService audit,
            Notifications notifications, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.gateway = gateway;
        this.payments = payments;
        this.audit = audit;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Roda no escopo da organização (sem sessão: aviso e agendador), com o contexto aberto para a auditoria. */
    <T> T inOrganization(UUID org, Supplier<T> work) {
        if (OrganizationContext.permits(org)) return transactions.execute(work);
        try (var ignored = OrganizationContext.open(new OrganizationPrincipal() {
            @Override public UUID organizationId() { return org; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        })) {
            return transactions.execute(work);
        }
    }

    /** Consulta o provedor e aplica o resultado à tentativa {@code providerId} da organização. Falha da consulta propaga e nada é gravado. */
    public Result apply(UUID org, String providerId) {
        List<Runnable> after = new ArrayList<>();
        Result result = inOrganization(org, () -> within(org, providerId, after));
        // RN-NOT-04: depois do commit; a notificação nunca desfaz o pagamento (falha engolida em publish).
        after.forEach(Runnable::run);
        return result;
    }

    private Result within(UUID org, String providerId, List<Runnable> after) {
        Attempt a = jdbc.sql("select id, unit_id, status, amount_cents, method from payment_attempt where provider_id = :p and organization_id = :org for update")
                .param("p", providerId).param("org", org)
                .query((rs, n) -> new Attempt(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getLong(4), rs.getString(5))).optional().orElse(null);
        if (a == null) return Result.UNKNOWN;
        PaymentCharge seen = gateway.consult(providerId);
        if (seen.amountCents() != a.amountCents()) {
            alert(org, a.id(), a.unitId(), "DIVERGENCE", "valor do provedor diferente do da tentativa");
            return Result.IGNORED;
        }
        String target = seen.status() == PaymentStatus.PENDING ? "CREATED" : seen.status().name();
        if (target.equals(a.status())) return "CREATED".equals(target) ? Result.IGNORED : Result.DUPLICATE;
        if (!FROM.getOrDefault(target, Set.of()).contains(a.status())) return Result.IGNORED;
        jdbc.sql("update payment_attempt set status = :s, resolved_at = :now, provider_response = jsonb_build_object('id', cast(:id as text), 'status', cast(:s as text), "
                + "'amountCents', cast(:amount as bigint)) where id = :attempt").param("s", target).param("now", OffsetDateTime.now(clock)).param("id", providerId)
                .param("amount", seen.amountCents()).param("attempt", a.id()).update();
        switch (target) {
            case "APPROVED" -> settle(org, a, providerId, after);
            case "REFUSED" -> refuse(org, a, after);
            default -> { }
        }
        return Result.APPLIED;
    }

    private void settle(UUID org, Attempt a, String providerId, List<Runnable> after) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        String method = "PIX".equals(a.method()) ? "PIX_MP" : a.method();
        for (var link : jdbc.sql("select charge_id, amount_cents from payment_attempt_charge where attempt_id = :a order by charge_id")
                .param("a", a.id()).query((rs, n) -> Map.entry(rs.getObject(1, UUID.class), rs.getLong(2))).list()) {
            UUID chargeId = link.getKey();
            var charge = jdbc.sql("select student_id, amount_cents, status from charge where id = :id and organization_id = :org for update")
                    .param("id", chargeId).param("org", org)
                    .query((rs, n) -> new Object[] {rs.getObject(1, UUID.class), rs.getLong(2), rs.getString(3)}).single();
            String status = (String) charge[2];
            if ("OPEN".equals(status)) {
                var done = payments.recordConfirmed(org, chargeId, a.unitId(), (UUID) charge[0], (Long) charge[1], method, "PORTAL", now, null, null,
                        providerId + ":" + chargeId, SYSTEM, false, null);
                after.add(() -> notifications.publish(org, a.unitId(), NotificationType.PAYMENT_RECEIVED, "charge", chargeId,
                        Map.of("studentName", done.studentName(), "amountCents", done.registered().amountCents())));
            } else if ("PAID".equals(status)) {
                jdbc.sql("insert into payment_duplicate (id, organization_id, charge_id, attempt_id, unit_id, amount_cents, method, provider_id, detected_at) "
                        + "values (:id, :org, :charge, :attempt, :unit, :amount, :method, :provider, :now) on conflict (attempt_id, charge_id) do nothing")
                        .param("id", UUID.randomUUID()).param("org", org).param("charge", chargeId).param("attempt", a.id()).param("unit", a.unitId())
                        .param("amount", link.getValue()).param("method", a.method()).param("provider", providerId).param("now", now).update();
            } else {
                alert(org, a.id(), a.unitId(), "DIVERGENCE", "provedor aprovou, mas a cobrança está " + status + " e não pode receber a baixa");
            }
        }
    }

    private void refuse(UUID org, Attempt a, List<Runnable> after) {
        audit.record(new AuditEntry(SYSTEM, "payment_attempt.refused", "payment_attempt", a.id().toString(), a.unitId(), Map.of("status", a.status()),
                Map.of("status", "REFUSED", "amountCents", a.amountCents(), "method", a.method()), null, null));
        String student = jdbc.sql("select min(s.name) from payment_attempt_charge pc join charge c on c.id = pc.charge_id join student s on s.id = c.student_id "
                + "where pc.attempt_id = :a").param("a", a.id()).query(String.class).single();
        after.add(() -> notifications.publish(org, a.unitId(), NotificationType.PAYMENT_REFUSED, "payment_attempt", a.id(),
                Map.of("studentName", student, "amountCents", a.amountCents())));
    }

    /** RN-PAG-12 (D13): um alerta em aberto não se repete para a mesma tentativa e tipo. */
    void alert(UUID org, UUID attempt, UUID unit, String kind, String detail) {
        jdbc.sql("insert into payment_alert (id, organization_id, unit_id, attempt_id, kind, detail, created_at) values (:id, :org, :unit, :attempt, :kind, :detail, :now) "
                + "on conflict (attempt_id, kind) where resolved_at is null do nothing")
                .param("id", UUID.randomUUID()).param("org", org).param("unit", unit).param("attempt", attempt).param("kind", kind).param("detail", detail)
                .param("now", OffsetDateTime.now(clock)).update();
    }
}
