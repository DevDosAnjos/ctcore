import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";
import type { ChargeList, ChargeRow } from "./types";

// Ordem da tela ("campo" ou "-campo") -> sort da rota; sem escolha vale o padrão do servidor (vencimento, mais recente primeiro).
const sortOf = (ordem: string) => (ordem ? `${ordem.replace(/^-/, "")},${ordem.startsWith("-") ? "desc" : "asc"}` : "dueOn,desc");

// Query da tela (RNF-27) -> GET /charges. `counts` leva os atalhos e os totais do rodapé (totalCount, totalCents); erro sobe com o status (403 vira "sem permissão").
export async function fetchCharges(q: Query): Promise<Page<ChargeRow>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho), sort: sortOf(q.ordem) });
  if (q.q) p.set("q", q.q);
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<ChargeList>(`/charges?${p}`);
  return { rows: data.items, total: data.total, counts: { ...data.counts, totalCount: data.totals.count, totalCents: data.totals.amountCents } };
}
