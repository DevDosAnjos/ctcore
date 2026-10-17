package br.com.ctcore.identity;

import br.com.ctcore.mail.EmailService;
import br.com.ctcore.mail.EmailTemplate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

@Component
public class EmailAccountMailer implements AccountMailer {
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", PT_BR)
            .withZone(ZoneId.of("America/Sao_Paulo"));
    private final EmailService email;
    private final MessageSource messages;
    private final String baseUrl;

    public EmailAccountMailer(EmailService email, MessageSource messages, @Value("${ctcore.app.base-url}") String baseUrl) {
        this.email = email;
        this.messages = messages;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override
    public void invite(String to, String code) {
        email.send(to, EmailTemplate.INVITE, baseUrl + "/convite/" + code);
    }

    @Override
    public void passwordReset(String to, String code) {
        email.send(to, EmailTemplate.PASSWORD_RESET, baseUrl + "/redefinir-senha/" + code);
    }

    @Override
    public void passwordChanged(String to) {
        email.send(to, EmailTemplate.PASSWORD_CHANGED);
    }

    @Override
    public void newAccess(String to, String device, String city, Instant at) {
        String where = city != null ? city : messages.getMessage("mail.new-access.unknown-city", null, PT_BR);
        email.send(to, EmailTemplate.NEW_ACCESS, device, where, WHEN.format(at));
    }
}
