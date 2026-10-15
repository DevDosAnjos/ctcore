package br.com.ctcore.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/** Caixa de saída em memória (D4): guarda as mensagens simuladas, no máximo {@value #LIMIT}; as mais antigas saem. */
public class Outbox {

    public static final int LIMIT = 1000;

    public enum Type { EMAIL, SMS, PUSH }

    public record Message(UUID id, Type type, String recipient, String subject, String content, Instant at) {}

    private final Clock clock;
    private final ArrayDeque<Message> messages = new ArrayDeque<>();

    public Outbox(Clock clock) {
        this.clock = clock;
    }

    public synchronized Message add(Type type, String recipient, String subject, String content) {
        Message m = new Message(UUID.randomUUID(), type, recipient, subject, content, clock.instant());
        if (messages.size() >= LIMIT) messages.removeFirst();
        messages.addLast(m);
        return m;
    }

    /** Da mais antiga para a mais nova. */
    public synchronized List<Message> list() {
        return List.copyOf(messages);
    }

    public synchronized void clear() {
        messages.clear();
    }
}
