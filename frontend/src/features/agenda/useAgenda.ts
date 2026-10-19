import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

interface Choice {
  id: string;
  name: string;
}

// Espelha AgendaLesson do OpenAPI (docs/openapi.yaml). `class` é o nome da turma.
export interface AgendaLesson {
  id: string;
  date: string;
  startsAt: string;
  endsAt: string;
  status: "SCHEDULED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED";
  cancelReason: string | null;
  class: Choice;
  unit: Choice;
  modality: Choice;
  teacher: Choice;
  substitute: boolean;
  canEdit: boolean;
  canCancel: boolean;
}

export interface NoClassDay {
  startsOn: string;
  endsOn: string;
  reason: string;
}

// Espelha Agenda do OpenAPI.
export interface AgendaData {
  units: Choice[];
  unit: Choice | null;
  teachers: Choice[];
  modalities: Choice[];
  lessons: AgendaLesson[];
  noClassDays: NoClassDay[];
}

export interface AgendaQuery {
  from: string;
  to: string;
  unitId: string;
  teacherId: string;
  modalityId: string;
}

type Done = { key: string; data?: AgendaData; failure?: "forbidden" | "error"; last?: AgendaData };

// GET /agenda. O servidor decide o alcance: 403 vira "sem permissão". Os dados anteriores ficam para os filtros não sumirem enquanto carrega.
export function useAgenda(q: AgendaQuery) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done>();
  const params = new URLSearchParams({ from: q.from, to: q.to });
  for (const k of ["unitId", "teacherId", "modalityId"] as const) if (q[k]) params.set(k, q[k]);
  const key = `${params}#${tick}`;
  useEffect(() => {
    let alive = true;
    api<AgendaData>(`/agenda?${params}`).then(
      (data) => alive && setDone((prev) => ({ key, data, last: data ?? prev?.last })),
      (e) => alive && setDone((prev) => ({ key, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : "error", last: prev?.last })),
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `key` resume params e tick
  }, [key]);
  const current = done?.key === key ? done : undefined;
  return {
    status: !current ? ("loading" as const) : (current.failure ?? ("ready" as const)),
    data: current?.data,
    last: done?.last,
    retry: () => setTick((n) => n + 1),
  };
}
