package br.com.ctcore.billing;

import br.com.ctcore.benefit.Benefits;
import java.time.LocalDate;
import java.util.UUID;

/** RN-COB-03: valor da mensalidade = plano menos o benefício válido na data, nunca abaixo de zero. Função pura; a composição é gravada na cobrança. */
public final class ChargePricing {
    /** Benefício da modalidade; {@code ended} é o benefício encerrado (status ENDED). */
    public record BenefitTerms(UUID id, String type, long value, LocalDate validUntil, boolean ended) {}

    /** {@code benefitId} só vem preenchido quando o benefício entrou na conta. */
    public record Composition(UUID planId, long planCents, UUID benefitId, long benefitCents, long amountCents) {}

    private ChargePricing() {}

    public static Composition compose(UUID planId, long planCents, BenefitTerms benefit, LocalDate on) {
        if (benefit == null || benefit.ended() || (benefit.validUntil() != null && benefit.validUntil().isBefore(on))) {
            return new Composition(planId, planCents, null, 0, planCents);
        }
        long amount = Benefits.monthlyValueCents(benefit.type(), benefit.value(), planCents);
        return new Composition(planId, planCents, benefit.id(), planCents - amount, amount);
    }
}
