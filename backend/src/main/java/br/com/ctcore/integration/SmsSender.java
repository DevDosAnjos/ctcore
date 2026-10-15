package br.com.ctcore.integration;

public interface SmsSender {
    default boolean isEnabled() { return true; }
    /** O código de confirmação vai dentro do texto; a versão simulada o deixa visível na Caixa de saída. */
    SendResult send(String to, String text);
}
