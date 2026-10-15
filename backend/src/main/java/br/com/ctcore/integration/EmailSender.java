package br.com.ctcore.integration;

public interface EmailSender {
    default boolean isEnabled() { return true; }
    SendResult send(String to, String subject, String body);
}
