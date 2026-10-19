import { useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";

interface Choice {
  id: string;
  name: string;
}

// Espelha ClassRow do OpenAPI (docs/openapi.yaml). Link do grupo e contato nunca vêm na lista.
export interface TrainingClass {
  id: string;
  name: string;
  unit: Choice;
  modality: Choice;
  ageRange: { id: string; name: string; minAge: number; maxAge: number | null } | null;
  schedule: { weekday: number; startsAt: string; endsAt: string }[];
  head: Choice;
  assistants: Choice[];
  activeStudents: number;
  capacity: number | null;
  overCapacity: boolean;
  status: "ACTIVE" | "INACTIVE";
}

// Query da tela (RNF-27) -> GET /classes. A ordem "name" ou "-name" vira sort=name,asc|desc; erro sobe com o status (403 vira "sem permissão").
export async function fetchClasses(q: Query): Promise<Page<TrainingClass>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  if (q.q) p.set("q", q.q);
  if (q.ordem) p.set("sort", q.ordem.startsWith("-") ? "name,desc" : "name,asc");
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: TrainingClass[]; total: number }>(`/classes?${p}`);
  return { rows: data.items, total: data.total };
}

// Opções dos filtros. Falha deixa a opção vazia; a lista de turmas segue.
export function useClassFilterOptions() {
  const [options, setOptions] = useState<{ modalities: Choice[]; teachers: Choice[]; ageRanges: Choice[]; units: Choice[] }>({
    modalities: [],
    teachers: [],
    ageRanges: [],
    units: [],
  });
  useEffect(() => {
    let alive = true;
    Promise.all([
      api<(Choice & { active: boolean })[]>("/modalities"),
      api<{ items: Choice[] }>("/teachers?size=100"),
      api<{ items: Choice[] }>("/age-ranges"),
      api<{ units: Choice[] }>("/units/reachable"),
    ]).then(
      ([m, t, a, u]) => alive && setOptions({ modalities: m.filter((x) => x.active), teachers: t.items, ageRanges: a.items, units: u.units }),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return options;
}

// Espelha ClassDetail do OpenAPI (RF-TUR-02): a linha da lista mais link do grupo, próxima aula, alunos e a regra de desativação.
export interface ClassDetail extends TrainingClass {
  groupLink: string | null;
  nextClass: { date: string; startsAt: string; endsAt: string } | null;
  students: { id: string; name: string; primary: boolean }[];
  deactivation: { allowed: boolean; blockingStudents: number };
}

type Loaded = { tick: number; id: string; klass?: ClassDetail; failure?: "forbidden" | "notFound" | "error" };

// Painel da turma. O servidor decide o alcance: 403 vira "sem permissão" e 404 "não encontrada".
export function useClass(id: string) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<Loaded>();
  useEffect(() => {
    let alive = true;
    api<ClassDetail>(`/classes/${id}`).then(
      (klass) => alive && setDone({ tick, id, klass }),
      (e) => alive && setDone({ tick, id, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : e instanceof ApiError && e.status === 404 ? "notFound" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [id, tick]);
  const current = done?.tick === tick && done.id === id ? done : undefined;
  return {
    status: !current ? "loading" : (current.failure ?? "ready"),
    klass: current?.klass,
    retry: () => setTick((n) => n + 1),
    // Recarrega sem passar por "carregando" (depois de desativar).
    refresh: async () => setDone({ tick, id, klass: await api<ClassDetail>(`/classes/${id}`) }),
  } as const;
}

export const deactivateClass = (id: string) => api<ClassDetail>(`/classes/${id}/deactivate`, { method: "POST" });

// Espelha ClassInput e ClassOptions do OpenAPI (RF-TUR-03).
export interface ClassInput {
  unitId?: string;
  name: string;
  modalityId: string;
  ageRangeId: string | null;
  schedule: { weekday: number; startsAt: string; endsAt: string }[];
  capacity: number | null;
  headTeacherId: string;
  assistantIds: string[];
  groupLink: string | null;
  notifyClass?: boolean;
}
export interface ClassOptions {
  units: { id: string; name: string; modalities: Choice[]; teachers: Choice[] }[];
  ageRanges: { id: string; name: string; minAge: number; maxAge: number | null }[];
}
export const createClass = (input: ClassInput) => api<ClassDetail>("/classes", { method: "POST", body: input });
export const updateClass = (id: string, input: ClassInput) => api<ClassDetail & { scheduleChanged: boolean }>(`/classes/${id}`, { method: "PUT", body: input });

// Opções do formulário; só carregam com a janela aberta. 403 vira "sem permissão".
export function useClassOptions(enabled: boolean) {
  const [tick, setTick] = useState(0);
  const [done, setDone] = useState<{ tick: number; options?: ClassOptions; forbidden?: boolean }>();
  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    api<ClassOptions>("/classes/options").then(
      (options) => alive && setDone({ tick, options }),
      (e) => alive && setDone({ tick, forbidden: e instanceof ApiError && e.status === 403 }),
    );
    return () => {
      alive = false;
    };
  }, [enabled, tick]);
  const current = done?.tick === tick ? done : undefined;
  return {
    status: !current ? "loading" : current.options ? "ready" : current.forbidden ? "forbidden" : "error",
    options: current?.options,
    retry: () => setTick((n) => n + 1),
  } as const;
}
