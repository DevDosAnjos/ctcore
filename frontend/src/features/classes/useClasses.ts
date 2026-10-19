import { useEffect, useState } from "react";
import { api } from "@/lib/api";
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
