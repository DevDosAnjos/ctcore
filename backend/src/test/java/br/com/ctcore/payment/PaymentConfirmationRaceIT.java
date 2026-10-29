package br.com.ctcore.payment;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.payment.PaymentConfirmations.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-PAG-11, RNF-12 (CA8): duas confirmações ao mesmo tempo da mesma tentativa geram um só pagamento e um só recibo. */
class PaymentConfirmationRaceIT extends PaymentOnlineBase {

    @Test
    @DisplayName("CA8: aviso e conciliação disputando a mesma tentativa em duas threads: um APPLIED, um DUPLICATE, um pagamento")
    void onePayment() throws Exception {
        var t = connected();
        UUID c = charge(t, "Maria Souza", 10000, "OPEN");
        var attempt = start(t, "PIX", c);
        sim().approve(attempt.providerId());
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Result>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<Result> task = () -> confirmations.apply(t.orgId(), attempt.providerId());
                results.add(pool.submit(task));
            }
            int applied = 0;
            for (var f : results) if (f.get() == Result.APPLIED) applied++;
            assertThat(applied).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from payment where charge_id = ?", c)).isEqualTo(1);
        assertThat(count("select count(*) from receipt where unit_id = ?", t.unitId())).isEqualTo(1);
    }
}
