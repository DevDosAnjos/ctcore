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

// Espelha ChargeDetail do OpenAPI (RF-COB-02). Valores em centavos; `reminders` vem vazio até o item de lembretes.
export type StoredStatus = "OPEN" | "IN_REVIEW" | "PAID" | "CANCELED" | "RENEGOTIATED" | "REFUNDED";
export interface ChargeRef {
  id: string;
  type: ChargeType;
  referenceMonth: string | null;
  dueOn: string;
  amountCents: number;
  status: StoredStatus;
}
export interface ChargePayment {
  id: string;
  amountCents: number;
  method: PaymentMethod;
  origin: "MANAGEMENT" | "PORTAL" | "AUTO_DEBIT";
  paidAt: string;
  registeredBy: { id: string; name: string | null } | null;
  observation: string | null;
  status: "CONFIRMED" | "REFUNDED";
  receipt: { number: number; code: string } | null;
}
export interface ChargeDetail {
  charge: ChargeRow;
  toleranceDays: number;
  composition: {
    plan: { id: string; name: string } | null;
    planCents: number;
    benefitType: string | null;
    benefitCents: number;
    amountCents: number;
  };
  payer: { id: string; name: string; kind: "STUDENT" | "GUARDIAN" };
  origin: ChargeRef | null;
  replacedBy: ChargeRef[];
  reason: string | null;
  canceledAt: string | null;
  canceledBy: { id: string; name: string | null } | null;
  payments: ChargePayment[];
  reminders: unknown[];
  actions: { cancel: boolean; renegotiate: boolean; registerPayment: boolean; sendReminder: boolean; refund: boolean };
}
export interface RenegotiateInput {
  chargeIds: string[];
  amountCents?: number;
  firstDueOn: string;
  installments: number;
  reason: string;
}

// Espelha GenerationPreview do OpenAPI (RF-COB-03). `adjustable`: só a próxima mensalidade de cada modalidade aceita troca de vencimento e cancelamento.
export interface PreviewLine {
  studentId: string;
  student: string;
  modalityRowId: string;
  modality: string;
  plan: string;
  discountCents: number;
  amountCents: number;
  referenceMonth: string;
  adjustable: boolean;
}
export interface PreviewDay {
  dueOn: string;
  count: number;
  totalCents: number;
  lines: PreviewLine[];
}
export interface PreviewSkipped {
  studentId: string;
  student: string;
  modality: string;
  referenceMonth: string;
  reason: string;
}
export interface GenerationPreview {
  until: string;
  days: PreviewDay[];
  count: number;
  totalCents: number;
  previousMonthTotalCents: number;
  skipped: PreviewSkipped[];
}

// Espelha OneOffInput e ChargeCreated do OpenAPI (RN-COB-11, RN-COB-06).
export interface OneOffInput {
  requestId: string;
  unitId: string;
  description: string;
  amountCents: number;
  dueOn: string;
}
export interface ChargeCreated {
  id: string;
  modalityRowId: string;
  type: "MONTHLY";
  unitId: string;
  referenceMonth: string;
  dueOn: string;
  toleranceDays: number;
  amountCents: number;
}

// Espelha DailyReceipts do OpenAPI (RF-COB-08).
export interface DailyReceiptsPerson {
  id: string;
  name: string;
}
export interface DailyReceipts {
  date: string;
  totalCents: number;
  count: number;
  byMethod: { method: string; count: number; totalCents: number }[];
  byRegistrar: { registeredBy: DailyReceiptsPerson | null; count: number; totalCents: number }[];
  payments: {
    id: string;
    paidAt: string;
    student: DailyReceiptsPerson;
    reference: string;
    method: string;
    registeredBy: DailyReceiptsPerson | null;
    amountCents: number;
    receiptNumber: number;
  }[];
}
