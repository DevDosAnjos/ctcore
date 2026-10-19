import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham AgeRange e AgeRangeList do OpenAPI (docs/openapi.yaml).
export interface AgeRange {
  id: string;
  name: string;
  minAge: number;
  maxAge: number | null;
}
export interface AgeRangeInput {
  name: string;
  minAge: number;
  maxAge: number | null;
}
interface Listing {
  canManage: boolean;
  items: AgeRange[];
}

type Loaded = { tick: number; data?: Listing; forbidden?: boolean };

// Faixas etárias de Configurações › Faixas etárias (RF-MOD-04). O servidor decide quem acessa (403) e quem altera (canManage).
export function useAgeRanges() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<Listing>("/age-ranges").then(
      (data) => alive && setDone({ tick, data }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.data ? "ready" : current.forbidden ? "forbidden" : "error";
  const sort = (items: AgeRange[]) => [...items].sort((a, b) => a.minAge - b.minAge || a.name.localeCompare(b.name));
  const change = (fn: (items: AgeRange[]) => AgeRange[]) =>
    setDone((d) => (d?.data ? { ...d, data: { ...d.data, items: sort(fn(d.data.items)) } } : d));

  return {
    status,
    items: current?.data?.items,
    canManage: current?.data?.canManage ?? false,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a janela mostrar; a lista reflete a mudança sem recarregar.
    create: async (input: AgeRangeInput) => {
      const created = await api<AgeRange>("/age-ranges", { method: "POST", body: input });
      change((items) => [...items, created]);
    },
    update: async (id: string, input: AgeRangeInput) => {
      const updated = await api<AgeRange>(`/age-ranges/${id}`, { method: "PUT", body: input });
      change((items) => items.map((r) => (r.id === id ? updated : r)));
    },
    remove: async (id: string) => {
      await api<void>(`/age-ranges/${id}`, { method: "DELETE" });
      change((items) => items.filter((r) => r.id !== id));
    },
  } as const;
}
