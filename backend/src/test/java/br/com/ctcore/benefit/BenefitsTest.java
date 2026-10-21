package br.com.ctcore.benefit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-BEN-07 (CA5): a mensalidade com benefício nunca é negativa; método único reutilizável. */
class BenefitsTest {
    @Test
    @DisplayName("CA5: plano de R$ 150 com desconto de R$ 200, bolsa de 100%, desconto de R$ 50 e bolsa de 30%")
    void monthlyValue() {
        assertThat(Benefits.monthlyValueCents("AMOUNT", 20000, 15000)).isZero();
        assertThat(Benefits.monthlyValueCents("PERCENT", 100, 15000)).isZero();
        assertThat(Benefits.monthlyValueCents("AMOUNT", 5000, 15000)).isEqualTo(10000);
        assertThat(Benefits.monthlyValueCents("PERCENT", 30, 15000)).isEqualTo(10500);
    }

    @Test
    @DisplayName("limites: desconto igual ao plano zera; 1% de valor pequeno arredonda a favor do cálculo; nunca negativo")
    void limits() {
        assertThat(Benefits.monthlyValueCents("AMOUNT", 15000, 15000)).isZero();
        assertThat(Benefits.monthlyValueCents("AMOUNT", 14999, 15000)).isEqualTo(1);
        assertThat(Benefits.monthlyValueCents("PERCENT", 1, 15000)).isEqualTo(14850);
        assertThat(Benefits.monthlyValueCents("PERCENT", 50, 101)).isEqualTo(50);
        assertThat(Benefits.monthlyValueCents("AMOUNT", 1, 0)).isZero();
    }
}
