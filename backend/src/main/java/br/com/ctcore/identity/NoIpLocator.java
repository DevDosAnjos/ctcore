package br.com.ctcore.identity;

import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class NoIpLocator implements IpLocator {
    @Override
    public Optional<String> city(String ip) {
        return Optional.empty();
    }
}
