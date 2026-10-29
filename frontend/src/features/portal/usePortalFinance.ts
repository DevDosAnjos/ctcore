import { useResource } from "@/features/students/useStudentRecord";
import type { HomeRef, HomeStudent } from "./usePortalHome";

// Espelham PortalFinance do OpenAPI (docs/openapi.yaml). Tudo calculado no servidor (RN-POR-02); sem dado de cartão nem chave de arquivo.
export type FinanceScope = "STUDENT" | "FAMILY";
export type FinanceSituation = "OVERDUE" | "DUE_TODAY" | "DUE_SOON" | "PENDING" | "IN_REVIEW";
export type HistoryStatus = "PAID" | "REFUNDED" | "CANCELED" | "RENEGOTIATED";
export interface FinanceCharge {
  id: string;
  studentId: string;
  studentName: string;
  type: "MONTHLY" | "ENROLLMENT_FEE" | "ONE_OFF" | "INSTALLMENT" | "READJUSTMENT_DIFF";
  description: string | null;
  referenceLabel: string;
  referenceMonth: string | null;
  dueOn: string;
  situation: FinanceSituation;
  amountCents: number;
  planCents: number;
  benefitCents: number;
  canPay: boolean;
  modality: HomeRef | null;
  unit: HomeRef;
}
export interface FinanceHistoryItem {
  id: string;
  referenceLabel: string;
  dueOn: string;
  amountCents: number;
  status: HistoryStatus;
  paidAt: string | null;
  method: string | null;
  receipt: { number: number; code: string } | null;
}
export interface PortalFinanceData {
  reduced: boolean;
  students: HomeStudent[];
  selectedStudentId: string | null;
  scope: FinanceScope;
  year: number;
  years: number[];
  familyOpenCents: number;
  open: FinanceCharge[];
  history: FinanceHistoryItem[];
}

/** Financeiro do aluno escolhido (nulo: o primeiro cartão), na visão da família ou de um ano do histórico. Escolhas na URL (D8). */
export function usePortalFinance(studentId: string | null, scope: FinanceScope, year: string | null) {
  const q = new URLSearchParams();
  if (studentId) q.set("studentId", studentId);
  if (scope === "FAMILY") q.set("scope", scope);
  if (year) q.set("year", year);
  const s = q.toString();
  return useResource<PortalFinanceData>(`/portal/finance${s ? `?${s}` : ""}`);
}

/** Link do PDF do recibo (RN-COB-16). */
export const portalReceiptUrl = (code: string) => `/api/v1/portal/receipts/${encodeURIComponent(code)}/pdf`;
