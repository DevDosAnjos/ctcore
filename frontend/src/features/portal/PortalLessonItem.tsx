import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { ScheduleLesson } from "./usePortalSchedule";

// RN-AGE-04, RN-AGE-05: a aula com seus avisos visuais (sempre com texto). O motivo e o resumo são texto, nunca HTML.
export function PortalLessonItem({ lesson: l }: { lesson: ScheduleLesson }) {
  const { t } = useTranslation();
  const cancelled = l.status === "CANCELLED";
  const reason =
    l.cancelReasonCode === "OTHER" || !l.cancelReasonCode
      ? (l.cancelReason ?? (l.cancelReasonCode ? t("agenda.cancelReasons.OTHER") : null))
      : t(`agenda.cancelReasons.${l.cancelReasonCode}`);
  const parts = [
    l.originalDate || l.originalStartsAt ? t("portalAgenda.wasOn", { date: formatDate(l.originalDate ?? l.date), time: l.originalStartsAt ?? l.startsAt }) : null,
    l.substitute ? t("portalAgenda.substituteName", { name: l.teacherName }) : null,
  ].filter(Boolean);
  const summary = parts.length ? parts.join(" · ") : t("portalAgenda.changedGeneric");
  return (
    <div className="flex flex-col gap-1 rounded-md border bg-card p-2 text-sm">
      <div className="flex flex-wrap items-center gap-1">
        <PortalTag>{t(`portalAgenda.kind.${l.kind}`)}</PortalTag>
        {cancelled && <PortalTag tone="alert">{t("portalAgenda.cancelled")}</PortalTag>}
        {l.changed && !cancelled && <PortalTag tone="strong">{t("portalAgenda.changed")}</PortalTag>}
        {l.past && <PortalTag>{t("portalAgenda.past")}</PortalTag>}
      </div>
      <p className={`break-words font-medium ${cancelled ? "line-through" : ""}`}>{l.title}</p>
      <p className="text-muted-foreground">{t("portalAgenda.when", { start: l.startsAt, end: l.endsAt })}</p>
      <p className="break-words text-muted-foreground">{t("portalAgenda.teacher", { name: l.teacherName })}</p>
      <p className="break-words text-muted-foreground">
        {l.modality.name} · {l.unit.name}
      </p>
      {cancelled && reason && <p className="break-words">{t("portalAgenda.reason", { reason })}</p>}
      {l.changed && !cancelled && <p className="break-words">{summary}</p>}
      {l.noticeId && (
        <Link to={`/portal/avisos?aviso=${l.noticeId}`} className="inline-flex min-h-11 items-center underline" aria-label={t("portalAgenda.viewNoticeOf", { title: l.title })}>
          {t("portalAgenda.viewNotice")}
        </Link>
      )}
    </div>
  );
}
