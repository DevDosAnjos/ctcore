package br.com.ctcore.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Relógio injetável: as regras usam este bean, nunca {@code Instant.now()}. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
