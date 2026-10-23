package br.com.ctcore.payment;

import java.security.SecureRandom;

/** RN-COB-16: código de conferência de 16 caracteres (80 bits), sem caracteres ambíguos (0, O, 1, I), não enumerável. */
final class ReceiptCodes {
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ReceiptCodes() {}

    static String next() {
        StringBuilder code = new StringBuilder(16);
        for (int i = 0; i < 16; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }
}
