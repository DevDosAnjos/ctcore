package br.com.ctcore.billing;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.ctcore.billing.ChargePricing.BenefitTerms;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-COB-03 (CA2): composição do valor da mensalidade. */
class ChargePricingTest {
    static final UUID PLAN = UUID.randomUUID(), BENEFIT = UUID.randomUUID();
    static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

    @Test
    @DisplayName("CA2: plano de R$ 200,00 com 25% vale 15000, com plano e benefício gravados")
    void percent() {
        var c = ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "PERCENT", 25, null, false), TODAY);
        assertThat(c).isEqualTo(new ChargePricing.Composition(PLAN, 20000, BENEFIT, 5000, 15000));
    }

    @Test
    @DisplayName("CA2: sem benefício, o valor é o do plano; benefício fixo maior que o plano dá zero; bolsa de 100% dá zero")
    void noBenefitAndFloor() {
        assertThat(ChargePricing.compose(PLAN, 20000, null, TODAY)).isEqualTo(new ChargePricing.Composition(PLAN, 20000, null, 0, 20000));
        var fixed = ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "AMOUNT", 25000, null, false), TODAY);
        assertThat(fixed.amountCents()).isZero();
        assertThat(fixed.benefitCents()).isEqualTo(20000);
        assertThat(ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "PERCENT", 100, null, false), TODAY).amountCents()).isZero();
        assertThat(ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "AMOUNT", 3000, null, false), TODAY).amountCents()).isEqualTo(17000);
    }

    @Test
    @DisplayName("CA2: benefício vencido ou encerrado não desconta; válido até hoje ainda desconta")
    void validity() {
        assertThat(ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "PERCENT", 25, TODAY.minusDays(1), false), TODAY).amountCents()).isEqualTo(20000);
        assertThat(ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "PERCENT", 25, null, true), TODAY)).isEqualTo(new ChargePricing.Composition(PLAN, 20000, null, 0, 20000));
        assertThat(ChargePricing.compose(PLAN, 20000, new BenefitTerms(BENEFIT, "PERCENT", 25, TODAY, false), TODAY).amountCents()).isEqualTo(15000);
    }
}
