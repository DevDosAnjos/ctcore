import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useSession } from "@/features/auth/SessionProvider";
import { api, ApiError } from "@/lib/api";

// Espelha ReachableUnits do OpenAPI (RF-ORG-05).
export interface ReachableUnit {
  id: string;
  name: string;
  shortName: string;
}
export const ALL = "all";
type Status = "loading" | "ready" | "forbidden" | "error";
export interface UnitScope {
  status: Status;
  units: ReachableUnit[];
  allUnits: boolean;
  /** "all" (só consulta), o id de uma unidade ou null quando nada está ao alcance. */
  selected: string | null;
  select: (value: string) => void;
  retry: () => void;
}

const Context = createContext<UnitScope | null>(null);
const storageKey = (linkId: string) => `ctcore.unitScope.${linkId}`;

// A escolha é conveniência do navegador (D2): o servidor decide o acesso em cada rota.
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}
function write(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // sem armazenamento: a escolha vale só nesta sessão da página
  }
}

export function UnitScopeProvider({ children }: { children: ReactNode }) {
  const { state } = useSession();
  const linkId = state.status === "ready" ? (state.session.activeLinkId ?? "") : "";
  const key = storageKey(linkId);
  const [tick, setTick] = useState(0);
  const fetchKey = `${linkId}:${tick}`;
  const [done, setDone] = useState<{ key: string; allUnits?: boolean; units?: ReachableUnit[]; status?: Status }>();
  const [picked, setPicked] = useState<{ key: string; value: string }>();

  useEffect(() => {
    let alive = true;
    api<{ allUnits: boolean; units: ReachableUnit[] }>("/units/reachable").then(
      (v) => alive && setDone({ key: fetchKey, ...v }),
      (e) => alive && setDone({ key: fetchKey, status: e instanceof ApiError && e.status === 403 ? "forbidden" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [fetchKey]);

  const current = done?.key === fetchKey ? done : undefined;
  const units = current?.units ?? [];
  // "Todas as unidades" só faz sentido com duas ou mais (RN-ORG-04).
  const all = !!current?.allUnits && units.length >= 2;
  const stored = picked?.key === key ? picked.value : read(key);
  const valid = stored !== null && (stored === ALL ? all : units.some((u) => u.id === stored));
  const selected = valid ? stored : all ? ALL : (units[0]?.id ?? null);
  const status: Status = !current ? "loading" : current.units ? "ready" : (current.status ?? "error");

  const value: UnitScope = {
    status,
    units,
    allUnits: all,
    selected: status === "ready" ? selected : null,
    select: (v) => {
      write(key, v);
      setPicked({ key, value: v });
    },
    retry: () => setTick((n) => n + 1),
  };
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function useUnitScope(): UnitScope {
  const scope = useContext(Context);
  if (!scope) throw new Error("useUnitScope fora do UnitScopeProvider");
  return scope;
}
