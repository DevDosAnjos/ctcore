package br.com.ctcore.benefit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.ctcore.api.error.ApiValidationException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RF-BEN-02: data efetiva (RN-BEN-04), "alto" (RN-BEN-06) e validação (RN-BEN-02/03), sem banco. */
class BenefitWriteTest {
    @Test
    @DisplayName("CA5: vence no primeiro dia do vencimento depois de hoje + 10 dias; virada de mês e de ano")
    void effectiveFrom() {
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 5), 10)).isEqualTo(YearMonth.of(2026, 4));
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 15), 10)).isEqualTo(YearMonth.of(2026, 4));
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 1), 12)).isEqualTo(YearMonth.of(2026, 3)); // limite dia 11, vence dia 12
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 1), 11)).isEqualTo(YearMonth.of(2026, 4)); // limite dia 11: não é depois
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 25), 20)).isEqualTo(YearMonth.of(2026, 4)); // limite 4/4
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 3, 25), 28)).isEqualTo(YearMonth.of(2026, 4)); // limite 4/4, vence 28/4
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 12, 25), 10)).isEqualTo(YearMonth.of(2027, 1)); // limite 4/1 -> 10/1
        assertThat(BenefitWrite.effectiveFrom(LocalDate.of(2026, 12, 31), 10)).isEqualTo(YearMonth.of(2027, 2)); // limite 10/1 -> 10/2
    }

    @Test
    @DisplayName("CA6: plano de R$ 150 com bolsa 30%, desconto R$ 50, R$ 200 e bolsa 100%")
    void monthlyAndHigh() {
        assertThat(Benefits.monthlyValueCents("PERCENT", 30, 15000)).isEqualTo(10500);
        assertThat(Benefits.monthlyValueCents("AMOUNT", 5000, 15000)).isEqualTo(10000);
        assertThat(Benefits.monthlyValueCents("AMOUNT", 20000, 15000)).isZero();
        assertThat(Benefits.monthlyValueCents("PERCENT", 100, 15000)).isZero();
        assertThat(BenefitWrite.high("PERCENT", 30, 15000)).isFalse();
        assertThat(BenefitWrite.high("PERCENT", 50, 15000)).isFalse();
        assertThat(BenefitWrite.high("PERCENT", 51, 15000)).isTrue();
        assertThat(BenefitWrite.high("AMOUNT", 5000, 15000)).isFalse();
        assertThat(BenefitWrite.high("AMOUNT", 7500, 15000)).isFalse();
        assertThat(BenefitWrite.high("AMOUNT", 7501, 15000)).isTrue();
        assertThat(BenefitWrite.high("AMOUNT", 20000, 15000)).isTrue();
    }

    private static void rejects(String type, String value, String field) {
        assertThatThrownBy(() -> BenefitWrite.value(type, new BigDecimal(value))).isInstanceOf(ApiValidationException.class);
        assertThat(field).isEqualTo("value");
    }

    @Test
    @DisplayName("CA3: tipo, valor e textos inválidos; limites aceitos")
    void validation() {
        assertThatThrownBy(() -> BenefitWrite.type("DESCONTO")).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> BenefitWrite.type(null)).isInstanceOf(ApiValidationException.class);
        assertThat(BenefitWrite.type(" percent ")).isEqualTo("PERCENT");
        for (String v : new String[] {"0", "101", "10.5", "-1"}) rejects("PERCENT", v, "value");
        for (String v : new String[] {"0", "100000000", "1.5"}) rejects("AMOUNT", v, "value");
        assertThatThrownBy(() -> BenefitWrite.value("AMOUNT", null)).isInstanceOf(ApiValidationException.class);
        assertThat(BenefitWrite.value("PERCENT", new BigDecimal("1"))).isEqualTo(1);
        assertThat(BenefitWrite.value("PERCENT", new BigDecimal("100.0"))).isEqualTo(100);
        assertThat(BenefitWrite.value("AMOUNT", new BigDecimal("99999999"))).isEqualTo(99_999_999L);
        assertThatThrownBy(() -> BenefitWrite.text("reason", "", 2, 200)).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> BenefitWrite.text("reason", " a ", 2, 200)).isInstanceOf(ApiValidationException.class);
        assertThatThrownBy(() -> BenefitWrite.text("reason", "x".repeat(201), 2, 200)).isInstanceOf(ApiValidationException.class);
        assertThat(BenefitWrite.text("reason", "  ok  ", 2, 200)).isEqualTo("ok");
        assertThat(BenefitWrite.text("reason", "x".repeat(200), 2, 200)).hasSize(200);
    }
}
