package br.com.ctcore.identity;

import java.time.Instant;

/** E-mails de conta (#19: enviados pelo EmailService, com registro e novas tentativas). */
public interface AccountMailer {
    void invite(String to, String code);

    void passwordReset(String to, String code);

    void passwordChanged(String to);

    /** RN-ACE-15: link sent to the new address. */
    default void emailChange(String to, String code) {}

    /** RN-ACE-15: notice to the old address after the change. */
    default void emailChanged(String to) {}

    /** RN-ACE-22; {@code city} is null when unknown. */
    default void newAccess(String to, String device, String city, Instant at) {}
}
