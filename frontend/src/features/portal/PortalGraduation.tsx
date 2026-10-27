import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { InfoGraduation, InfoLevel } from "./usePortalInfo";

function Swatch({ level }: { level: InfoLevel }) {
  return (
    <span aria-hidden="true" className="flex h-4 w-12 shrink-0 overflow-hidden rounded border">
      <span className="flex-1" style={{ backgroundColor: level.color }} />
      {level.secondaryColor && <span className="flex-1" style={{ backgroundColor: level.secondaryColor }} />}
    </span>
  );
}

// RF-POR-07, RN-GRA-04: faixa atual e histórico por modalidade; sem progresso. No portal reduzido a lista nem chega (RN-INA-08).
export function PortalGraduation({ items }: { items: InfoGraduation[] }) {
  const { t } = useTranslation();
  const k = "portalInfo.graduation.";
  const line = (l: InfoLevel) => t(`${k}line`, { level: l.levelName, degree: l.degree });
  return (
    <Card heading="h2" title={t(`${k}title`)}>
      <ul className="flex flex-col divide-y">
        {items.map((g) => (
          <li key={g.modality.id} className="flex flex-col gap-2 py-3 text-sm first:pt-0 last:pb-0">
            <PortalTag>{g.modality.name}</PortalTag>
            <div className="flex items-center gap-3">
              {g.belt && <Swatch level={g.belt} />}
              <p className="break-words">
                <span className="text-muted-foreground">{t(`${k}current`)}: </span>
                <span className="font-medium">{g.belt ? line(g.belt) : t(`${k}none`)}</span>
              </p>
            </div>
            {g.history.length > 0 && (
              <div>
                <p className="font-medium">{t(`${k}history`)}</p>
                <ul className="flex flex-col gap-1">
                  {g.history.map((h) => (
                    <li key={h.id} className="flex flex-wrap items-center gap-x-2">
                      <Swatch level={h} />
                      <span className="break-words">{line(h)}</span>
                      {h.achievedOn && <span className="text-muted-foreground">{t(`${k}on`, { date: formatDate(h.achievedOn) })}</span>}
                      {h.origin && <PortalTag>{t(`${k}origin`)}</PortalTag>}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </li>
        ))}
      </ul>
    </Card>
  );
}
