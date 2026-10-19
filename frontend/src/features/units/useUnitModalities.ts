import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham UnitModalities e UnitModality do OpenAPI (RF-MOD-03, docs/openapi.yaml).
export interface UnitModality {
  id: string;
  name: string;
  offered: boolean;
}
interface Listing {
  canManage: boolean;
  items: UnitModality[];
}
type Loaded = { tick: number; unitId: string; listing?: Listing; forbidden?: boolean };

// Modalidades ativas da organização com a marcação da unidade escolhida. O servidor decide quem lê e quem marca (403).
export function useUnitModalities(unitId: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<Listing>(`/units/${unitId}/modalities`).then(
      (listing) => alive && setDone({ tick, unitId, listing }),
      (e) => alive && setDone({ tick, unitId, forbidden: e instanceof ApiError && (e.status === 403 || e.status === 404) }),
    );
    return () => {
      alive = false;
    };
  }, [unitId, tick]);

  const current = done?.tick === tick && done.unitId === unitId ? done : undefined;
  const status = !current ? "loading" : current.listing ? "ready" : current.forbidden ? "forbidden" : "error";
  const mark = (id: string, offered: boolean) =>
    setDone((d) => (d?.listing ? { ...d, listing: { ...d.listing, items: d.listing.items.map((m) => (m.id === id ? { ...m, offered } : m)) } } : d));

  return {
    status,
    listing: current?.listing,
    retry: () => setTick((n) => n + 1),
    // Efeito imediato: o interruptor muda na hora e volta ao valor anterior se o servidor recusar (o erro sobe).
    setOffered: async (id: string, offered: boolean) => {
      mark(id, offered);
      try {
        await api<UnitModality>(`/units/${unitId}/modalities/${id}`, { method: "PUT", body: { offered } });
      } catch (e) {
        mark(id, !offered);
        throw e;
      }
    },
  } as const;
}
