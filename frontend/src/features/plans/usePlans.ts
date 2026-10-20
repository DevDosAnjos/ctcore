import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";

// Espelha PlanSummary do OpenAPI (docs/openapi.yaml).
export interface PlanSummary {
  plans: { active: number; inactive: number; studentsUsing: number; minValueCents: number | null; maxValueCents: number | null };
  benefits: { active: number; high: number };
}
type Result = { key: string; summary?: PlanSummary; status?: "forbidden" | "error" };

// RF-PLA-01: resumo da unidade escolhida (unitId nulo = todas do alcance); pede de novo ao trocar de unidade ou ao tentar de novo.
export function usePlanSummary(unitId: string | null) {
  const [tick, setTick] = useState(0);
  const key = `${unitId ?? ""}:${tick}`;
  const [done, setDone] = useState<Result>();
  useEffect(() => {
    let alive = true;
    api<PlanSummary>(`/plans/summary${unitId ? `?unitId=${encodeURIComponent(unitId)}` : ""}`).then(
      (summary) => alive && setDone({ key, summary }),
      (e) => alive && setDone({ key, status: e instanceof ApiError && e.status === 403 ? "forbidden" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [key, unitId]);
  const current = done?.key === key ? done : undefined;
  return { summary: current?.summary, status: !current ? "loading" : (current.status ?? "ready"), retry: () => setTick((n) => n + 1) } as const;
}

interface Choice {
  id: string;
  name: string;
}

// Espelha PlanRow do OpenAPI (docs/openapi.yaml). Frequência semanal nula é livre; só informa.
export interface PlanRow {
  id: string;
  unit: Choice;
  modality: Choice;
  name: string;
  valueCents: number;
  weeklyFrequency: 2 | 3 | null;
  validOtherUnits: boolean;
  studentsUsing: number;
  status: "ACTIVE" | "INACTIVE";
}

// Query da tela (RNF-27) -> GET /plans; erro sobe com o status (403 vira "sem permissão").
export async function fetchPlans(q: Query): Promise<Page<PlanRow>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  if (q.q) p.set("q", q.q);
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: PlanRow[]; total: number }>(`/plans?${p}`);
  return { rows: data.items, total: data.total };
}

// Opções dos filtros (modalidades ativas e unidades do alcance). Falha deixa a opção vazia; a lista segue.
export function usePlanFilterOptions() {
  const [options, setOptions] = useState<{ modalities: Choice[]; units: Choice[] }>({ modalities: [], units: [] });
  useEffect(() => {
    let alive = true;
    Promise.all([api<(Choice & { active: boolean })[]>("/modalities"), api<{ units: Choice[] }>("/units/reachable")]).then(
      ([m, u]) => alive && setOptions({ modalities: m.filter((x) => x.active), units: u.units }),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return options;
}
