import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";

export interface Choice {
  id: string;
  name: string;
}

// Espelham NoClassDayList, NoClassDayItem e NoClassDayInput do OpenAPI (docs/openapi.yaml).
export interface NoClassDayItem {
  id: string;
  unit: Choice;
  startsOn: string;
  endsOn: string;
  reason: string;
  origin: "HOLIDAY" | "COORDINATOR" | "UNIT";
  allClasses: boolean;
  classes: Choice[];
  notice: { mode: NoticeMode; sendAt: string | null; status: "NOT_NOTIFIED" | "SCHEDULED" | "SENT" };
  cancelledLessons: number;
  canEdit: boolean;
}

export type NoticeMode = "NONE" | "NOW" | "SCHEDULED";

export interface Suggestion {
  code: string;
  date: string;
}

export interface NoClassDayList {
  units: Choice[];
  unit: Choice | null;
  days: NoClassDayItem[];
  suggestions: Suggestion[];
}

export interface NoClassDayInput {
  unitIds: string[];
  startsOn: string;
  endsOn: string;
  reason: string;
  holiday?: boolean;
  classIds?: string[];
  notice: { mode: NoticeMode; sendAt?: string };
}

export interface PreviewLesson {
  id: string;
  date: string;
  startsAt: string;
  endsAt: string;
  class: Choice | null;
  title: string | null;
}

export interface Preview {
  units: { unit: Choice; lessons: PreviewLesson[] }[];
}

export const createDay = (body: NoClassDayInput) => api<NoClassDayItem[]>("/no-class-days", { method: "POST", body });
export const updateDay = (id: string, body: NoClassDayInput) => api<NoClassDayItem>(`/no-class-days/${id}`, { method: "PUT", body });
export const deleteDay = (id: string, notify: boolean) => api<void>(`/no-class-days/${id}?notify=${notify}`, { method: "DELETE" });

type Done = { key: string; data?: NoClassDayList; failure?: "forbidden" | "error"; last?: NoClassDayList };

// GET /no-class-days. O servidor decide o alcance: 403 vira "sem permissão". Os dados anteriores ficam para a escolha de unidade não sumir ao carregar.
export function useNoClassDays(unitId: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Done>();
  const key = `${unitId}#${tick}`;
  useEffect(() => {
    let alive = true;
    api<NoClassDayList>(`/no-class-days${unitId ? `?unitId=${unitId}` : ""}`).then(
      (data) => alive && setDone((prev) => ({ key, data, last: data ?? prev?.last })),
      (e) => alive && setDone((prev) => ({ key, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : "error", last: prev?.last })),
    );
    return () => {
      alive = false;
    };
  }, [key, unitId]);
  const current = done?.key === key ? done : undefined;
  return {
    status: !current ? ("loading" as const) : (current.failure ?? ("ready" as const)),
    data: current?.data,
    last: done?.last,
    retry: () => setTick((n) => n + 1),
  };
}

// GET /no-class-days/preview: as aulas que o período cancelaria. Só consulta com datas válidas; muda com unidades, período e turmas.
export function usePreview(unitIds: string[], startsOn: string, endsOn: string, classIds: string[]) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ key: string; data?: Preview; failure?: true }>();
  const valid = unitIds.length > 0 && /^\d{4}-\d{2}-\d{2}$/.test(startsOn) && /^\d{4}-\d{2}-\d{2}$/.test(endsOn) && endsOn >= startsOn;
  const params = new URLSearchParams({ startsOn, endsOn });
  for (const u of unitIds) params.append("unitIds", u);
  for (const c of classIds) params.append("classIds", c);
  const key = `${params}#${tick}`;
  useEffect(() => {
    if (!valid) return;
    let alive = true;
    api<Preview>(`/no-class-days/preview?${params}`).then(
      (data) => alive && setDone({ key, data }),
      () => alive && setDone({ key, failure: true }),
    );
    return () => {
      alive = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- `key` resume params e tick
  }, [key, valid]);
  const current = valid && done?.key === key ? done : undefined;
  return {
    status: !valid ? ("idle" as const) : !current ? ("loading" as const) : current.failure ? ("error" as const) : ("ready" as const),
    data: current?.data,
    retry: () => setTick((n) => n + 1),
  };
}
