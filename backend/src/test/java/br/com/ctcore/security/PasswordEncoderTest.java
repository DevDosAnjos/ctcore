package br.com.ctcore.security;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderTest {

    private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @Test
    @DisplayName("CA12 / RNF-04 / RNF-29: Argon2id, confere a senha certa, recusa a errada, hashes distintos")
    void argon2id() {
        String hash = encoder.encode("senha-de-teste-123");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(encoder.matches("senha-de-teste-123", hash)).isTrue();
        assertThat(encoder.matches("outra-senha", hash)).isFalse();
        assertThat(encoder.encode("senha-de-teste-123")).isNotEqualTo(hash);
    }
}
