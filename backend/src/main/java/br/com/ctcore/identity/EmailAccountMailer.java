package br.com.ctcore.identity;

import br.com.ctcore.integration.EmailSender;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class EmailAccountMailer implements AccountMailer {
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private final EmailSender email;
    private final MessageSource messages;
    private final String baseUrl;

    public EmailAccountMailer(EmailSender email, MessageSource messages, @Value("${ctcore.app.base-url}") String baseUrl) {
        this.email = email;
        this.messages = messages;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public void invite(String to, String code) {
        send(to, "mail.invite", baseUrl + "/convite/" + code);
    }

    @Override
    public void passwordReset(String to, String code) {
        send(to, "mail.reset", baseUrl + "/redefinir-senha/" + code);
    }

    @Override
    public void passwordChanged(String to) {
        send(to, "mail.password-changed");
    }

    private void send(String to, String key, Object... args) {
        email.send(to, messages.getMessage(key + ".subject", null, PT_BR), messages.getMessage(key + ".body", args, PT_BR));
    }
}
