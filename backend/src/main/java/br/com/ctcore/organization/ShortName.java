package br.com.ctcore.organization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** RN-ORG-15 (D3): nome curto em minúsculas, sem acento, só {@code a-z0-9} e hífen, até 60 caracteres. */
public final class ShortName {
    static final int MAX = 60;
    private static final Pattern FORMAT = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private ShortName() {}

    /** Gera do nome; em colisão ({@code taken}) acrescenta {@code -2}, {@code -3}... */
    public static String generate(String name, Predicate<String> taken) {
        String base = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.length() > MAX - 5) base = base.substring(0, MAX - 5).replaceAll("-$", "");
        if (base.isEmpty()) base = "unidade";
        String candidate = base;
        for (int n = 2; taken.test(candidate); n++) candidate = base + "-" + n;
        return candidate;
    }

    /** Devolve o valor se o formato é válido; senão lança {@link IllegalArgumentException} com o motivo. */
    public static String validate(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("obrigatório");
        if (value.length() > MAX) throw new IllegalArgumentException("no máximo 60 caracteres");
        if (!FORMAT.matcher(value).matches()) throw new IllegalArgumentException("use só letras minúsculas sem acento, números e hífen");
        return value;
    }
}
