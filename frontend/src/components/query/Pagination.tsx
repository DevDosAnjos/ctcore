import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

// Tamanhos de página do contrato (`size` em docs/openapi.yaml): 25, 50 ou 100; padrão 25.
export const PAGE_SIZES = [25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 25;

export function Pagination({
  page,
  pages,
  total,
  size,
  onPage,
  onSize,
}: {
  page: number;
  pages: number;
  total: number;
  size: number;
  onPage: (p: number) => void;
  onSize: (s: number) => void;
}) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t("query.results")} className="flex flex-wrap items-center justify-between gap-2 py-2">
      <span>{t("query.total", { count: total })}</span>
      <label className="flex items-center gap-2">
        {t("query.pageSize")}
        <select
          className="min-h-11 rounded-md border bg-background px-2"
          value={size}
          onChange={(e) => onSize(Number(e.target.value))}
        >
          {PAGE_SIZES.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="outline" className="min-h-11" disabled={page <= 1} onClick={() => onPage(page - 1)}>
          {t("query.prev")}
        </Button>
        <span aria-live="polite">{t("query.page", { page, pages })}</span>
        <Button variant="outline" className="min-h-11" disabled={page >= pages} onClick={() => onPage(page + 1)}>
          {t("query.nextPage")}
        </Button>
      </div>
    </nav>
  );
}
