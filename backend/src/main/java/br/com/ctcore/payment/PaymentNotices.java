package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiValidationException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * RN-PAG-10, RN-PAG-11 (D5): aviso do provedor, sem sessão. O corpo só aponta a tentativa; quem decide é a consulta ao provedor. Cada aviso é gravado
 * com o resultado (só o identificador, nunca o corpo bruto). A organização vem da função {@code payment_attempt_owner}; desconhecido responde como conhecido.
 */
@Service
public class PaymentNotices {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentNotices.class);
    static final int MAX_ID = 100;

    private final JdbcClient jdbc;
    private final PaymentConfirmations confirmations;
    private final Clock clock;

    public PaymentNotices(@Lazy JdbcClient jdbc, PaymentConfirmations confirmations, Clock clock) {
        this.jdbc = jdbc;
        this.confirmations = confirmations;
        this.clock = clock;
    }

    /** Falha ao processar propaga (o provedor repete, e a conciliação recupera) depois de gravar o aviso como FAILED e abrir o alerta. */
    public void receive(String providerId) {
        if (providerId == null || providerId.isBlank() || providerId.length() > MAX_ID) throw new ApiValidationException("id", "valor inválido");
        UUID org = jdbc.sql("select payment_attempt_owner(:p)").param("p", providerId).query(UUID.class).optional().orElse(null);
        if (org == null) {
            record(null, providerId, "UNKNOWN");
            return;
        }
        PaymentConfirmations.Result result;
        try {
            result = confirmations.apply(org, providerId);
        } catch (RuntimeException e) {
            LOG.error("payment notice failed org={} cause={}", org, e.getClass().getSimpleName());
            confirmations.inOrganization(org, () -> {
                record(org, providerId, "FAILED");
                var attempt = jdbc.sql("select id, unit_id from payment_attempt where provider_id = :p and organization_id = :org").param("p", providerId).param("org", org)
                        .query((rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}).single();
                confirmations.alert(org, attempt[0], attempt[1], "WEBHOOK_FAILURE", "falha ao processar o aviso do provedor: " + e.getClass().getSimpleName());
                return null;
            });
            throw e;
        }
        confirmations.inOrganization(org, () -> {
            record(org, providerId, result.name());
            return null;
        });
    }

    private void record(UUID org, String providerId, String result) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        jdbc.sql("insert into payment_notice (id, organization_id, provider_id, content, received_at, processed_at, result) "
                + "values (:id, :org, :p, jsonb_build_object('id', cast(:p as text)), :now, :now, :result)")
                .param("id", UUID.randomUUID()).param("org", org, java.sql.Types.OTHER).param("p", providerId).param("now", now).param("result", result).update();
    }
}
