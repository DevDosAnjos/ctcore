package br.com.ctcore.payment;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RN-PAG-10: aviso (webhook) do provedor de pagamento. Sem sessão; só o campo {@code id} é lido, qualquer outro é ignorado e nada do corpo é devolvido. */
@RestController
@RequestMapping("/api/v1/webhooks/payments")
class PaymentWebhookController {
    record Body(String id) {}

    private final PaymentNotices notices;

    PaymentWebhookController(PaymentNotices notices) {
        this.notices = notices;
    }

    @PostMapping
    Map<String, Object> receive(@RequestBody Body body) {
        notices.receive(body.id());
        return Map.of();
    }
}
