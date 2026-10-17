package br.com.ctcore.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditSanitizerTest {

    @Test
    @DisplayName("CA2, RN-AUD-02: guarda só os campos alterados")
    void onlyChangedFields() {
        var r = AuditSanitizer.diff(Map.of("nome", "Ana", "faixa", "branca"), Map.of("nome", "Ana", "faixa", "azul"));
        assertThat(r.get(0)).isEqualTo(Map.of("faixa", "branca"));
        assertThat(r.get(1)).isEqualTo(Map.of("faixa", "azul"));
    }

    @Test
    @DisplayName("CA3: criação guarda só depois; exclusão só antes, com todos os campos")
    void creationAndDeletion() {
        var created = AuditSanitizer.diff(null, Map.of("nome", "Ana", "faixa", "branca"));
        assertThat(created.get(0)).isNull();
        assertThat(created.get(1)).containsEntry("nome", "Ana").containsEntry("faixa", "branca");
        var deleted = AuditSanitizer.diff(Map.of("nome", "Ana"), null);
        assertThat(deleted.get(0)).containsEntry("nome", "Ana");
        assertThat(deleted.get(1)).isNull();
    }

    @Test
    @DisplayName("CA5, RN-AUD-03: chaves sensíveis saem em qualquer nível; as demais ficam")
    void sensitiveKeysRemoved() {
        var data = new java.util.LinkedHashMap<String, Object>();
        for (String k : List.of("password", "senha", "token", "accessToken", "secret", "apiKey")) data.put(k, "x");
        data.put("cardNumber", "4111");
        data.put("cvv", "123");
        data.put("expiry", "12/30");
        data.put("nome", "Ana");
        data.put("nested", Map.of("API_KEY", "k", "ok", 1));
        data.put("list", List.of(Map.of("Token", "t", "ok", 2)));
        var out = AuditSanitizer.diff(null, data).get(1);
        assertThat(out.keySet()).containsExactlyInAnyOrder("nome", "nested", "list");
        assertThat(out.get("nested")).isEqualTo(Map.of("ok", 1));
        assertThat(out.get("list")).isEqualTo(List.of(Map.of("ok", 2)));
        assertThat(out.toString()).doesNotContain("4111", "12/30");
    }
}
