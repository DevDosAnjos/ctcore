import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { LessonDetail } from "./useLesson";

export type CancelCode = "TEACHER_UNAVAILABLE" | "HOLIDAY" | "ACADEMY_EVENT" | "OTHER";

// Espelha LessonEditInput e LessonCancelInput do OpenAPI (RF-AGE-03).
export interface LessonEditInput {
  scope: "THIS" | "FOLLOWING";
  date: string;
  startsAt: string;
  endsAt: string;
  teacherId: string;
  reason?: string;
}

export interface LessonCancelInput {
  reason: CancelCode;
  reasonText?: string;
  channels: ("EMAIL" | "WHATSAPP")[];
  trialDecision?: "REBOOK" | "CANCEL_TRIALS";
}

export const editLesson = (id: string, body: LessonEditInput) => api<LessonDetail>(`/agenda/lessons/${id}`, { method: "PUT", body });
export const cancelLesson = (id: string, body: LessonCancelInput) => api<LessonDetail>(`/agenda/lessons/${id}/cancel`, { method: "POST", body });

// Opções de professor: os ativos da unidade da aula, vindos de GET /agenda (o servidor recusa com 422 o que não servir).
export function useUnitTeachers(unitId: string, date: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ tick: number; teachers?: { id: string; name: string }[]; failure?: "forbidden" | "error" }>();
  useEffect(() => {
    let alive = true;
    api<{ teachers: { id: string; name: string }[] }>(`/agenda?from=${date}&to=${date}&unitId=${unitId}`).then(
      (d) => alive && setDone({ tick, teachers: d.teachers }),
      (e) => alive && setDone({ tick, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [unitId, date, tick]);
  const current = done?.tick === tick ? done : undefined;
  return {
    status: !current ? ("loading" as const) : (current.failure ?? ("ready" as const)),
    teachers: current?.teachers ?? [],
    retry: () => setTick((n) => n + 1),
  };
}
