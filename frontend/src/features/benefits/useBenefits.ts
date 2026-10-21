import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";

export { usePlanFilterOptions as useBenefitFilterOptions } from "@/features/plans/usePlans";

interface Choice {
  id: string;
  name: string;
}

// Espelha BenefitRow do OpenAPI (docs/openapi.yaml). `value`: percentual inteiro em PERCENT, centavos em AMOUNT.
export interface BenefitRow {
  id: string;
  student: Choice;
  unit: Choice;
  modality: Choice;
  type: "PERCENT" | "AMOUNT";
  value: number;
  reason: string;
  decidedBy: string;
  grantedBy: string | null;
  validUntil: string | null;
  status: "ACTIVE" | "EXPIRED" | "ENDED";
  high: boolean;
  monthlyValueCents: number;
}

// Query da tela (RNF-27) -> GET /benefits; erro sobe com o status (403 vira "sem permissão").
export async function fetchBenefits(q: Query): Promise<Page<BenefitRow>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  if (q.q) p.set("q", q.q);
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: BenefitRow[]; total: number }>(`/benefits?${p}`);
  return { rows: data.items, total: data.total };
}
