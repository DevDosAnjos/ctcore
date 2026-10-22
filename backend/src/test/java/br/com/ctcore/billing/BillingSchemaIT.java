package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.organization.OrganizationBase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** CA1, CA8 (tabelas): permissões do papel de execução e checks da cobrança, do pagamento e do recibo. */
class BillingSchemaIT extends OrganizationBase {
    @AfterEach
    void clean() {
        BillingFixtures.clean(adminJdbc());
        adminJdbc().update("delete from student");
    }

    @Test
    @DisplayName("CA1: o papel de execução não apaga cobrança, pagamento nem recibo, não altera pagamento e recibo, mas altera a situação da cobrança")
    void permissions() throws Exception {
        var t = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 15000, "OPEN");
        UUID payment = UUID.randomUUID();
        adminJdbc().update("insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) values (?, ?, ?, ?, ?, 15000, 'CASH', 'MANAGEMENT', now())",
                payment, t.orgId(), charge, student, t.unitId());
        adminJdbc().update("insert into receipt (id, organization_id, payment_id, unit_id, number, code) values (?, ?, ?, ?, 1, 'ABCDEFGHJKLMNPQR')", UUID.randomUUID(), t.orgId(), payment, t.unitId());
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "ctcore_app", "synthetic_runtime"); Statement sql = app.createStatement()) {
            app.setAutoCommit(false);
            sql.execute("select set_config('app.organization_id', '" + t.orgId() + "', true)");
            for (String denied : new String[] {"delete from charge", "delete from payment", "delete from receipt", "delete from receipt_counter",
                    "update payment set amount_cents = 1", "update receipt set number = 9"}) {
                var savepoint = app.setSavepoint();
                assertThatThrownBy(() -> sql.executeUpdate(denied)).as(denied).hasMessageContaining("permission denied");
                app.rollback(savepoint);
            }
            assertThat(sql.executeUpdate("update charge set status = 'CANCELED' where id = '" + charge + "'")).isEqualTo(1);
            app.rollback();
        }
    }

    @Test
    @DisplayName("CA1: tipo e situação fora da lista, meio inválido, valor negativo e segundo pagamento da mesma cobrança são recusados pelo banco")
    void checks() {
        var t = tenant();
        UUID student = BillingFixtures.student(adminJdbc(), t.orgId(), t.unitId(), "Maria Souza");
        String insert = "insert into charge (id, organization_id, unit_id, student_id, type, due_on, amount_cents, status) values (?, ?, ?, ?, ?, date '2026-11-10', ?, ?)";
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), student, "OTHER", 1, "OPEN")).hasMessageContaining("charge_type_check");
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), student, "ONE_OFF", 1, "OVERDUE")).hasMessageContaining("charge_status_check");
        assertThatThrownBy(() -> adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), student, "ONE_OFF", -1, "OPEN")).hasMessageContaining("charge_amount_cents_check");
        for (String type : new String[] {"MONTHLY", "ENROLLMENT_FEE", "ONE_OFF", "INSTALLMENT", "READJUSTMENT_DIFF"}) {
            for (String status : new String[] {"OPEN", "IN_REVIEW", "PAID", "CANCELED", "RENEGOTIATED", "REFUNDED"}) {
                assertThat(adminJdbc().update(insert, UUID.randomUUID(), t.orgId(), t.unitId(), student, type, 1, status)).isEqualTo(1);
            }
        }
        UUID charge = BillingFixtures.oneOff(adminJdbc(), t.orgId(), t.unitId(), student, 100, "OPEN");
        String pay = "insert into payment (id, organization_id, charge_id, student_id, unit_id, amount_cents, method, origin, paid_at) values (?, ?, ?, ?, ?, 100, ?, 'MANAGEMENT', now())";
        assertThatThrownBy(() -> adminJdbc().update(pay, UUID.randomUUID(), t.orgId(), charge, student, t.unitId(), "BITCOIN")).hasMessageContaining("payment_method_check");
        adminJdbc().update(pay, UUID.randomUUID(), t.orgId(), charge, student, t.unitId(), "CASH");
        assertThatThrownBy(() -> adminJdbc().update(pay, UUID.randomUUID(), t.orgId(), charge, student, t.unitId(), "PIX_KEY")).hasMessageContaining("payment_one_confirmed");
    }
}
