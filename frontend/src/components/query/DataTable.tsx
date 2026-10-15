import type { ReactNode } from "react";
import { ArrowDown, ArrowUp } from "lucide-react";
import { useTranslation } from "react-i18next";

export type Column<T> = { key: string; label: string; sortable?: boolean; render: (row: T) => ReactNode };

// ordem: "nome" (crescente) ou "-nome" (decrescente). Só recebe a página atual.
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  order,
  onOrder,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  order: string;
  onOrder: (order: string) => void;
}) {
  const { t } = useTranslation();
  const desc = order.startsWith("-");
  const sortKey = order.replace(/^-/, "");
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr>
            {columns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className="px-3 py-2"
                aria-sort={c.key === sortKey ? (desc ? "descending" : "ascending") : undefined}
              >
                {c.sortable ? (
                  <button
                    type="button"
                    className="min-h-11 min-w-11 font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
            <tr key={rowKey(r)} className="border-t">
              {columns.map((c) => (
                <td key={c.key} className="px-3 py-2">
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
