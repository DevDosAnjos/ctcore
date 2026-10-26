import { useEffect, useState } from "react";
import type { Page, Query } from "@/components/query/QueryPage";
import { api } from "@/lib/api";
import type { PortalRequest } from "./types";

// Query da tela (RNF-27) -> GET /portal-requests. Sem `status` valem os abertos; o servidor ordena do mais antigo ao mais novo.
export async function fetchPortalRequests(q: Query): Promise<Page<PortalRequest>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: PortalRequest[]; total: number }>(`/portal-requests?${p}`);
  return { rows: data.items, total: data.total };
}

// Aprovar só marca o pedido (D4); a matrícula muda quando a gestão conclui o fluxo. Recusar exige mensagem.
export const approveRequest = (id: string, message: string) => api<PortalRequest>(`/portal-requests/${id}/approve`, { method: "POST", body: message ? { message } : {} });
export const refuseRequest = (id: string, message: string) => api<PortalRequest>(`/portal-requests/${id}/refuse`, { method: "POST", body: { message } });

// Pedido de um aluno, para a ficha abrir a troca já preenchida (R18). Falha deixa `undefined` e a ficha segue sem preencher.
export function usePortalRequest(id: string | null) {
  const [found, setFound] = useState<{ id: string; request?: PortalRequest }>();
  useEffect(() => {
    if (!id) return;
    let alive = true;
    api<PortalRequest>(`/portal-requests/${id}`).then(
      (request) => alive && setFound({ id, request }),
      () => alive && setFound({ id }),
    );
    return () => {
      alive = false;
    };
  }, [id]);
  return { loading: !!id && found?.id !== id, request: found?.id === id ? found.request : undefined };
}

// Unidades do alcance, para o filtro. Falha deixa a lista vazia.
export function useReachableUnits() {
  const [units, setUnits] = useState<{ id: string; name: string }[]>([]);
  useEffect(() => {
    let alive = true;
    api<{ units: { id: string; name: string }[] }>("/units/reachable").then(
      (v) => alive && setUnits(v.units),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return units;
}
