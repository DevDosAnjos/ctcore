package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import java.time.OffsetDateTime;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RN-COB-16: conferência pública do recibo, sem sessão. Lê só as funções {@code public_receipt} (página) e {@code receipt_pdf_data} (PDF) do banco;
 * código desconhecido ou malformado é sempre 404. Não audita. O PDF é capacidade por código de 80 bits (D1): traz o CPF de quem pagou só mascarado; o completo está em {@code /api/v1/receipts/{code}/pdf}, com sessão.
 */
@RestController
@RequestMapping("/api/v1/public/receipts")
class PublicReceiptController {
    record PublicReceipt(String unit, int number, String reference, long amountCents, OffsetDateTime paidAt, String payerInitials) {}

    private final JdbcClient jdbc;
    private final ReceiptFiles files;

    PublicReceiptController(@Lazy JdbcClient jdbc, ReceiptFiles files) {
        this.jdbc = jdbc;
        this.files = files;
    }

    @GetMapping("/{code}")
    PublicReceipt get(@PathVariable String code) {
        return jdbc.sql("select unit_name, number, reference, amount_cents, paid_at, payer_initials from public_receipt(:code)").param("code", code)
                .query((rs, n) -> new PublicReceipt(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getLong(4), rs.getObject(5, OffsetDateTime.class), rs.getString(6)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    @GetMapping("/{code}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable String code) throws java.io.IOException {
        return files.publicPdf(code);
    }
}
