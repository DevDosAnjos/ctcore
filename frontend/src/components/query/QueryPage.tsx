import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { DataTable, type Column } from "./DataTable";
import { Pagination } from "./Pagination";
import { useQueryParams } from "./useQueryParams";

export type Query = { q: string; pagina: number; ordem: string; filtros: Record<string, string>; tamanho: number };
export type Page<T> = { rows: T[]; total: number };

// Consulta: busca, filtros, ordenação e página vivem no endereço (RNF-27). Nunca pede mais que uma página.
// A tela mapeia Query para o contrato da rota; erro com status 403 vira "sem permissão".
// `fetchPage` deve ter identidade estável (definida fora do componente ou em useCallback).
export function QueryPage<T>({
  columns,
  fetchPage,
  rowKey,
  pageSize = 20,
  filterKeys = [],
  filters,
  emptyAction,
}: {
  columns: Column<T>[];
  fetchPage: (q: Query) => Promise<Page<T>>;
  rowKey: (row: T) => string;
  pageSize?: number;
  filterKeys?: string[];
  filters?: (p: { get: (k: string) => string; set: (k: string, v: string) => void }) => ReactNode;
  emptyAction?: ReactNode;
}) {
  const { t } = useTranslation();
  const { get, set, clear } = useQueryParams();

  const q = get("q");
  const rawPage = Number(get("pagina"));
  const pagina = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  const rawOrder = get("ordem");
  const ordem = columns.some((c) => c.sortable && c.key === rawOrder.replace(/^-/, "")) ? rawOrder : "";
  const filtros = Object.fromEntries(filterKeys.map((k) => [k, get(k)]).filter(([, v]) => v));
  const filtrosKey = JSON.stringify(filtros);
  const hasFilter = Boolean(q) || Object.keys(filtros).length > 0;

  // O resultado guarda a chave da consulta que o gerou; chave diferente da atual significa carregando.
  const [tick, setTick] = useState(0);
  const key = JSON.stringify([q, pagina, ordem, filtrosKey, pageSize, tick]);
  const [done, setDone] = useState<{ key: string; s: "error" | "forbidden" | "ok"; data?: Page<T> }>();
  const state = done?.key === key ? done : { s: "loading" as const, data: undefined };

  useEffect(() => {
    let alive = true;
    fetchPage({ q, pagina, ordem, filtros: JSON.parse(filtrosKey), tamanho: pageSize }).then(
      (data) => {
        if (!alive) return;
        // Página fora do intervalo: volta para a primeira, sem erro (CA6).
        if (pagina > 1 && data.rows.length === 0) set({ pagina: "" }, true);
        else setDone({ key, s: "ok", data });
      },
      (e: { status?: number }) => {
        if (alive) setDone({ key, s: e?.status === 403 ? "forbidden" : "error" });
      },
    );
    return () => {
      alive = false;
    };
  }, [key, q, pagina, ordem, filtrosKey, pageSize, fetchPage, set]);

  let body: ReactNode;
  if (state.s === "loading") body = <LoadingState />;
  else if (state.s === "error") body = <ErrorState onRetry={() => setTick((n) => n + 1)} />;
  else if (state.s === "forbidden") body = <ForbiddenState />;
  else if (state.data!.total === 0 && hasFilter)
    body = (
      <EmptyState
        title={t("query.noResultTitle")}
        text={t("query.noResultText")}
        action={
          <Button className="min-h-11" onClick={clear}>
            {t("query.clear")}
          </Button>
        }
      />
    );
  else if (state.data!.total === 0) body = <EmptyState action={emptyAction} />;
  else {
    const pages = Math.max(1, Math.ceil(state.data!.total / pageSize));
    body = (
      <>
        <DataTable
          columns={columns}
          rows={state.data!.rows}
          rowKey={rowKey}
          order={ordem}
          onOrder={(o) => set({ ordem: o })}
        />
        <Pagination page={pagina} pages={pages} onPage={(p) => set({ pagina: String(p) })} />
      </>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <form role="search" className="flex flex-wrap items-end gap-2" onSubmit={(e) => e.preventDefault()}>
        <input
          type="search"
          aria-label={t("query.search")}
          className="min-h-11 min-w-0 flex-1 rounded-md border bg-background px-3"
          value={q}
          onChange={(e) => set({ q: e.target.value })}
        />
        {filters?.({ get, set: (k, v) => set({ [k]: v }) })}
      </form>
      {body}
    </div>
  );
}
