import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";
import type { AnnouncementItem, AnnouncementList } from "./types";

// Ordem da tela ("campo" ou "-campo") -> sort da rota; sem escolha vale o padrão do servidor (mais recente primeiro).
const sortOf = (ordem: string) => (ordem ? `${ordem.replace(/^-/, "")},${ordem.startsWith("-") ? "desc" : "asc"}` : "date,desc");

// Query da tela (RNF-27) -> GET /announcements; erro sobe com o status (403 vira "sem permissão").
export async function fetchAnnouncements(q: Query): Promise<Page<AnnouncementItem>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho), sort: sortOf(q.ordem) });
  for (const k of ["from", "to", "classId", "type"]) if (q.filtros[k]) p.set(k, q.filtros[k]);
  const data = await api<AnnouncementList>(`/announcements?${p}`);
  return { rows: data.items, total: data.total };
}

// RN-COM-06: só o agendado se cancela; o enviado volta 409.
export const cancelAnnouncement = (id: string) => api<void>(`/announcements/${id}`, { method: "DELETE" });
