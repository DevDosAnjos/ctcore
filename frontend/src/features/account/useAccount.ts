import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { useSession, type SessionView } from "@/features/auth/SessionProvider";

// Espelha AccountSession do OpenAPI (docs/openapi.yaml).
export interface AccountSessionItem {
  id: string;
  device: string;
  city: string | null;
  createdAt: string;
  lastUsedAt: string;
  current: boolean;
}

/** Relê a sessão (e-mail, duas etapas) sem passar por "carregando", para a tela não desmontar. */
export function useReloadSession() {
  const { setSession } = useSession();
  return async () => setSession(await api<SessionView>("/auth/session"));
}

type Loaded = { tick: number; items?: AccountSessionItem[] };

export function useAccountSessions() {
  const { refresh } = useSession();
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<AccountSessionItem[]>("/auth/sessions").then(
      (items) => alive && setDone({ tick, items }),
      (e) => {
        if (!alive) return;
        if (e instanceof ApiError && e.status === 401) void refresh(); // sessão expirada: volta ao login
        else setDone({ tick });
      },
    );
    return () => {
      alive = false;
    };
  }, [tick, refresh]);

  const current = done?.tick === tick ? done : undefined;
  return {
    status: !current ? ("loading" as const) : current.items ? ("ready" as const) : ("error" as const),
    items: current?.items ?? [],
    reload: () => setTick((n) => n + 1),
  };
}
