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
  return { rows: data.items, total: data.total };
}

// Opções dos filtros: unidades ao alcance e faixas etárias. Falha deixa a lista vazia; a lista de alunos segue.
export function useStudentFilterOptions() {
  const [options, setOptions] = useState<{ units: Choice[]; ageRanges: Choice[] }>({ units: [], ageRanges: [] });
  useEffect(() => {
    let alive = true;
    Promise.all([api<{ units: Choice[] }>("/units/reachable"), api<{ items: Choice[] }>("/age-ranges")]).then(
      ([u, r]) => alive && setOptions({ units: u.units, ageRanges: r.items }),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, []);
  return options;
}
