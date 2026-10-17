package br.com.ctcore.identity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** RN-ACE-08: 8+ characters, one digit, one uppercase letter, no symbol required, not in the common-passwords list (D2). */
@Component
public class PasswordPolicy {
    public enum Violation { WEAK, LEAKED }

    private final Set<String> common = load();

    /** Null when the password is acceptable. */
    public Violation check(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.codePoints().noneMatch(Character::isDigit)
                || password.codePoints().noneMatch(Character::isUpperCase)) {
            return Violation.WEAK;
        }
        return common.contains(password.toLowerCase(Locale.ROOT)) ? Violation.LEAKED : null;
    }

    private static Set<String> load() {
        try (var in = new BufferedReader(new InputStreamReader(
                new ClassPathResource("passwords/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            Set<String> words = new HashSet<>();
            in.lines().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).forEach(words::add);
            return Set.copyOf(words);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
