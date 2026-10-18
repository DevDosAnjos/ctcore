package br.com.ctcore.organization;

/** Titular da organização: CPF ou CNPJ com dígitos verificadores válidos, guardado só com dígitos (mesma regra do isValidCpf do front). */
public final class HolderDocument {
    private HolderDocument() {}

    /** Em branco vira null; senão devolve só os dígitos ou lança IllegalArgumentException com a mensagem do campo. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String masked = raw.trim();
        if (!masked.matches("[0-9.\\-/ ]+")) throw new IllegalArgumentException("informe só números");
        String d = masked.replaceAll("[^0-9]", "");
        boolean ok = d.length() == 11 ? validCpf(d) : d.length() == 14 && validCnpj(d);
        if (!ok) throw new IllegalArgumentException("CPF ou CNPJ inválido");
        return d;
    }

    private static boolean validCpf(String d) {
        if (d.chars().distinct().count() == 1) return false;
        return check(d, 9, 10) == digit(d, 9) && check(d, 10, 11) == digit(d, 10);
    }

    private static boolean validCnpj(String d) {
        if (d.chars().distinct().count() == 1) return false;
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return weighted(d, w1) == digit(d, 12) && weighted(d, w2) == digit(d, 13);
    }

    private static int check(String d, int len, int start) {
        int sum = 0;
        for (int i = 0; i < len; i++) sum += digit(d, i) * (start - i);
        int r = sum * 10 % 11;
        return r == 10 ? 0 : r;
    }

    private static int weighted(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) sum += digit(d, i) * weights[i];
        int r = sum % 11;
        return r < 2 ? 0 : 11 - r;
    }

    private static int digit(String d, int i) {
        return d.charAt(i) - '0';
    }
}
