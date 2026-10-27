package br.com.ctcore.payment;

import br.com.ctcore.api.page.PageRequest;
import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** RF-COB-05: comprovantes em conferência da gestão (cobranças completo na unidade e alcance de unidade). */
@RestController
@RequestMapping("/api/v1/payment-proofs")
class PaymentProofController {
    record ConfirmBody(Boolean sendReceipt) {}

    record RefuseBody(String reason) {}

    private final PaymentProofs proofs;

    PaymentProofController(PaymentProofs proofs) {
        this.proofs = proofs;
    }

    @GetMapping
    PaymentProofs.ProofList list(@AuthenticationPrincipal AccountPrincipal who, @RequestParam(required = false) UUID unitId, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return proofs.list(who, unitId, PageRequest.of(page, size, null, Set.of()));
    }

    @GetMapping("/{id}")
    PaymentProofs.Proof get(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id) {
        return proofs.get(who, id);
    }

    @PostMapping("/{id}/confirm")
    PaymentProofs.Confirmed confirm(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody(required = false) ConfirmBody body,
            HttpServletRequest request) {
        return proofs.confirm(who, id, body != null && Boolean.TRUE.equals(body.sendReceipt()), request.getRemoteAddr());
    }

    @PostMapping("/{id}/refuse")
    PaymentProofs.Proof refuse(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody(required = false) RefuseBody body,
            HttpServletRequest request) {
        return proofs.refuse(who, id, body == null ? null : body.reason(), request.getRemoteAddr());
    }
}
