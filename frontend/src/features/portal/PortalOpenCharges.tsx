import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDate } from "@/lib/format";
import { PixProofDialog } from "./PixProofDialog";
import { PortalTag } from "./PortalTag";
import type { FinanceCharge } from "./usePortalFinance";

type Props = { items: FinanceCharge[]; showStudent: boolean; onChanged: () => void };

// RF-POR-05, RN-POR-02: cobranças em aberto (vencidas primeiro, ordem do servidor) com composição e "Pagar". Marcar várias soma o total e
// libera "Pagar selecionadas" só quando são da mesma unidade (D2, D4). Em conferência não tem "Pagar".
export function PortalOpenCharges({ items, showStudent, onChanged }: Props) {
  const { t } = useTranslation();
  const [picked, setPicked] = useState<string[]>([]);
  const [paying, setPaying] = useState<string[]>();
  const chosen = items.filter((c) => c.canPay && picked.includes(c.id));
  const mixed = new Set(chosen.map((c) => c.unit.id)).size > 1;
  const toggle = (id: string) => setPicked((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]));
  return (
    <Card heading="h2" title={t("portalFinance.open.title")}>
      {items.length === 0 ? (
        <EmptyState title={t("portalFinance.open.emptyTitle")} text={t("portalFinance.open.emptyText")} />
      ) : (
        <>
          <ul className="flex flex-col divide-y">
            {items.map((c) => {
              const what = showStudent ? `${c.referenceLabel} · ${c.studentName}` : c.referenceLabel;
              return (
                <li key={c.id} className="flex flex-col gap-2 py-3">
                  <div className="flex flex-wrap items-center gap-2">
                    {c.canPay && (
                      <input
                        type="checkbox"
                        className="h-6 w-6"
                        aria-label={t("portalFinance.open.select", { what })}
                        checked={picked.includes(c.id)}
                        onChange={() => toggle(c.id)}
                      />
                    )}
                    <PortalTag tone={c.situation === "OVERDUE" ? "alert" : "neutral"}>{t(`portalFinance.open.situation.${c.situation}`)}</PortalTag>
                  </div>
                  <p className="break-words font-medium">{c.referenceLabel}</p>
                  {showStudent && <p className="break-words">{c.studentName}</p>}
                  <p className="break-words text-muted-foreground">{t("portalFinance.open.unit", { unit: c.unit.name })}</p>
                  <p className="text-muted-foreground">
                    {t("portalFinance.open.dueOn", { date: formatDate(c.dueOn) })} · {formatCurrency(c.amountCents)}
                  </p>
                  <details>
                    <summary className="inline-flex min-h-11 cursor-pointer items-center underline">{t("portalFinance.open.composition")}</summary>
                    <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
                      <dt className="text-muted-foreground">{t("portalFinance.open.plan")}</dt>
                      <dd>{formatCurrency(c.planCents)}</dd>
                      <dt className="text-muted-foreground">{t("portalFinance.open.benefit")}</dt>
                      <dd>{formatCurrency(c.benefitCents)}</dd>
                      <dt className="font-medium">{t("portalFinance.open.final")}</dt>
                      <dd className="font-medium">{formatCurrency(c.amountCents)}</dd>
                    </dl>
                  </details>
                  {c.canPay && (
                    <Button className="min-h-11 self-start" aria-label={t("portalFinance.open.payItem", { what })} onClick={() => setPaying([c.id])}>
                      {t("portalFinance.open.pay")}
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
          {chosen.length > 0 && (
            <div className="mt-3 flex flex-col gap-2 border-t pt-3">
              <p role="status" className="font-medium">
                {t("portalFinance.open.selected", { count: chosen.length, value: formatCurrency(chosen.reduce((n, c) => n + c.amountCents, 0)) })}
              </p>
              {mixed && (
                <p role="alert" className="text-sm text-destructive">
                  {t("portalFinance.open.mixedUnits")}
                </p>
              )}
              <Button className="min-h-11 self-start" disabled={mixed} onClick={() => setPaying(chosen.map((c) => c.id))}>
                {t("portalFinance.open.paySelected")}
              </Button>
            </div>
          )}
        </>
      )}
      {paying && (
        <PixProofDialog
          chargeIds={paying}
          onClose={() => setPaying(undefined)}
          onSent={() => {
            setPicked([]);
            onChanged();
          }}
        />
      )}
    </Card>
  );
}
