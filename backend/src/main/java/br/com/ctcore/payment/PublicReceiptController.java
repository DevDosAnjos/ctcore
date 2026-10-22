package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RN-COB-16: conferência pública do recibo, sem sessão. Lê só a função {@code public_receipt} do banco, que devolve unidade, número, referência, valor, data
 * e iniciais; código desconhecido ou malformado é sempre 404. Não audita.
 */
@RestController
@RequestMapping("/api/v1/public/receipts")
class PublicReceiptController {
    record PublicReceipt(String unit, int number, String reference, long amountCents, OffsetDateTime paidAt, String payerInitials) {}

    private final JdbcClient jdbc;

    PublicReceiptController(@Lazy JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/{code}")
    PublicReceipt get(@PathVariable String code) {
        return jdbc.sql("select unit_name, number, reference, amount_cents, paid_at, payer_initials from public_receipt(:code)").param("code", code)
                .query((rs, n) -> new PublicReceipt(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getLong(4), rs.getObject(5, OffsetDateTime.class), rs.getString(6)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }
}
