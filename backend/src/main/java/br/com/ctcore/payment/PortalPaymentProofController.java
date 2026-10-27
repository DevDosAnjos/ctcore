package br.com.ctcore.payment;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** RN-COB-13, RN-POR-01: Pix na chave com comprovante, só para quem paga (aluno adulto e responsável financeiro), depois do portão do termo. Uma cobrança ou várias da mesma unidade. */
@RestController
@RequestMapping("/api/v1/portal/charges")
class PortalPaymentProofController {
    private final PaymentProofs proofs;

    PortalPaymentProofController(PaymentProofs proofs) {
        this.proofs = proofs;
    }

    @GetMapping("/{id}/pix-instructions")
    PaymentProofs.Instructions instructions(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return proofs.instructions(who, id);
    }

    @GetMapping("/pix-instructions")
    PaymentProofs.BatchInstructions instructions(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) List<UUID> ids) {
        return proofs.instructions(who, ids);
    }

    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    PaymentProofs.Sent send(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestPart(value = "file", required = false) MultipartFile file,
            HttpServletRequest request) throws IOException {
        return proofs.send(who, id, file == null ? new byte[0] : file.getBytes(), request.getRemoteAddr());
    }

    @PostMapping(value = "/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    List<PaymentProofs.Sent> send(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(value = "chargeIds", required = false) List<UUID> chargeIds,
            @RequestPart(value = "file", required = false) MultipartFile file, HttpServletRequest request) throws IOException {
        return proofs.send(who, chargeIds, file == null ? new byte[0] : file.getBytes(), request.getRemoteAddr());
    }
}
