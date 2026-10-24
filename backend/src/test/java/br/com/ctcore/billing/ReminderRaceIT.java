package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CA8 (#79): pagamento confirmado numa transação aberta enquanto a tarefa roda: a linha da cobrança é travada antes do envio. */
class ReminderRaceIT extends ReminderBase {
    @Test
    @DisplayName("CA8: a cobrança é paga em transação aberta; o lembrete espera a trava e, depois do commit, não sai")
    void paymentWinsTheRace() throws Exception {
        var t = tenant();
        var c = charge(t, adult(t, "rem-corrida@example.com"), "2026-10-08", "OPEN");
        try (Connection payer = adminJdbc().getDataSource().getConnection()) {
            payer.setAutoCommit(false);
            try (var st = payer.prepareStatement("update charge set status = 'PAID' where id = ?")) {
                st.setObject(1, c);
                st.executeUpdate();
            }
            CompletableFuture<Integer> running = CompletableFuture.supplyAsync(() -> job().runOnce());
            long deadline = System.currentTimeMillis() + 20000;
            while (adminJdbc().queryForObject("select count(*) from pg_stat_activity where wait_event_type = 'Lock' and query like '%for update%'", Integer.class) == 0) {
                assertThat(System.currentTimeMillis()).as("a tarefa deveria esperar a trava").isLessThan(deadline);
                Thread.sleep(50);
            }
            payer.commit();
            assertThat(running.get()).isZero();
        }
        assertThat(mails("rem-corrida%")).isEmpty();
        assertThat(history(c)).isEmpty();
    }
}
