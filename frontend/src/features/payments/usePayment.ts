import { api } from "@/lib/api";

// Espelham PaymentInput e Payment do OpenAPI (RN-COB-12, #71). Valores em centavos.
export type PaymentMethod = "CASH" | "PIX_KEY" | "CARD_MACHINE" | "TRANSFER";
export const PAYMENT_METHODS: PaymentMethod[] = ["CASH", "PIX_KEY", "CARD_MACHINE", "TRANSFER"];
export interface PaymentReceipt {
  chargeId: string;
  receipt: { number: number; code: string };
}

// O valor nunca vai do cliente: o servidor cobra o total da cobrança em aberto.
export const registerPayment = (chargeId: string, method: PaymentMethod) => api<PaymentReceipt>(`/charges/${chargeId}/payments`, { method: "POST", body: { method } });
