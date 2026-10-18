package br.com.ctcore.organization;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Logo da organização (RF-ORG-01): tipo pelos bytes (PNG, JPEG, WebP), exatamente 512 x 512 e até 5 MB; o nome e o tipo enviados não contam. */
public record LogoImage(String extension, String contentType, byte[] bytes) {
    public static final int SIZE = 512;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** Lança IllegalArgumentException com a mensagem do campo se o arquivo não for uma logo aceita. */
    public static LogoImage parse(byte[] b) {
        if (b == null || b.length == 0) throw new IllegalArgumentException("arquivo vazio");
        if (b.length > MAX_BYTES) throw new IllegalArgumentException("arquivo acima de 5 MB");
        int[] size;
        LogoImage kind;
        if (startsWith(b, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            kind = new LogoImage("png", "image/png", b);
            size = decoded(b);
        } else if (startsWith(b, 0xFF, 0xD8, 0xFF)) {
            kind = new LogoImage("jpg", "image/jpeg", b);
            size = decoded(b);
        } else if (startsWith(b, 'R', 'I', 'F', 'F') && b.length > 30 && at(b, 8, 'W', 'E', 'B', 'P')) {
            kind = new LogoImage("webp", "image/webp", b);
            size = webp(b);
        } else {
            throw new IllegalArgumentException("use PNG, JPG ou WebP");
        }
        if (size == null) throw new IllegalArgumentException("imagem corrompida");
        if (size[0] != SIZE || size[1] != SIZE) throw new IllegalArgumentException("a imagem precisa ter 512 x 512 pixels");
        return kind;
    }

    /** PNG e JPEG: decodifica de verdade, assim arquivo cortado é recusado. */
    private static int[] decoded(byte[] b) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(b));
            return image == null ? null : new int[] {image.getWidth(), image.getHeight()};
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ponytail: WebP só pelo cabeçalho (VP8, VP8L, VP8X) e pelo tamanho do RIFF; não decodifica os pixels.
    private static int[] webp(byte[] b) {
        long riff = le(b, 4, 4) + 8;
        if (riff != b.length) return null;
        if (at(b, 12, 'V', 'P', '8', ' ') && at(b, 23, 0x9D, 0x01, 0x2A)) {
            return new int[] {(int) (le(b, 26, 2) & 0x3FFF), (int) (le(b, 28, 2) & 0x3FFF)};
        }
        if (at(b, 12, 'V', 'P', '8', 'L') && b[20] == 0x2F) {
            long bits = le(b, 21, 4);
            return new int[] {(int) (bits & 0x3FFF) + 1, (int) ((bits >> 14) & 0x3FFF) + 1};
        }
        if (at(b, 12, 'V', 'P', '8', 'X')) {
            return new int[] {(int) le(b, 24, 3) + 1, (int) le(b, 27, 3) + 1};
        }
        return null;
    }

    private static long le(byte[] b, int from, int n) {
        long v = 0;
        for (int i = n - 1; i >= 0; i--) v = (v << 8) | (b[from + i] & 0xFF);
        return v;
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        return at(b, 0, prefix);
    }

    private static boolean at(byte[] b, int from, int... expected) {
        if (b.length < from + expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((b[from + i] & 0xFF) != expected[i]) return false;
        return true;
    }
}
