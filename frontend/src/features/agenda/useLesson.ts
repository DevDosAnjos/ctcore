import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { AgendaLesson } from "./useAgenda";

// Espelha LessonDetail do OpenAPI (docs/openapi.yaml).
export interface LessonDetail extends AgendaLesson {
  visibilityClass: AgendaLesson["teacher"] | null;
  head: AgendaLesson["teacher"] | null;
  activeStudents: number;
  trialClasses: number;
  groupLink: string | null;
  canViewClass: boolean;
}

type Done = {
  tick: number;
  id: string;
  lesson?: LessonDetail;
  failure?: "forbidden" | "notFound" | "error";
};

// GET /agenda/lessons/{id}: 403 vira "sem permissão" e 404 "aula não encontrada".
export function useLesson(id: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done>();
  useEffect(() => {
    let alive = true;
    api<LessonDetail>(`/agenda/lessons/${id}`).then(
      (lesson) => alive && setDone({ tick, id, lesson }),
      (e) =>
        alive &&
        setDone({
          tick,
          id,
          failure:
            e instanceof ApiError && e.status === 403
              ? "forbidden"
              : e instanceof ApiError && e.status === 404
                ? "notFound"
                : "error",
        }),
    );
    return () => {
      alive = false;
    };
  }, [id, tick]);
  const current = done?.tick === tick && done.id === id ? done : undefined;
  return {
    status: !current
      ? ("loading" as const)
      : (current.failure ?? ("ready" as const)),
    lesson: current?.lesson,
    retry: () => setTick((n) => n + 1),
  };
}
