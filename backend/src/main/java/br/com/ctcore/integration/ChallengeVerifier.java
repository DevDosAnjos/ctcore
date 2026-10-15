package br.com.ctcore.integration;

public interface ChallengeVerifier {
    default boolean isEnabled() { return true; }
    boolean verify(String token);
}
