package br.com.ctcore.identity;

/** E-mails de conta; #19 troca o miolo por modelos sem mudar quem chama. */
public interface AccountMailer {
    void invite(String to, String code);

    void passwordReset(String to, String code);

    void passwordChanged(String to);
}
