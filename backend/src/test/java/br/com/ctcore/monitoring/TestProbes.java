package br.com.ctcore.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rotas que só existem nos testes: uma que loga, uma que falha, abertas fora da cadeia principal. */
@TestConfiguration
public class TestProbes {

    @RestController
    static class Probe {
        private static final Logger log = LoggerFactory.getLogger("probe");

        @GetMapping("/probe/ok")
        String ok() {
            log.info("legit id=12345 ref=550e8400-e29b-41d4-a716-446655440000");
            log.info("senha do usuario password={}", "SenhaSecreta123");
            return "ok";
        }

        @GetMapping("/probe/fail")
        String fail() {
            throw new IllegalStateException("falhou token=TokenSecreto999 cpf 123.456.789-09");
        }
    }

    @Bean
    @Order(0)
    SecurityFilterChain probeChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/probe/**")
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
