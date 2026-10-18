import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelha UnitSummary do OpenAPI (docs/openapi.yaml).
export interface UnitSummary {
  id: string;
  name: string;
  shortName: string;
  shortNameAdjusted: boolean;
  city: string | null;
  activeStudents: number;
  administrators: { name: string }[];
  active: boolean;
}

// Espelha NewUnit e NewUnitResult do OpenAPI (RF-ORG-03).
export interface NewUnit {
  name: string;
  postalCode?: string;
  address?: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  phone?: string;
  email?: string;
  whatsapp?: string;
  administrator?: { name: string; email: string };
}
export interface NewUnitResult {
  unit: UnitSummary;
  invite: "NONE" | "SENT" | "FAILED";
}

export const MAX_UNITS = 10;
export const createUnit = (body: NewUnit) => api<NewUnitResult>("/units", { method: "POST", body });

type Loaded = { tick: number; units?: UnitSummary[]; forbidden?: boolean };

// Consulta e ajuste do nome curto de Configurações › Unidades (RF-ORG-02). O servidor decide quem acessa (403).
export function useUnits() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<UnitSummary[]>("/units").then(
      (units) => alive && setDone({ tick, units }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.units ? "ready" : current.forbidden ? "forbidden" : "error";

  return {
    status,
    units: current?.units,
    retry: () => setTick((n) => n + 1),
    // Recarrega a lista sem passar por "carregando" (depois de desativar uma unidade).
    refresh: async () => {
      const units = await api<UnitSummary[]>("/units");
      setDone({ tick, units });
    },
    // Erros sobem (ApiError) para o diálogo explicar; a lista passa a mostrar o nome novo.
    adjustShortName: async (id: string, shortName: string) => {
      const updated = await api<UnitSummary>(`/units/${id}/short-name`, { method: "PATCH", body: { shortName } });
      setDone((d) => (d?.units ? { ...d, units: d.units.map((u) => (u.id === id ? updated : u)) } : d));
      return updated;
    },
  } as const;
}
