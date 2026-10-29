package br.com.ctcore.integration;

/**
 * Transição inválida: IllegalStateException; cobrança inexistente: NoSuchElementException.
 * Desligada ({@code isEnabled()} falso), as operações lançam IllegalStateException: quem chama consulta antes.
 */
public interface PaymentGateway {
    default boolean isEnabled() { return true; }

    /** Idempotente: mesma chave devolve a mesma cobrança; mesma chave com outro valor lança IllegalArgumentException. */
    PaymentCharge createCharge(String idempotencyKey, long amountCents, PaymentMethod method);

    /** Estado atual no provedor, sem alterá-lo (confirmação do aviso e conciliação, RN-PAG-10). */
    PaymentCharge consult(String id);

    PaymentCharge approve(String id);
    PaymentCharge refuse(String id);
    PaymentCharge expire(String id);
    PaymentCharge refund(String id);
    PaymentCharge dispute(String id);

    /** Repete o aviso do estado atual, sem alterá-lo. */
    PaymentCharge resendNotice(String id);
}
