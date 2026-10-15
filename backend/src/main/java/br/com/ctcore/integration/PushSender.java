package br.com.ctcore.integration;

public interface PushSender {
    default boolean isEnabled() { return true; }
    SendResult send(String to, String title, String body);
}
