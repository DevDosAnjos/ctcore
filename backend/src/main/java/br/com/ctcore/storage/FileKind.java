package br.com.ctcore.storage;

import java.nio.charset.StandardCharsets;

/** Tipo do arquivo pelo conteúdo, nunca pelo nome: JPG, PNG, WebP ou PDF. */
public record FileKind(String extension, String contentType) {

    /** Nulo se o conteúdo não é um dos quatro tipos aceitos. */
    public static FileKind of(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return new FileKind("jpg", "image/jpeg");
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return new FileKind("png", "image/png");
        if (b.length >= 12 && new String(b, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF") && new String(b, 8, 4, StandardCharsets.ISO_8859_1).equals("WEBP")) {
            return new FileKind("webp", "image/webp");
        }
        if (b.length >= 5 && new String(b, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) return new FileKind("pdf", "application/pdf");
        return null;
    }
}
