import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelha PortalTermPending do OpenAPI (docs/openapi.yaml).
export interface PortalTermPending {
  id: string;
  name: string;
  term: { version: number; body: string };
  imageUse: { version: number; body: string; granted: boolean };
}

type Loaded = { tick: number; data?: PortalTermPending[]; forbidden?: boolean };

// Termos pendentes que a conta aceita (RN-MAT-11, RN-POR-03). O servidor decide quem aceita; o front só mostra.
export function usePortalTerm() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<PortalTermPending[]>("/portal/term").then(
      (data) => alive && setDone({ tick, data }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.data ? "ready" : current.forbidden ? "forbidden" : "error";

  return {
    status,
    data: current?.data,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a tela mostrar; depois do aceite a lista é lida de novo.
    accept: async (p: PortalTermPending, imageUse: boolean) => {
      await api(`/portal/students/${p.id}/term-acceptance`, { method: "POST", body: { version: p.term.version, imageUse } });
      setTick((n) => n + 1);
    },
  };
}
