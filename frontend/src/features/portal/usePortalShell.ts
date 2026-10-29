import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelha PortalShell do OpenAPI (docs/openapi.yaml).
export interface PortalShellData {
  organization: { name: string; color: string | null; logoUrl: string | null };
  reduced: boolean;
  unreadAnnouncements: number;
  /** Unidade do rodapé; nulo sem modalidade ativa. Opcional só para os testes que montam a moldura sem ela. */
  unit?: { name: string; address: string | null; whatsapp: string | null } | null;
}

/** D5: a moldura é lida na abertura e a cada 60 s (mesmo ritmo do sino da gestão). */
export const SHELL_POLL_MS = 60_000;

const REFRESH = "portal-shell-refresh";

/** Quem grava leitura de aviso pede a releitura do sino sem esperar os 60 s. */
export const refreshPortalShell = () => window.dispatchEvent(new Event(REFRESH));

type Loaded = { tick: number; data?: PortalShellData; forbidden?: boolean; failed?: boolean };

// Marca, contador do sino e portal reduzido (RF-POR-01, RN-POR-08). Falha numa releitura mantém o último valor; a primeira leitura mostra o erro.
export function usePortalShell() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();
  const [last, setLast] = useState<PortalShellData>();

  useEffect(() => {
    let alive = true;
    api<PortalShellData>("/portal/shell").then(
      (data) => {
        if (!alive) return;
        setLast(data);
        setDone({ tick, data });
      },
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403, failed: true }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  useEffect(() => {
    const timer = setInterval(() => setTick((n) => n + 1), SHELL_POLL_MS);
    const now = () => setTick((n) => n + 1);
    window.addEventListener(REFRESH, now);
    return () => {
      clearInterval(timer);
      window.removeEventListener(REFRESH, now);
    };
  }, []);

  const current = done?.tick === tick ? done : undefined;
  const data = current?.data ?? last;
  const status = data ? "ready" : !current ? "loading" : current.forbidden ? "forbidden" : "error";
  return { status, data, retry: useCallback(() => setTick((n) => n + 1), []) } as const;
}
