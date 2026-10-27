import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { HomeBelt } from "./usePortalHome";

type Props = { items: HomeBelt[]; showLabel: boolean };

// RF-POR-02, RN-GRA-04: a faixa atual por modalidade, "Nova" nos 14 dias seguintes ao registro. Sem progresso de graduação (RN-POR-09).
export function PortalBelts({ items, showLabel }: Props) {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={t("portalHome.belts.title")}>
      {items.length === 0 ? (
        <EmptyState title={t("portalHome.belts.emptyTitle")} text={t("portalHome.belts.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((b) => (
            <li key={b.modality.id} className="flex items-center gap-3 py-3">
              <span aria-hidden="true" className="flex h-4 w-12 shrink-0 overflow-hidden rounded border">
                <span className="flex-1" style={{ backgroundColor: b.color }} />
                {b.secondaryColor && <span className="flex-1" style={{ backgroundColor: b.secondaryColor }} />}
              </span>
              <div className="flex min-w-0 flex-col gap-1">
                <div className="flex flex-wrap items-center gap-2">
                  {showLabel && <PortalTag>{b.modality.name}</PortalTag>}
                  {b.isNew && <PortalTag tone="strong">{t("portalHome.belts.new")}</PortalTag>}
                </div>
                <p className="break-words font-medium">{t("portalHome.belts.line", { level: b.levelName, degree: b.degree })}</p>
                {b.achievedOn && <p className="text-muted-foreground">{t("portalHome.belts.since", { date: formatDate(b.achievedOn) })}</p>}
              </div>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
