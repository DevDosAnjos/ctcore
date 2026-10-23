import { useCallback, useState } from "react";

export const OPTIONAL_COLUMNS = ["modalities", "status", "financial", "unit", "age", "ageRange", "createdAt"] as const;
export type OptionalColumn = (typeof OPTIONAL_COLUMNS)[number];

const storageKey = (accountId: string) => `ctcore.studentColumns.${accountId}`;

// Preferência de colunas por conta, só neste navegador (D5): sem armazenamento, ou com valor estranho, abre com todas ligadas.
function read(accountId: string): OptionalColumn[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(storageKey(accountId)) ?? "null");
    if (Array.isArray(saved)) return OPTIONAL_COLUMNS.filter((c) => saved.includes(c));
  } catch {
    // sem armazenamento ou valor inválido
  }
  return [...OPTIONAL_COLUMNS];
}

export function useStudentColumns(accountId: string) {
  // A conta troca a chave do componente que usa o hook; o estado inicial lê a escolha dela.
  const [visible, setVisible] = useState(() => read(accountId));
  const toggle = useCallback(
    (column: OptionalColumn) => {
      setVisible((current) => {
        const next = OPTIONAL_COLUMNS.filter((c) => (c === column ? !current.includes(c) : current.includes(c)));
        try {
          localStorage.setItem(storageKey(accountId), JSON.stringify(next));
        } catch {
          // a escolha vale só nesta página
        }
        return next;
      });
    },
    [accountId],
  );
  return { visible, toggle };
}
