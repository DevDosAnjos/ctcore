import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham Modality e ModalityRequest do OpenAPI (docs/openapi.yaml).
export interface Modality {
  id: string;
  name: string;
  active: boolean;
  canManage: boolean;
}
export interface ModalityRequest {
  id: string;
  name: string;
  note: string | null;
  status: "OPEN" | "ADDED" | "DISMISSED";
  createdAt: string;
}

type Loaded = {
  tick: number;
  items?: Modality[];
  requests?: ModalityRequest[];
  forbidden?: boolean;
};

// Catálogo com a situação da organização e pedidos de Configurações › Modalidades (RF-MOD-01). O servidor decide quem acessa (403).
export function useModalities() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    Promise.all([
      api<Modality[]>("/modalities"),
      api<ModalityRequest[]>("/modalities/requests"),
    ]).then(
      ([items, requests]) => alive && setDone({ tick, items, requests }),
      (e) =>
        alive &&
        setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current
    ? "loading"
    : current.items
      ? "ready"
      : current.forbidden
        ? "forbidden"
        : "error";

  return {
    status,
    items: current?.items,
    requests: current?.requests ?? [],
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a tela explicar; a lista passa a mostrar a situação nova.
    setActive: async (id: string, active: boolean) => {
      const updated = await api<Modality>(`/modalities/${id}/activation`, {
        method: "PUT",
        body: { active },
      });
      setDone((d) =>
        d?.items
          ? { ...d, items: d.items.map((m) => (m.id === id ? updated : m)) }
          : d,
      );
    },
    sendRequest: async (name: string, note: string) => {
      const created = await api<ModalityRequest>("/modalities/requests", {
        method: "POST",
        body: { name, note: note || null },
      });
      setDone((d) =>
        d ? { ...d, requests: [created, ...(d.requests ?? [])] } : d,
      );
    },
  } as const;
}
