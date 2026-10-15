package br.com.ctcore.integration;

public interface UsageMetrics {
    default boolean isEnabled() { return true; }
    void record(String event);
}
