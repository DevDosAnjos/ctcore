package br.com.ctcore.payment;

import br.com.ctcore.identity.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** RN-COB-12: a organização e a unidade vêm da sessão e da cobrança; o valor nunca vem do cliente (só é conferido). */
@RestController
@RequestMapping("/api/v1/charges/{id}/payments")
class PaymentController {
    private final Payments payments;

    PaymentController(Payments payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Payments.Registered register(@AuthenticationPrincipal AccountPrincipal who, @PathVariable UUID id, @RequestBody Payments.Input input, HttpServletRequest request) {
        return payments.register(who, id, input, request.getRemoteAddr());
    }
}
