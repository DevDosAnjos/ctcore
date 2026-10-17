package br.com.ctcore.identity;

import java.util.Optional;

/** Approximate city of an IP. Until Sprint 22 the only bean is {@link NoIpLocator} (D5). */
public interface IpLocator {
    Optional<String> city(String ip);
}
