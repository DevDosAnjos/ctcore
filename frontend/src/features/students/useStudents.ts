import { useEffect, useState } from "react";
import { api } from "@/lib/api";
import type { Page, Query } from "@/components/query/QueryPage";
import type { StudentList, StudentRow } from "./types";

interface Choice {
  id: string;
  name: string;
}

// Query da tela (RNF-27) -> GET /students. A ordem é sempre por nome (a rota só aceita sort=name); erro sobe com o status (403 vira "sem permissão").
export async function fetchStudents(q: Query): Promise<Page<StudentRow>> {
  const p = new URLSearchParams({ page: String(q.pagina), size: String(q.tamanho), sort: "name" });
  if (q.q) p.set("q", q.q);
  for (const [k, v] of Object.entries(q.filtros)) p.set(k, v);
  const data = await api<StudentList>(`/students?${p}`);
  return { rows: data.items, total: data.total, counts: data.counts };
}

// Opções dos filtros: unidades ao alcance, faixas etárias, modalidades e turmas ativas. Cada uma falha sozinha (fica vazia); a lista de alunos segue.
export function useStudentFilterOptions() {
  const [options, setOptions] = useState<{ units: Choice[]; ageRanges: Choice[]; modalities: Choice[]; classes: Choice[] }>({ units: [], ageRanges: [], modalities: [], classes: [] });
  useEffect(() => {
    let alive = true;
    const safe = <T,>(path: string) => api<T>(path).catch(() => null);
    Promise.all([
      safe<{ units: Choice[] }>("/units/reachable"),
      safe<{ items: Choice[] }>("/age-ranges"),
      safe<(Choice & { active: boolean })[]>("/modalities"),
      safe<{ items: Choice[] }>("/classes?size=100&status=ACTIVE"),
    ]).then(([u, r, m, c]) => alive && setOptions({ units: u?.units ?? [], ageRanges: r?.items ?? [], modalities: (m ?? []).filter((x) => x.active), classes: c?.items ?? [] }));
    return () => {
      alive = false;
    };
  }, []);
  return options;
}
