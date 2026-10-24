import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";
import type { OverdueCharge, OverdueList } from "./types";

// Ordem da tela ("campo" ou "-campo") -> sort da rota; sem escolha vale o padrão do servidor (mais dias de atraso primeiro).
const sortOf = (ordem: string) => (ordem ? `${ordem.replace(/^-/, "")},${ordem.startsWith("-") ? "desc" : "asc"}` : "daysLate,desc");

// Query da tela (RNF-27) -> GET /charges/overdue; erro sobe com o status (403 vira "sem permissão").
export async function fetchOverdue(q: Query): Promise<Page<OverdueCharge>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho), sort: sortOf(q.ordem) });
  if (q.q) p.set("q", q.q);
  if (q.filtros.unitId) p.set("unitId", q.filtros.unitId);
  const data = await api<OverdueList>(`/charges/overdue?${p}`);
  return { rows: data.items, total: data.total };
}
