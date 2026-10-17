import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api, ApiError } from "@/lib/api";

export interface SessionView {
  account: { id: string; email: string; name?: string | null };
  links: { id: string; organizationId: string; role: string; unitIds: string[] }[];
  activeLinkId?: string | null;
}

type State = { status: "loading" | "anon" | "error" } | { status: "ready"; session: SessionView };
interface Ctx {
  state: State;
  refresh: () => Promise<void>;
  setSession: (s: SessionView) => void;
}

const SessionCtx = createContext<Ctx | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>({ status: "loading" });
  const refresh = useCallback(async () => {
    setState({ status: "loading" });
    try {
      setState({ status: "ready", session: await api<SessionView>("/auth/session") });
    } catch (e) {
      setState({ status: e instanceof ApiError && e.status === 401 ? "anon" : "error" });
    }
  }, []);
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- carga inicial da sessão
    void refresh();
  }, [refresh]);
  const value = useMemo(() => ({ state, refresh, setSession: (session: SessionView) => setState({ status: "ready", session }) }), [state, refresh]);
  return <SessionCtx.Provider value={value}>{children}</SessionCtx.Provider>;
}

export function useSession() {
  const ctx = useContext(SessionCtx);
  if (!ctx) throw new Error("useSession fora do SessionProvider");
  return ctx;
}
