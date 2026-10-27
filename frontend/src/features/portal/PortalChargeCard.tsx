import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDate } from "@/lib/format";
import { PixProofDialog } from "./PixProofDialog";
import { PortalTag } from "./PortalTag";
import type { HomeCharge } from "./usePortalHome";

type Props = { items: HomeCharge[]; nextDueOn: string | null; label: (charge: HomeCharge) => string | null; onChanged: () => void };

// RF-POR-02, RN-POR-04: a cobrança que pede ação (vencida antes de vencendo) com "Pagar", ou "Tudo em dia". "Pagar" abre o Pix com comprovante (D10).
export function PortalChargeCard({ items, nextDueOn, label, onChanged }: Props) {
  const { t } = useTranslation();
  const [paying, setPaying] = useState<string>();
  return (
    <Card heading="h2" title={t("portalHome.charges.title")}>
      {items.length === 0 ? (
        <div>
          <p className="text-lg font-semibold">{t("portalHome.charges.upToDate")}</p>
          {nextDueOn && <p className="text-muted-foreground">{t("portalHome.charges.nextDue", { date: formatDate(nextDueOn) })}</p>}
        </div>
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((c) => {
            const what = c.description ?? t("portalHome.charges.generic");
            const tag = label(c);
            return (
              <li key={c.id} className="flex flex-col gap-2 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <PortalTag tone={c.situation === "OVERDUE" ? "alert" : "neutral"}>{t(`portalHome.charges.situation.${c.situation}`)}</PortalTag>
                  {tag && <PortalTag>{tag}</PortalTag>}
                </div>
                <p className="break-words font-medium">{what}</p>
                <p className="text-muted-foreground">
                  {t("portalHome.charges.dueOn", { date: formatDate(c.dueOn) })} · {formatCurrency(c.amountCents)}
                </p>
                {c.canPay && (
                  <Button className="min-h-11 self-start" aria-label={t("portalHome.charges.payItem", { what })} onClick={() => setPaying(c.id)}>
                    {t("portalHome.charges.pay")}
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      {paying && <PixProofDialog chargeId={paying} onClose={() => setPaying(undefined)} onSent={onChanged} />}
    </Card>
  );
}
