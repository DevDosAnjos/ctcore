package br.com.ctcore.organization;

import java.util.Locale;
import java.util.regex.Pattern;

/** Chave Pix da unidade (D6): CPF, CNPJ, e-mail, telefone (+55 e dígitos) ou chave aleatória (UUID), guardada normalizada. */
public final class PixKey {
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");
    private static final Pattern PHONE = Pattern.compile("\\+55[1-9][1-9](9\\d{8}|\\d{8})");

    private PixKey() {}

    /** Em branco vira null; chave inválida lança IllegalArgumentException. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.strip();
        if (v.contains("@")) {
            if (v.length() > 77 || !EMAIL.matcher(v).matches()) throw invalid();
            return v.toLowerCase(Locale.ROOT);
        }
        if (v.startsWith("+")) {
            String phone = "+" + v.substring(1).replaceAll("[\\s()-]", "");
            if (!PHONE.matcher(phone).matches()) throw invalid();
            return phone;
        }
        if (UUID.matcher(v).matches()) return v.toLowerCase(Locale.ROOT);
        try {
            return HolderDocument.normalize(v);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("chave Pix inválida");
    }
}
