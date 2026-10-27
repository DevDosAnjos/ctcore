import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { HomeLesson } from "./usePortalHome";

type Props = { items: HomeLesson[]; label: (lesson: HomeLesson) => string | null };

// RF-POR-02, RN-AGE-01: próximas aulas, com a cancelada e a alterada em destaque (o resumo da alteração é da Agenda).
export function PortalNextLessons({ items, label }: Props) {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={t("portalHome.lessons.title")}>
      {items.length === 0 ? (
        <EmptyState title={t("portalHome.lessons.emptyTitle")} text={t("portalHome.lessons.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((l) => {
            const tag = label(l);
            const cancelled = l.status === "CANCELLED";
            return (
              <li key={l.id} className="flex flex-col gap-1 py-3">
                <div className="flex flex-wrap items-center gap-2">
                  {cancelled && <PortalTag tone="alert">{t("portalHome.lessons.cancelled")}</PortalTag>}
                  {l.changed && !cancelled && <PortalTag tone="strong">{t("portalHome.lessons.changed")}</PortalTag>}
                  {l.adhoc && <PortalTag>{t("portalHome.lessons.adhoc")}</PortalTag>}
                  {tag && <PortalTag>{tag}</PortalTag>}
                </div>
                <p className={`break-words font-medium ${cancelled ? "line-through" : ""}`}>{l.title}</p>
                <p className="text-muted-foreground">{t("portalHome.lessons.when", { date: formatDate(l.date), start: l.startsAt, end: l.endsAt })}</p>
                {cancelled && l.cancelReason && <p className="break-words">{t("portalHome.lessons.reason", { reason: l.cancelReason })}</p>}
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
