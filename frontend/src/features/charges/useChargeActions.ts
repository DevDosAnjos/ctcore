import { api } from "@/lib/api";
import { useResource } from "@/features/students/useStudentRecord";
import type { ChargeDetail, ChargeList, ChargeRow, ReminderResult, RenegotiateInput } from "./types";

export const useChargeDetail = (id: string) => useResource<ChargeDetail>(`/charges/${id}`);
export const cancelCharge = (id: string, reason: string) => api<ChargeDetail>(`/charges/${id}/cancel`, { method: "POST", body: { reason } });
export const renegotiateCharges = (body: RenegotiateInput) => api<ChargeRow[]>("/charges/renegotiate", { method: "POST", body });
// Cobranças de um aluno (aba Financeiro e selo da ficha); `enabled` falso não faz pedido (Professor, D6).
export const useStudentCharges = (studentId: string, enabled: boolean) => useResource<ChargeList>(enabled ? `/charges?studentId=${studentId}&size=100` : null);
// RF-COB-06: envia o lembrete por e-mail das cobranças e devolve o resultado de cada uma, na ordem do pedido.
export const sendReminders = (chargeIds: string[]) => api<{ results: ReminderResult[] }>("/charges/reminders", { method: "POST", body: { chargeIds } }).then((r) => r.results);
