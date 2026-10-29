import type { ReactNode } from "react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { Checkbox } from "@/components/ui/checkbox";

export type Column<T> = { key: string; label: string; sortable?: boolean; numeric?: boolean; render: (row: T) => ReactNode };

// ordem: "nome" (crescente) ou "-nome" (decrescente). Só recebe a página atual.
// Com `selectable`, `selected` guarda as chaves marcadas (`rowKey`) e `onSelectionChange` recebe a nova lista.
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  order,
  onOrder,
  selectable,
  selected = [],
  onSelectionChange,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  order: string;
  onOrder: (order: string) => void;
  selectable?: boolean;
  selected?: string[];
  onSelectionChange?: (keys: string[]) => void;
}) {
  const { t } = useTranslation();
  const desc = order.startsWith("-");
  const sortKey = order.replace(/^-/, "");
  const keys = rows.map(rowKey);
  const all = keys.length > 0 && keys.every((k) => selected.includes(k));
  const toggle = (k: string) => onSelectionChange?.(selected.includes(k) ? selected.filter((s) => s !== k) : [...selected, k]);
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead className="bg-surface-soft text-xs uppercase text-muted-foreground">
          <tr>
            {selectable && (
              <th scope="col" className="w-10 px-3 py-2">
                <Checkbox
                  aria-label={t("ui.selectAll")}
                  checked={all}
                  onChange={() => onSelectionChange?.(all ? selected.filter((s) => !keys.includes(s)) : [...new Set([...selected, ...keys])])}
                />
              </th>
            )}
            {columns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={cn("px-3 py-2", c.numeric && "text-right")}
                aria-sort={c.key === sortKey ? (desc ? "descending" : "ascending") : undefined}
              >
                {c.sortable ? (
                  <button
                    type="button"
                    className="min-h-11 min-w-11 font-semibold uppercase focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    aria-label={t("query.sortBy", { column: c.label })}
                    onClick={() => onOrder(c.key === sortKey && !desc ? `-${c.key}` : c.key)}
                  >
                    {c.label}
                    {c.key === sortKey && (desc ? <ArrowDown aria-hidden="true" /> : <ArrowUp aria-hidden="true" />)}
                  </button>
                ) : (
                  c.label
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={rowKey(r)} className="h-[52px] border-t border-divider hover:bg-surface-soft">
              {selectable && (
                <td className="px-3">
                  <Checkbox aria-label={t("ui.selectRow")} checked={selected.includes(rowKey(r))} onChange={() => toggle(rowKey(r))} />
                </td>
              )}
              {columns.map((c) => (
                <td key={c.key} className={cn("px-3 py-2", c.numeric && "text-right tabular-nums")}>
                  {c.render(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
