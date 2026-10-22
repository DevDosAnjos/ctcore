import { api } from "@/lib/api";

// Espelham PaymentInput e Payment do OpenAPI (RN-COB-12, #71, #75). Valores em centavos.
export type PaymentMethod = "CASH" | "PIX_KEY" | "CARD_MACHINE" | "TRANSFER";
export const PAYMENT_METHODS: PaymentMethod[] = ["CASH", "PIX_KEY", "CARD_MACHINE", "TRANSFER"];
export type ReceiptEmail = "SENT" | "NO_ADDRESS" | "NOT_REQUESTED" | "FAILED";
export interface PaymentReceipt {
  chargeId: string;
  receipt: { number: number; code: string };
  receiptEmail?: ReceiptEmail;
}
export interface PaymentInput {
  method: PaymentMethod;
  paidAt?: string;
  observation?: string;
  amountCents: number;
  sendReceipt: boolean;
}

export const registerPayment = (chargeId: string, input: PaymentInput) => api<PaymentReceipt>(`/charges/${chargeId}/payments`, { method: "POST", body: input });

/** Endereço do PDF do recibo (RN-COB-16): público, sem sessão, não expira. */
export const receiptPdfUrl = (code: string) => `/api/v1/public/receipts/${encodeURIComponent(code)}/pdf`;
