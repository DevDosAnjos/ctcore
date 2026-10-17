package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TotpTest {
    private static final byte[] RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("RN-ACE-17: CA3: vetores da RFC 6238 (SHA-1, 8 dígitos)")
    void rfcVectors() {
        long[][] times = {{59, 0}, {1111111109L, 0}, {1111111111L, 0}, {1234567890L, 0}, {2000000000L, 0}, {20000000000L, 0}};
        String[] expected = {"94287082", "07081804", "14050471", "89005924", "69279037", "65353130"};
        for (int i = 0; i < times.length; i++) {
            assertThat(Totp.code(RFC, Totp.step(Instant.ofEpochSecond(times[i][0])), 8)).isEqualTo(expected[i]);
        }
    }

    @Test
    @DisplayName("RN-ACE-17: CA3: aceita o passo anterior e o seguinte, não dois de diferença")
    void window() {
        Instant now = Instant.ofEpochSecond(1_000_000_000L);
        long step = Totp.step(now);
        assertThat(Totp.match(RFC, Totp.code(RFC, step), now)).hasValue(step);
        assertThat(Totp.match(RFC, Totp.code(RFC, step - 1), now)).hasValue(step - 1);
        assertThat(Totp.match(RFC, Totp.code(RFC, step + 1), now)).hasValue(step + 1);
        assertThat(Totp.match(RFC, Totp.code(RFC, step - 2), now)).isEmpty();
        assertThat(Totp.match(RFC, Totp.code(RFC, step + 2), now)).isEmpty();
    }

    @Test
    @DisplayName("Base32 faz o caminho de ida e volta")
    void base32() {
        byte[] secret = Totp.newSecret();
        assertThat(Base32.decode(Base32.encode(secret))).isEqualTo(secret);
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Base32.decode("1!")).isInstanceOf(IllegalArgumentException.class);
    }
}
