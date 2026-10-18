import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelha StaffUser do OpenAPI (docs/openapi.yaml, RF-ACE-06).
export interface StaffUser {
  linkId: string;
  name: string;
  email: string;
  role: "ADMINISTRATOR" | "TEACHER";
  units: { id: string; name: string }[];
  lastAccessAt: string | null;
  status: "PENDING" | "ACTIVE" | "ENDED";
  canManage: boolean;
}

export const inviteUser = (body: { email: string; name: string; unitIds: string[] }) => api<StaffUser>("/users", { method: "POST", body });
export const resendInvite = (linkId: string) => api<void>(`/users/${linkId}/resend`, { method: "POST" });
export const changeUnits = (linkId: string, unitIds: string[]) => api<StaffUser>(`/users/${linkId}/units`, { method: "PUT", body: { unitIds } });
export const deactivateUser = (linkId: string) => api<void>(`/users/${linkId}`, { method: "DELETE" });
export const appointMyself = (unitIds: string[]) => api<StaffUser>("/users/coordinator-appointment", { method: "POST", body: { unitIds } });

type Loaded = { tick: number; users?: StaffUser[]; forbidden?: boolean };

// Lista de Configurações › Usuários. O servidor decide quem acessa (403) e quem aparece.
export function useUsers() {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();

  useEffect(() => {
    let alive = true;
    api<StaffUser[]>("/users").then(
      (users) => alive && setDone({ tick, users }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [tick]);

  const current = done?.tick === tick ? done : undefined;
  const status = !current ? "loading" : current.users ? "ready" : current.forbidden ? "forbidden" : "error";
  return {
    status,
    users: current?.users,
    retry: () => setTick((n) => n + 1),
    // Recarrega sem passar por "carregando" (depois de convidar, trocar unidades ou desativar).
    refresh: async () => setDone({ tick, users: await api<StaffUser[]>("/users") }),
  } as const;
}
