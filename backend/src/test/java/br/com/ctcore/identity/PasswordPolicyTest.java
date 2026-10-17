package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.config.SecurityConfig;
import br.com.ctcore.identity.PasswordPolicy.Violation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    @DisplayName("RN-ACE-08: CA7: sem maiúscula, sem número e curta são fracas")
    void weak() {
        assertThat(policy.check("abcdefg1")).isEqualTo(Violation.WEAK);
        assertThat(policy.check("Abcdefgh")).isEqualTo(Violation.WEAK);
        assertThat(policy.check("Ab1")).isEqualTo(Violation.WEAK);
        assertThat(policy.check("Abcdef1")).isEqualTo(Violation.WEAK);
        assertThat(policy.check(null)).isEqualTo(Violation.WEAK);
    }

    @Test
    @DisplayName("RN-ACE-08: CA7: senha da lista de vazadas é recusada, ignorando maiúsculas")
    void leaked() {
        assertThat(policy.check("Password1")).isEqualTo(Violation.LEAKED);
        assertThat(policy.check("PASSWORD123")).isEqualTo(Violation.LEAKED);
    }

    @Test
    @DisplayName("RN-ACE-08: CA7: 8 caracteres com número e maiúscula passam, sem símbolo")
    void accepted() {
        assertThat(policy.check("Abcdefg1")).isNull();
        assertThat(policy.check("Ação12345")).isNull();
    }

    @Test
    @DisplayName("RN-ACE-09: CA8: a senha é guardada com Argon2id")
    void storedAsArgon2id() {
        assertThat(new SecurityConfig().passwordEncoder().encode("Abcdefg1")).startsWith("$argon2id$");
    }
}
