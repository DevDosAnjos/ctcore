package br.com.ctcore.organization;

import java.util.regex.Pattern;

/** RNF-22: cor principal {@code #RRGGBB} com contraste mínimo AA (4,5:1) contra texto branco. */
public final class BrandColor {
    public static final double MIN_CONTRAST = 4.5;
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    private BrandColor() {}

    /** Razão WCAG entre a cor e o branco, de 1 a 21. */
    public static double contrastWithWhite(String hex) {
        double l = luminance(hex);
        return 1.05 / (l + 0.05);
    }

    /** Em branco vira null; senão devolve minúsculas ou lança IllegalArgumentException com a mensagem do campo. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String hex = raw.trim();
        if (!HEX.matcher(hex).matches()) throw new IllegalArgumentException("use o formato #RRGGBB");
        if (contrastWithWhite(hex) < MIN_CONTRAST) throw new IllegalArgumentException("contraste com texto branco abaixo de 4,5:1");
        return hex.toLowerCase();
    }

    private static double luminance(String hex) {
        double sum = 0;
        double[] weight = {0.2126, 0.7152, 0.0722};
        for (int i = 0; i < 3; i++) {
            double c = Integer.parseInt(hex.substring(1 + 2 * i, 3 + 2 * i), 16) / 255.0;
            sum += weight[i] * (c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4));
        }
        return sum;
    }
}
