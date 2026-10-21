import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

export type TermsKind = "ENROLLMENT" | "IMAGE_USE";

// Espelham TermsVersion, TermsView e TermsPublishInput do OpenAPI (docs/openapi.yaml).
export interface TermsVersion {
  version: number;
  body: string;
  reaccept: boolean;
  publishedAt: string | null;
  publishedBy: string | null;
}
export interface TermsView {
  canManage: boolean;
  current: TermsVersion;
  versions: TermsVersion[];
}
export interface TermsInput {
  body: string;
  reaccept: boolean;
}

type Loaded = { tick: number; data?: TermsView; forbidden?: boolean };

// Termo de Configurações › Termos (RF-MAT-05). O servidor decide quem acessa (403) e quem publica (canManage).
export function useTerms(kind: TermsKind) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<TermsView>(`/organization/terms/${kind}`).then(
      (data) => alive && setDone({ tick, data }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [kind, tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.data ? "ready" : current.forbidden ? "forbidden" : "error";

  return {
    status,
    data: current?.data,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a tela mostrar; a atual e o histórico refletem a nova versão sem recarregar.
    publish: async (input: TermsInput) => {
      const created = await api<TermsVersion>(`/organization/terms/${kind}/versions`, { method: "POST", body: input });
      setDone((d) => (d?.data ? { ...d, data: { ...d.data, current: created, versions: [created, ...d.data.versions] } } : d));
    },
  } as const;
}
