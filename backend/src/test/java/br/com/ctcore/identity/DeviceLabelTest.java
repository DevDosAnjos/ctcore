package br.com.ctcore.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceLabelTest {
    @Test
    @DisplayName("CA8: navegador e sistema por trechos conhecidos; o resto é aparelho desconhecido")
    void labels() {
        assertThat(DeviceLabel.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"))
                .isEqualTo("Chrome em Windows");
        assertThat(DeviceLabel.of("Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/126.0 Safari/537.36 Edg/126.0")).isEqualTo("Edge em Windows");
        assertThat(DeviceLabel.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605 Version/17.0 Safari/605.1.15")).isEqualTo("Safari em macOS");
        assertThat(DeviceLabel.of("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605 Version/17.0 Safari/604.1")).isEqualTo("Safari em iOS");
        assertThat(DeviceLabel.of("Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0")).isEqualTo("Firefox em Linux");
        assertThat(DeviceLabel.of("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0 Mobile Safari/537.36")).isEqualTo("Chrome em Android");
        assertThat(DeviceLabel.of("curl/8.0")).isEqualTo("Aparelho desconhecido");
        assertThat(DeviceLabel.of(null)).isEqualTo("Aparelho desconhecido");
    }
}
