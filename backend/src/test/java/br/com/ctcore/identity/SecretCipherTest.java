package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecretCipherTest {
    private static String key(int fill) {
        byte[] k = new byte[32];
        java.util.Arrays.fill(k, (byte) fill);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    @DisplayName("RN-ACE-17: CA4: o texto guardado não traz o segredo em claro e só a mesma chave o lê")
    void roundTripAndKeyChange() {
        byte[] secret = "segredo-em-claro".getBytes();
        SecretCipher a = new SecretCipher(key(1));
        String stored = a.encrypt(secret);
        assertThat(stored).doesNotContain("segredo").doesNotContain(Base64.getEncoder().encodeToString(secret));
        assertThat(a.decrypt(stored)).isEqualTo(secret);
        assertThat(a.encrypt(secret)).isNotEqualTo(stored);
        assertThatThrownBy(() -> new SecretCipher(key(2)).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RN-ACE-17: CA4: sem chave, ou com chave de tamanho errado, não sobe")
    void requiresKey() {
        assertThatThrownBy(() -> new SecretCipher("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16]))).isInstanceOf(IllegalStateException.class);
    }
}
