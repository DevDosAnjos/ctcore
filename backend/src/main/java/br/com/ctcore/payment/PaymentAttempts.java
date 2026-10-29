package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ApiValidationException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.integration.PaymentGateway;
import br.com.ctcore.integration.PaymentMethod;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-PAG-03, RN-PAG-04 (D3, D4): cria a tentativa de pagamento online de uma ou mais cobranças da mesma unidade. Sem rota: quem expõe é o portal (#98 em diante),
 * que já conferiu que a pessoa pode pagar aquelas cobranças. A chave de idempotência é o id da tentativa, escolhido por quem chama.
 */
@Service
public class PaymentAttempts {
    public record Attempt(UUID id, String providerId, String status, long amountCents, String method) {}

    private final JdbcClient jdbc;
    private final PaymentConfirmations scope;
    private final PaymentGateway gateway;
    private final Clock clock;

    public PaymentAttempts(@Lazy JdbcClient jdbc, PaymentConfirmations scope, PaymentGateway gateway, Clock clock) {
        this.jdbc = jdbc;
        this.scope = scope;
        this.gateway = gateway;
        this.clock = clock;
    }

    public Attempt start(UUID org, UUID unitId, List<UUID> chargeIds, String method, UUID key) {
        if (method == null || Arrays.stream(PaymentMethod.values()).noneMatch(m -> m.name().equals(method))) throw new ApiValidationException("method", "valor inválido");
        if (chargeIds == null || chargeIds.isEmpty() || chargeIds.stream().distinct().count() != chargeIds.size()) throw new ApiValidationException("chargeIds", "valor inválido");
        if (!gateway.isEnabled()) throw new ApiStatusException(ErrorCode.PAYMENT_UNAVAILABLE);
        return scope.inOrganization(org, () -> {
            var existing = jdbc.sql("select id, provider_id, status, amount_cents, method from payment_attempt where id = :id and organization_id = :org")
                    .param("id", key).param("org", org).query(PaymentAttempts::row).optional();
            if (existing.isPresent()) return existing.get();
            boolean connected = jdbc.sql("select coalesce(btrim(payment_account_reference), '') <> '' from unit_settings where unit_id = :u and organization_id = :org")
                    .param("u", unitId).param("org", org).query(Boolean.class).optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
            if (!connected) throw new ApiStatusException(ErrorCode.PAYMENT_ACCOUNT_NOT_CONNECTED);
            var charges = jdbc.sql("select id, unit_id, status, amount_cents from charge where organization_id = :org and id in (:ids)")
                    .param("org", org).param("ids", chargeIds)
                    .query((rs, n) -> new Object[] {rs.getObject(2, UUID.class), rs.getString(3), rs.getLong(4), rs.getObject(1, UUID.class)}).list();
            if (charges.size() != chargeIds.size()) throw new ApiStatusException(ErrorCode.NOT_FOUND);
            if (charges.stream().anyMatch(c -> !unitId.equals(c[0]))) throw new ApiStatusException(ErrorCode.CONFLICT);
            if (charges.stream().anyMatch(c -> !"OPEN".equals(c[1]))) throw new ApiStatusException(ErrorCode.CHARGE_NOT_PAYABLE);
            long total = charges.stream().mapToLong(c -> (Long) c[2]).sum();
            if (total <= 0) throw new ApiStatusException(ErrorCode.UNPROCESSABLE);
            var provider = gateway.createCharge(key.toString(), total, PaymentMethod.valueOf(method));
            jdbc.sql("insert into payment_attempt (id, organization_id, unit_id, method, amount_cents, provider_id, created_at) "
                    + "values (:id, :org, :unit, :method, :amount, :provider, :now)")
                    .param("id", key).param("org", org).param("unit", unitId).param("method", method).param("amount", total).param("provider", provider.id())
                    .param("now", OffsetDateTime.now(clock)).update();
            for (var c : charges) {
                jdbc.sql("insert into payment_attempt_charge (organization_id, attempt_id, charge_id, amount_cents) values (:org, :attempt, :charge, :amount)")
                        .param("org", org).param("attempt", key).param("charge", c[3]).param("amount", c[2]).update();
            }
            return new Attempt(key, provider.id(), "CREATED", total, method);
        });
    }

    private static Attempt row(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Attempt(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5));
    }
}
