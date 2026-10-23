package br.com.ctcore.payment;

import br.com.ctcore.identity.AccountPrincipal;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RN-COB-16: PDF do recibo com o CPF completo, só com sessão e acesso a cobranças da unidade do recibo. */
@RestController
@RequestMapping("/api/v1/receipts")
class ReceiptStaffController {
    private final ReceiptFiles files;

    ReceiptStaffController(ReceiptFiles files) {
        this.files = files;
    }

    @GetMapping("/{code}/pdf")
    ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String code) throws IOException {
        return files.staffPdf(who, code);
    }
}
