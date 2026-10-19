import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

// Espelham Graduation, GraduationLevel e GraduationUpdate do OpenAPI (docs/openapi.yaml).
export interface GraduationLevel {
  id: string;
  name: string;
  color: string;
  secondaryColor: string | null;
  maxDegree: number;
  minAge: number | null;
  maxAge: number | null;
  kids: boolean;
}
export interface Graduation {
  enabled: boolean;
  requireOriginProof: boolean;
  canManage: boolean;
  levels: GraduationLevel[];
}
export interface GraduationUpdate {
  enabled: boolean;
  requireOriginProof: boolean;
  levels: (Omit<GraduationLevel, "id"> & { id?: string })[];
}

type Loaded = { key: string; data?: Graduation; forbidden?: boolean };

// Sistema de graduação de uma modalidade ativa (RF-MOD-02). O servidor decide quem lê e quem grava (403).
export function useGraduation(modalityId: string | null) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();
  const key = `${modalityId}:${tick}`;

  useEffect(() => {
    if (!modalityId) return;
    let alive = true;
    api<Graduation>(`/modalities/${modalityId}/graduation`).then(
      (data) => alive && setDone({ key, data }),
      (e) =>
        alive &&
        setDone({ key, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [modalityId, key]);

  const current = done?.key === key ? done : undefined;
  return {
    status: !modalityId
      ? "idle"
      : !current
        ? "loading"
        : current.data
          ? "ready"
          : current.forbidden
            ? "forbidden"
            : "error",
    data: current?.data,
    retry: () => setTick((n) => n + 1),
    // Erros sobem (ApiError) para a janela mostrar no campo apontado.
    save: (body: GraduationUpdate) =>
      api<Graduation>(`/modalities/${modalityId}/graduation`, {
        method: "PUT",
        body,
      }),
  } as const;
}
