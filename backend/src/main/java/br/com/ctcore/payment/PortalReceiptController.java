package br.com.ctcore.payment;

import br.com.ctcore.identity.AccountPrincipal;
import br.com.ctcore.student.PortalTerms;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RF-POR-05, RN-COB-16: recibo no portal, só de aluno que a conta paga, com o CPF mascarado; depois do portão do termo. */
@RestController
@RequestMapping("/api/v1/portal/receipts")
class PortalReceiptController {
    private final ReceiptFiles files;
    private final PortalTerms terms;

    PortalReceiptController(ReceiptFiles files, PortalTerms terms) {
        this.files = files;
        this.terms = terms;
    }

    @GetMapping("/{code}/pdf")
    ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AccountPrincipal who, @PathVariable String code) throws IOException {
        terms.requireAccepted(who);
        return files.portalPdf(who, code);
    }
}
