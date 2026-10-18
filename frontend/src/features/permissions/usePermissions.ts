import { useEffect, useState } from "react";
import { useSession } from "@/features/auth/SessionProvider";
import { api } from "@/lib/api";
import type { PermissionMap } from "./types";

// Permissões efetivas do vínculo ativo (GET /permissions); refeitas ao trocar de vínculo ou ao tentar de novo.
export function usePermissions() {
  const { state } = useSession();
  const linkId = state.status === "ready" ? (state.session.activeLinkId ?? "") : "";
  const [tick, setTick] = useState(0);
  const key = `${linkId}:${tick}`;
  const [done, setDone] = useState<{ key: string; permissions?: PermissionMap }>();

  useEffect(() => {
    let alive = true;
    api<{ permissions: PermissionMap }>("/permissions").then(
      (v) => alive && setDone({ key, permissions: v.permissions }),
      () => alive && setDone({ key }),
    );
    return () => {
      alive = false;
    };
  }, [key]);

  const current = done?.key === key ? done : undefined;
  const status = !current ? "loading" : current.permissions ? "ready" : "error";
  return { status, permissions: current?.permissions, retry: () => setTick((n) => n + 1) } as const;
}
