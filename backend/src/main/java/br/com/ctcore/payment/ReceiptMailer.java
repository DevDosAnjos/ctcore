package br.com.ctcore.payment;

import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** RF-COB-04 (D2, D7): manda o link do PDF ao pagador depois do commit; falha do envio nunca desfaz o pagamento. Nem o endereço nem o texto vão a log. */
@Component
class ReceiptMailer {
    private final EmailService email;
    private final JdbcClient jdbc;
    private final String baseUrl;

    ReceiptMailer(EmailService email, @Lazy JdbcClient jdbc, @Value("${ctcore.app.base-url}") String baseUrl) {
        this.email = email;
        this.jdbc = jdbc;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    /** SENT, NO_ADDRESS ou FAILED (mensagem guardada mas ainda não entregue: o {@code EmailRetryJob} tenta de novo). */
    String send(java.util.UUID organizationId, String to, String code, String reference, int number) {
        if (to == null || !to.contains("@")) return "NO_ADDRESS";
        try {
            UUID id = email.send(organizationId, to.strip(), EmailTemplate.PAYMENT_CONFIRMED, baseUrl + "/api/v1/public/receipts/" + code + "/pdf", reference, number);
            String status = jdbc.sql("select status from email_message where id = :id").param("id", id).query(String.class).single();
            if (status.equals("SKIPPED")) return "NO_ADDRESS"; // RN-ALU-10 (D7): endereço "Com problema"
            return Set.of("PENDING", "FAILED").contains(status) ? "FAILED" : "SENT";
        } catch (RuntimeException e) {
            return "FAILED";
        }
    }
}
