package br.com.ctcore.identity;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238: HMAC-SHA1, 6 digits, 30 s step, one step of tolerance each way (RN-ACE-17). */
final class Totp {
    static final int STEP_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    static byte[] newSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    static long step(Instant at) {
        return Math.floorDiv(at.getEpochSecond(), STEP_SECONDS);
    }

    static String code(byte[] secret, long step) {
        return code(secret, step, 6);
    }

    static String code(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + digits + "d", bin % (int) Math.pow(10, digits));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The step whose code equals {@code code} (previous, current or next), compared in constant time. */
    static OptionalLong match(byte[] secret, String code, Instant now) {
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        long current = step(now);
        OptionalLong found = OptionalLong.empty();
        for (long s = current - 1; s <= current + 1; s++) {
            if (MessageDigest.isEqual(given, code(secret, s).getBytes(StandardCharsets.UTF_8))) {
                found = OptionalLong.of(s);
            }
        }
        return found;
    }
}
