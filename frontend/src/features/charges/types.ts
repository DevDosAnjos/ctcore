// Espelha ChargeRow, ChargeList e ChargeSituation do OpenAPI (RF-COB-01). Nenhum campo de juros ou multa (RN-COB-07).
export type ChargeSituation = "PENDING" | "DUE_TODAY" | "OVERDUE" | "IN_REVIEW" | "PAID" | "PAID_LATE" | "CANCELED" | "RENEGOTIATED" | "REFUNDED";
export type ChargeType = "MONTHLY" | "ENROLLMENT_FEE" | "ONE_OFF" | "INSTALLMENT" | "READJUSTMENT_DIFF";
export type PaymentMethod = "PIX_MP" | "CREDIT_CARD" | "DEBIT_CARD" | "BOLETO" | "PIX_KEY" | "CASH" | "CARD_MACHINE" | "TRANSFER";
export type ChargeQuick = "OVERDUE" | "DUE_TODAY" | "IN_REVIEW" | "PAID_MONTH";

export const SITUATIONS: ChargeSituation[] = ["PENDING", "DUE_TODAY", "OVERDUE", "IN_REVIEW", "PAID", "PAID_LATE", "CANCELED", "RENEGOTIATED", "REFUNDED"];
export const TYPES: ChargeType[] = ["MONTHLY", "ENROLLMENT_FEE", "ONE_OFF", "INSTALLMENT", "READJUSTMENT_DIFF"];
export const QUICK: ChargeQuick[] = ["OVERDUE", "DUE_TODAY", "IN_REVIEW", "PAID_MONTH"];

export interface ChargeRow {
  id: string;
  student: { id: string; name: string; photoUrl: string | null };
  unit: { id: string; name: string };
  type: ChargeType;
  modality: { id: string; name: string } | null;
  referenceMonth: string | null;
  description: string | null;
  amountCents: number;
  dueOn: string;
  status: ChargeSituation;
  payment: { method: PaymentMethod; paidAt: string } | null;
}

export interface ChargeList {
  items: ChargeRow[];
  page: number;
  size: number;
  total: number;
  counts: { overdue: number; dueToday: number; inReview: number; paidMonth: number };
  totals: { count: number; amountCents: number };
}
