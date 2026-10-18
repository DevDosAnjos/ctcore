package br.com.ctcore.identity;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-256-GCM for the TOTP secret (D7). The key comes from the environment; without it the application does not start. */
@Component
public class SecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretCipher(@Value("${ctcore.security.two-factor-key:}") String base64Key) {
        byte[] raw = base64Key == null || base64Key.isBlank() ? new byte[0] : Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != 32) {
            throw new IllegalStateException("TWO_FACTOR_ENCRYPTION_KEY must be 32 bytes in base64");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(byte[] plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] body = cipher.doFinal(plain);
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Throws when the text was not made with this key. */
    public byte[] decrypt(String text) {
        try {
            byte[] in = Base64.getDecoder().decode(text);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, 12));
            return cipher.doFinal(in, 12, in.length - 12);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot decrypt two-factor secret", e);
        }
    }
}
