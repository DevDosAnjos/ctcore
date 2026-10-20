import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

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
