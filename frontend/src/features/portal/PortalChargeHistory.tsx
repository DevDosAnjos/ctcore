import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { formatCurrency, formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import { portalReceiptUrl, type FinanceHistoryItem } from "./usePortalFinance";

type Props = { items: FinanceHistoryItem[]; year: number; years: number[]; onYear: (year: number) => void };

// RF-POR-05: histórico por ano (pagas, estornadas, canceladas e renegociadas) com meio, data e "Baixar recibo" nas pagas.
export function PortalChargeHistory({ items, year, years, onYear }: Props) {
  const { t } = useTranslation();
  const options = years.includes(year) ? years : [year, ...years];
  return (
    <Card
      heading="h2"
      title={t("portalFinance.history.title")}
      actions={
        <label className="flex items-center gap-2">
          <span className="text-sm font-medium">{t("portalFinance.history.year")}</span>
          <select className="min-h-11 rounded-md border bg-background px-2" value={year} onChange={(e) => onYear(Number(e.target.value))}>
            {options.map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </label>
      }
    >
      {items.length === 0 ? (
        <EmptyState title={t("portalFinance.history.emptyTitle")} text={t("portalFinance.history.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((h) => (
            <li key={h.id} className="flex flex-col gap-1 py-3">
              <div className="flex flex-wrap items-center gap-2">
                <PortalTag tone={h.status === "PAID" ? "neutral" : "alert"}>{t(`portalFinance.history.status.${h.status}`)}</PortalTag>
                <span className="break-words font-medium">{h.referenceLabel}</span>
              </div>
              <p className="text-muted-foreground">
                {t("portalFinance.history.dueOn", { date: formatDate(h.dueOn) })} · {formatCurrency(h.amountCents)}
              </p>
              {h.status === "PAID" && (
                <p className="text-muted-foreground">
                  {h.paidAt ? t("portalFinance.history.paidOn", { date: formatDate(h.paidAt) }) : "—"} · {h.method ? t("portalFinance.history.method", { method: t(`payments.method.${h.method}`) }) : "—"}
                </p>
              )}
              {h.receipt && (
                <a
                  className="inline-flex min-h-11 items-center self-start underline"
                  href={portalReceiptUrl(h.receipt.code)}
                  target="_blank"
                  rel="noreferrer"
                  aria-label={t("portalFinance.history.receiptItem", { what: h.referenceLabel })}
                >
                  {t("portalFinance.history.receipt")}
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
