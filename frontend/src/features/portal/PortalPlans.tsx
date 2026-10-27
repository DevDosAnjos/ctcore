import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { formatCurrency, formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { InfoPlan } from "./usePortalInfo";

// RF-POR-07, RN-POR-07: plano, benefício vigente, mensalidade e vencimento, só leitura (mudar é pedido). A mensalidade vem pronta do servidor.
export function PortalPlans({ items }: { items: InfoPlan[] }) {
  const { t } = useTranslation();
  const k = "portalInfo.plans.";
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      <ul className="flex flex-col divide-y">
        {items.map((p) => (
          <li key={p.modality.id} className="flex flex-col gap-1 py-3 text-sm first:pt-0 last:pb-0">
            <PortalTag>{p.modality.name}</PortalTag>
            <p>
              <span className="text-muted-foreground">{t(`${k}plan`)}: </span>
              {p.plan.name} · {formatCurrency(p.planCents)}
            </p>
            <p>
              <span className="text-muted-foreground">{t(`${k}benefit`)}: </span>
              {p.benefit
                ? [
                    p.benefit.type === "PERCENT" ? t(`${k}benefitPercent`, { value: p.benefit.value }) : t(`${k}benefitAmount`, { value: formatCurrency(p.benefit.value) }),
                    p.benefit.validUntil ? t(`${k}benefitUntil`, { date: formatDate(p.benefit.validUntil) }) : "",
                  ]
                    .filter(Boolean)
                    .join(" ")
                : t(`${k}noBenefit`)}
            </p>
            <p className="font-medium">
              <span className="font-normal text-muted-foreground">{t(`${k}monthly`)}: </span>
              {formatCurrency(p.monthlyCents)}
            </p>
            <p>
              <span className="text-muted-foreground">{t(`${k}dueDay`)}: </span>
              {t(`${k}dueDayValue`, { day: p.dueDay })}
            </p>
            {p.nextPlan && (
              <p>
                <span className="text-muted-foreground">{t(`${k}next`)}: </span>
                {t(`${k}nextValue`, { name: p.nextPlan.name, value: formatCurrency(p.nextPlan.valueCents) })}
              </p>
            )}
          </li>
        ))}
      </ul>
      <p className="mt-3 text-sm text-muted-foreground">{t(`${k}changeHint`)}</p>
    </Card>
  );
}
