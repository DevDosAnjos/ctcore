import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

export function Pagination({ page, pages, onPage }: { page: number; pages: number; onPage: (p: number) => void }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t("query.results")} className="flex items-center justify-between gap-2 py-2">
      <Button variant="outline" className="min-h-11" disabled={page <= 1} onClick={() => onPage(page - 1)}>
        {t("query.prev")}
      </Button>
      <span aria-live="polite">{t("query.page", { page, pages })}</span>
      <Button variant="outline" className="min-h-11" disabled={page >= pages} onClick={() => onPage(page + 1)}>
        {t("query.nextPage")}
      </Button>
    </nav>
  );
}
