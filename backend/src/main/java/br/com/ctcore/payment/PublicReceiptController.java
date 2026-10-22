package br.com.ctcore.payment;

import br.com.ctcore.api.error.ApiStatusException;
import br.com.ctcore.api.error.ErrorCode;
import br.com.ctcore.isolation.OrganizationContext;
import br.com.ctcore.isolation.OrganizationPrincipal;
import br.com.ctcore.storage.FileStorage;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RN-COB-16: conferência pública do recibo, sem sessão. Lê só as funções {@code public_receipt} (página) e {@code receipt_pdf_data} (PDF) do banco;
 * código desconhecido ou malformado é sempre 404. Não audita. O PDF é capacidade por código de 80 bits (D1): traz o CPF de quem pagou.
 */
@RestController
@RequestMapping("/api/v1/public/receipts")
class PublicReceiptController {
    record PublicReceipt(String unit, int number, String reference, long amountCents, OffsetDateTime paidAt, String payerInitials) {}

    private final JdbcClient jdbc;
    private final FileStorage storage;

    PublicReceiptController(@Lazy JdbcClient jdbc, @Lazy FileStorage storage) {
        this.jdbc = jdbc;
        this.storage = storage;
    }

    @GetMapping("/{code}")
    PublicReceipt get(@PathVariable String code) {
        return jdbc.sql("select unit_name, number, reference, amount_cents, paid_at, payer_initials from public_receipt(:code)").param("code", code)
                .query((rs, n) -> new PublicReceipt(rs.getString(1), rs.getInt(2), rs.getString(3), rs.getLong(4), rs.getObject(5, OffsetDateTime.class), rs.getString(6)))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
    }

    @GetMapping("/{code}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable String code) throws java.io.IOException {
        record Row(UUID organization, ReceiptPdf.Data data) {}
        Row row = jdbc.sql("select organization_id, logo, unit_name, cnpj, address, timezone, number, code, amount_cents, fee_passed_cents, method, paid_at, "
                + "student_name, payer_name, payer_cpf, reference from receipt_pdf_data(:code)").param("code", code)
                .query((rs, n) -> new Row(rs.getObject(1, UUID.class), new ReceiptPdf.Data(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                        rs.getInt(7), rs.getString(8), rs.getLong(9), rs.getLong(10), rs.getString(11), rs.getObject(12, OffsetDateTime.class), rs.getString(13),
                        rs.getString(14), rs.getString(15), rs.getString(16))))
                .optional().orElseThrow(() -> new ApiStatusException(ErrorCode.NOT_FOUND));
        byte[] pdf = ReceiptPdf.render(row.data(), logo(row.organization(), row.data().logo()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff").header("Content-Disposition", "inline; filename=\"recibo-" + row.data().number() + ".pdf\"").body(pdf);
    }

    /** A logo é lida no contexto da organização do recibo, fechado no fim (nada vaza para a próxima requisição). Sem logo ou com falha, o PDF sai sem ela. */
    private byte[] logo(UUID organization, String name) {
        if (name == null) return null;
        try (var ignored = OrganizationContext.open(new OrganizationPrincipal() {
            @Override public UUID organizationId() { return organization; }
            @Override public UUID unitId() { return null; }
            @Override public Set<UUID> authorizedUnitIds() { return Set.of(); }
        })) {
            return storage.get(name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
