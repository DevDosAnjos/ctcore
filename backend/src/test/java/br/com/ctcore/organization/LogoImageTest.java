package br.com.ctcore.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogoImageTest {
    static byte[] image(String format, int w, int h) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    /** Cabeçalho WebP mínimo; a leitura só olha o cabeçalho. */
    static byte[] webp(String chunk, int w, int h) {
        var b = new byte[40];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
        b[4] = (byte) (b.length - 8);
        System.arraycopy("WEBPVP8".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 7);
        b[15] = (byte) chunk.charAt(0);
        switch (chunk) {
            case " " -> { b[23] = (byte) 0x9D; b[24] = 0x01; b[25] = 0x2A; b[26] = (byte) w; b[27] = (byte) (w >> 8); b[28] = (byte) h; b[29] = (byte) (h >> 8); }
            case "L" -> { b[20] = 0x2F; long bits = (w - 1) | ((long) (h - 1) << 14); for (int i = 0; i < 4; i++) b[21 + i] = (byte) (bits >> (8 * i)); }
            default -> { b[24] = (byte) (w - 1); b[25] = (byte) ((w - 1) >> 8); b[27] = (byte) (h - 1); b[28] = (byte) ((h - 1) >> 8); }
        }
        return b;
    }

    @Test
    @DisplayName("CA8: PNG, JPEG e WebP (VP8, VP8L, VP8X) de 512 x 512 são aceitos")
    void accepts() throws Exception {
        assertThat(LogoImage.parse(image("png", 512, 512)).extension()).isEqualTo("png");
        assertThat(LogoImage.parse(image("jpg", 512, 512)).contentType()).isEqualTo("image/jpeg");
        for (String chunk : new String[] {" ", "L", "X"}) {
            assertThat(LogoImage.parse(webp(chunk, 512, 512)).extension()).as(chunk).isEqualTo("webp");
        }
    }

    @Test
    @DisplayName("CA9: outra dimensão é recusada nos três formatos")
    void wrongSize() throws Exception {
        for (int[] s : new int[][] {{511, 512}, {1024, 1024}}) {
            assertThatThrownBy(() -> LogoImage.parse(image("png", s[0], s[1]))).hasMessageContaining("512");
            assertThatThrownBy(() -> LogoImage.parse(image("jpg", s[0], s[1]))).hasMessageContaining("512");
            for (String chunk : new String[] {" ", "L", "X"}) {
                assertThatThrownBy(() -> LogoImage.parse(webp(chunk, s[0], s[1]))).hasMessageContaining("512");
            }
        }
    }

    @Test
    @DisplayName("CA9: texto com nome .png, GIF, vazio, acima de 5 MB e arquivo cortado são recusados")
    void notAnImage() throws Exception {
        assertThatThrownBy(() -> LogoImage.parse("hello".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogoImage.parse(image("gif", 512, 512))).hasMessageContaining("PNG");
        assertThatThrownBy(() -> LogoImage.parse(new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LogoImage.parse(new byte[LogoImage.MAX_BYTES + 1])).hasMessageContaining("5 MB");
        byte[] png = image("png", 512, 512);
        assertThatThrownBy(() -> LogoImage.parse(Arrays.copyOf(png, png.length / 2))).hasMessageContaining("corrompida");
        assertThatThrownBy(() -> LogoImage.parse("<svg/>".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }
}
