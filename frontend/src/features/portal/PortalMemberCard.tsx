import { useState } from "react";
import { useTranslation } from "react-i18next";
import { formatDate, formatDateTime } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { PortalCardData } from "./usePortalCard";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

// RF-POR-03, RNF-22: a cor da organização vai na faixa superior e na borda, nunca como fundo do texto. Sem QR code.
export function PortalMemberCard({ data, offline }: { data: PortalCardData; offline: boolean }) {
  const { t } = useTranslation();
  const [logoBroken, setLogoBroken] = useState(false);
  const [photoBroken, setPhotoBroken] = useState(false);
  const { organization: org, student } = data;
  const color = org.color ?? undefined;
  return (
    <article
      aria-label={t("portalCard.cardLabel", { name: student.name })}
      className="overflow-hidden rounded-lg border-2 bg-card text-card-foreground"
      style={{ borderColor: color }}
    >
      <div className="flex min-h-12 items-center gap-2 px-4 py-2" style={{ backgroundColor: color }}>
        {org.logoUrl && !logoBroken && <img src={org.logoUrl} alt="" className="h-8 w-8 rounded bg-background object-contain" onError={() => setLogoBroken(true)} />}
        <p className="break-words rounded bg-background px-2 py-0.5 font-semibold text-foreground">{org.name}</p>
      </div>
      <div className="flex flex-col gap-4 p-4">
        <div className="flex items-center gap-4">
          {student.photoUrl && !photoBroken ? (
            <img src={student.photoUrl} alt="" className="h-20 w-20 shrink-0 rounded-full border object-cover" onError={() => setPhotoBroken(true)} />
          ) : (
            <span
              aria-hidden="true"
              className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full border bg-secondary text-2xl font-semibold text-secondary-foreground"
            >
              {initials(student.name)}
            </span>
          )}
          <div className="flex min-w-0 flex-col gap-2">
            <h2 className="break-words text-xl font-semibold">{student.name}</h2>
            <div className="flex flex-wrap items-center gap-2">
              <PortalTag tone={data.status === "ACTIVE" ? "strong" : "neutral"}>{t(`portalCard.status.${data.status}`)}</PortalTag>
              <span className="text-sm text-muted-foreground">{t("portalCard.statusOn", { date: formatDate(data.today) })}</span>
            </div>
          </div>
        </div>
        {data.modalities.length === 0 ? (
          <div>
            <p className="font-medium">{t("portalCard.emptyTitle")}</p>
            <p className="text-muted-foreground">{t("portalCard.emptyText")}</p>
          </div>
        ) : (
          <section aria-label={t("portalCard.modalitiesTitle")}>
            <ul className="flex flex-col divide-y">
              {data.modalities.map((m) => (
                <li key={`${m.modality.id}|${m.unit.id}`} className="flex items-center gap-3 py-3">
                  <span aria-hidden="true" className="flex h-4 w-12 shrink-0 overflow-hidden rounded border">
                    {m.belt && <span className="flex-1" style={{ backgroundColor: m.belt.color }} />}
                    {m.belt?.secondaryColor && <span className="flex-1" style={{ backgroundColor: m.belt.secondaryColor }} />}
                  </span>
                  <div className="flex min-w-0 flex-col gap-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="break-words font-medium">{m.modality.name}</p>
                      <PortalTag>{m.unit.name}</PortalTag>
                    </div>
                    <p className="break-words text-muted-foreground">
                      {m.belt
                        ? t("portalHome.belts.line", {
                            level: m.belt.levelName,
                            degree: m.belt.degree,
                          })
                        : t("portalCard.noBelt")}
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        )}
        <div className="flex flex-wrap items-center gap-2 border-t pt-3 text-sm text-muted-foreground">
          {offline && (
            <p role="status">
              <PortalTag tone="alert">{t("portalCard.offline")}</PortalTag>
            </p>
          )}
          <p>
            {t("portalCard.updatedAt", {
              date: formatDateTime(data.generatedAt, data.timezone),
            })}
          </p>
        </div>
      </div>
    </article>
  );
}
