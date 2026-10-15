package br.com.ctcore.integration;

/** Cobrança e, ao mesmo tempo, o evento do aviso: id, estado e valor em centavos. */
public record PaymentCharge(String id, long amountCents, PaymentStatus status) {}
