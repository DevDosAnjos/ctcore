import { api } from "@/lib/api";
import { useResource } from "@/features/students/useStudentRecord";
import type { GenerationPreview } from "./types";

export const PERIODS = [10, 30, 60] as const;
// Sem unidade escolhida não há pedido (a rota exige `unitId`).
export const useChargePreview = (unitId: string | null, days: number) =>
  useResource<GenerationPreview>(unitId ? `/billing/generation-preview?unitId=${unitId}&days=${days}` : null);
const base = (studentId: string, rowId: string) => `/students/${studentId}/modalities/${rowId}/next-charge`;
export const changeNextDue = (studentId: string, rowId: string, dueOn: string) => api<{ dueOn: string; referenceMonth: string }>(base(studentId, rowId), { method: "PATCH", body: { dueOn } });
export const cancelNext = (studentId: string, rowId: string, reason: string, referenceMonth: string) =>
  api<{ chargeId: string; referenceMonth: string }>(`${base(studentId, rowId)}/cancel`, { method: "POST", body: { reason, referenceMonth } });
