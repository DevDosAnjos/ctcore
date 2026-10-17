package br.com.ctcore.identity;

import java.time.Instant;

/** E-mails de conta (#19: enviados pelo EmailService, com registro e novas tentativas). */
public interface AccountMailer {
    void invite(String to, String code);

    void passwordReset(String to, String code);

    void passwordChanged(String to);

    /** RN-ACE-22; {@code city} is null when unknown. */
    default void newAccess(String to, String device, String city, Instant at) {}
}
