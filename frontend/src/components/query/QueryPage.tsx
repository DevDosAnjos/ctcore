import { useEffect, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { DataTable, type Column } from "./DataTable";
import { DEFAULT_PAGE_SIZE, PAGE_SIZES, Pagination } from "./Pagination";
import { useQueryParams } from "./useQueryParams";

export type Query = { q: string; pagina: number; ordem: string; filtros: Record<string, string>; tamanho: number };
// `counts` são totais auxiliares da tela (ex.: filtros rápidos), vindos do mesmo pedido.
export type Page<T> = { rows: T[]; total: number; counts?: Record<string, number> };

// Consulta: busca, filtros, ordenação, página e tamanho vivem no endereço (RNF-27). Nunca pede mais que uma página.
// A tela mapeia Query para o contrato da rota (`tamanho` vira `size`, sempre 25, 50 ou 100); erro com status 403 vira "sem permissão".
// `fetchPage` deve ter identidade estável (definida fora do componente ou em useCallback).
export function QueryPage<T>({
  columns,
  fetchPage,
  rowKey,
  filterKeys = [],
  filters,
  emptyAction,
  cards,
  footer,
}: {
  columns: Column<T>[];
  fetchPage: (q: Query) => Promise<Page<T>>;
  rowKey: (row: T) => string;
  filterKeys?: string[];
  filters?: (p: { get: (k: string) => string; set: (k: string, v: string) => void; counts?: Record<string, number> }) => ReactNode;
  emptyAction?: ReactNode;
  // Opcionais: abaixo de md a lista sai em cartões no lugar da tabela; `footer` recebe a página carregada (ex.: totais).
  cards?: (row: T) => ReactNode;
  footer?: (page: Page<T>) => ReactNode;
}) {
  const { t } = useTranslation();
  const { get, set, clear } = useQueryParams();

  const q = get("q");
  const rawPage = Number(get("pagina"));
  const pagina = Number.isInteger(rawPage) && rawPage >= 1 ? rawPage : 1;
  const rawOrder = get("ordem");
  const ordem = columns.some((c) => c.sortable && c.key === rawOrder.replace(/^-/, "")) ? rawOrder : "";
  const rawSize = Number(get("tamanho"));
  const tamanho = (PAGE_SIZES as readonly number[]).includes(rawSize) ? rawSize : DEFAULT_PAGE_SIZE;
  const filtros = Object.fromEntries(filterKeys.map((k) => [k, get(k)]).filter(([, v]) => v));
  const filtrosKey = JSON.stringify(filtros);
  const hasFilter = Boolean(q) || Object.keys(filtros).length > 0;

  // O resultado guarda a chave da consulta que o gerou; chave diferente da atual significa carregando.
  const [tick, setTick] = useState(0);
  const key = JSON.stringify([q, pagina, ordem, filtrosKey, tamanho, tick]);
  const [done, setDone] = useState<{ key: string; s: "error" | "forbidden" | "ok"; data?: Page<T> }>();
  const state = done?.key === key ? done : { s: "loading" as const, data: undefined };
  // Os totais auxiliares ficam à vista enquanto a página seguinte carrega.
  const [lastCounts, setLastCounts] = useState<Record<string, number>>();

  useEffect(() => {
    let alive = true;
    fetchPage({ q, pagina, ordem, filtros: JSON.parse(filtrosKey), tamanho }).then(
      (data) => {
        if (!alive) return;
        // Página fora do intervalo: volta para a primeira, sem erro (CA6).
        if (pagina > 1 && data.rows.length === 0) set({ pagina: "" }, true);
        else {
          setDone({ key, s: "ok", data });
          if (data.counts) setLastCounts(data.counts);
        }
      },
      (e: { status?: number }) => {
        if (alive) setDone({ key, s: e?.status === 403 ? "forbidden" : "error" });
      },
    );
    return () => {
      alive = false;
    };
  }, [key, q, pagina, ordem, filtrosKey, tamanho, fetchPage, set]);

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
    const pages = Math.max(1, Math.ceil(state.data!.total / tamanho));
    body = (
      <>
        <div className={cards ? "hidden md:block" : undefined}>
          <DataTable columns={columns} rows={state.data!.rows} rowKey={rowKey} order={ordem} onOrder={(o) => set({ ordem: o })} />
        </div>
        {cards && (
          <ul className="flex flex-col gap-3 md:hidden">
            {state.data!.rows.map((r) => (
              <li key={rowKey(r)} className="rounded-md border p-3">
                {cards(r)}
              </li>
            ))}
          </ul>
        )}
        <Pagination
          page={pagina}
          pages={pages}
          total={state.data!.total}
          size={tamanho}
          onPage={(p) => set({ pagina: String(p) })}
          // Trocar o tamanho volta para a página 1; o padrão não aparece no endereço.
          onSize={(s) => set({ tamanho: s === DEFAULT_PAGE_SIZE ? "" : String(s) })}
        />
        {footer?.(state.data!)}
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
        {filters?.({ get, set: (k, v) => set({ [k]: v }), counts: lastCounts })}
      </form>
      {body}
    </div>
  );
}
