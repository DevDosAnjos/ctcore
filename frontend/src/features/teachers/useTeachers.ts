import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";

// Espelham TeacherRow do OpenAPI (docs/openapi.yaml). Telefone e e-mail nunca vêm na lista (RN-PRO-04).
export interface Teacher {
  id: string;
  name: string;
  photoUrl: string | null;
  modalities: { id: string; name: string; level: string | null; degree: number | null }[];
  units: { id: string; name: string }[];
  classCount: number;
  status: "ACTIVE" | "INACTIVE";
  directoryConsent: boolean;
  access: "NONE" | "PENDING" | "ACTIVE" | "ENDED";
}
interface Choice {
  id: string;
  name: string;
}

// Query da tela (RNF-27) -> GET /teachers. A ordem "name" ou "-name" vira sort=name,asc|desc; erro sobe com o status (403 vira "sem permissão").
export async function fetchTeachers(q: Query): Promise<Page<Teacher>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho) });
  if (q.q) p.set("q", q.q);
  if (q.ordem) p.set("sort", q.ordem.startsWith("-") ? "name,desc" : "name,asc");
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<{ items: Teacher[]; total: number }>(`/teachers?${p}`);
  return { rows: data.items, total: data.total };
}

// Opções dos filtros: modalidades ativas da organização e unidades ao alcance. Falha deixa a lista vazia; a lista de professores segue.
export function useTeacherFilterOptions() {
  const [options, setOptions] = useState<{ modalities: Choice[]; units: Choice[] }>({ modalities: [], units: [] });
  useEffect(() => {
    let alive = true;
    Promise.all([
      api<(Choice & { active: boolean })[]>("/modalities"),
      api<{ units: Choice[] }>("/units/reachable"),
    ]).then(
      ([m, u]) => alive && setOptions({ modalities: m.filter((x) => x.active), units: u.units }),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return options;
}
