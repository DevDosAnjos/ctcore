import type { Query, Page } from "@/components/query/QueryPage";
import { api } from "@/lib/api";
import type { PaymentProof, PaymentProofConfirmed } from "./types";

// Query da tela (RNF-27) -> GET /payment-proofs: só os pendentes, do mais antigo ao mais novo.
export async function fetchPaymentProofs(q: Query): Promise<Page<PaymentProof>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: PaymentProof[]; total: number }>(`/payment-proofs?${p}`);
  return { rows: data.items, total: data.total };
}

export const confirmProof = (id: string, sendReceipt: boolean) => api<PaymentProofConfirmed>(`/payment-proofs/${id}/confirm`, { method: "POST", body: { sendReceipt } });
export const refuseProof = (id: string, reason: string) => api<PaymentProof>(`/payment-proofs/${id}/refuse`, { method: "POST", body: { reason } });
