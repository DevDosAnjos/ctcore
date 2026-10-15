package br.com.ctcore.integration;

import java.util.Optional;

public interface MapLocator {
    record Coordinates(double latitude, double longitude) {}

    default boolean isEnabled() { return true; }
    Optional<Coordinates> locate(String cep);
}
