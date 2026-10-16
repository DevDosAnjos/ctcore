package br.com.ctcore.audit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** RN-AUD-03: drops secret keys at any depth, then keeps only the fields that changed (D3, D6). */
final class AuditSanitizer {
    // ponytail: substring match on a normalized key; over-removes names like "tokenizedAt" on purpose.
    private static final List<String> SENSITIVE = List.of("password", "senha", "token", "secret", "apikey",
            "cardnumber", "cvv", "cvc", "securitycode", "expiry");

    private AuditSanitizer() {}

    static boolean sensitive(String key) {
        String k = key.toLowerCase().replaceAll("[^a-z0-9]", "");
        return SENSITIVE.stream().anyMatch(k::contains);
    }

    static Object clean(Object value) {
        if (value instanceof Map<?, ?> map) {
            var out = new LinkedHashMap<String, Object>();
            map.forEach((k, v) -> {
                if (!sensitive(String.valueOf(k))) out.put(String.valueOf(k), clean(v));
            });
            return out;
        }
        if (value instanceof Iterable<?> items) {
            var out = new ArrayList<Object>();
            items.forEach(i -> out.add(clean(i)));
            return out;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cleanMap(Map<String, Object> m) {
        return m == null ? null : (Map<String, Object>) clean(m);
    }

    /** Returns {before, after}; with both sides present, only keys whose values differ. */
    static List<Map<String, Object>> diff(Map<String, Object> before, Map<String, Object> after) {
        var b = cleanMap(before);
        var a = cleanMap(after);
        if (b == null || a == null) return Arrays.asList(b, a);
        var keys = new LinkedHashSet<>(b.keySet());
        keys.addAll(a.keySet());
        var rb = new LinkedHashMap<String, Object>();
        var ra = new LinkedHashMap<String, Object>();
        for (String key : keys) {
            if (b.containsKey(key) && a.containsKey(key) && Objects.equals(b.get(key), a.get(key))) continue;
            if (b.containsKey(key)) rb.put(key, b.get(key));
            if (a.containsKey(key)) ra.put(key, a.get(key));
        }
        return List.of(rb, ra);
    }
}
