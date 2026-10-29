import { useTranslation } from "react-i18next";
import { brShort, weekDays, weekday } from "@/features/agenda/agendaDates";
import { PortalLessonItem } from "./PortalLessonItem";
import { PortalTag } from "./PortalTag";
import type { PortalScheduleData } from "./usePortalSchedule";

// RF-POR-04: no celular, abas dos dias da semana e a lista do dia escolhido.
export function PortalAgendaDayList({ data, day, onDay }: { data: PortalScheduleData; day: string; onDay: (d: string) => void }) {
  const { t } = useTranslation();
  const days = weekDays(data.weekStart);
  const lessons = data.lessons.filter((l) => l.date === day);
  const off = data.noClassDays.filter((n) => n.date === day);
  return (
    <div>
      <div role="tablist" aria-label={t("portalAgenda.daysLabel")} className="mb-3 grid grid-cols-7 gap-1">
        {days.map((d) => (
          <button
            key={d}
            type="button"
            role="tab"
            id={`agenda-tab-${d}`}
            aria-selected={d === day}
            aria-controls="agenda-panel"
            className={`flex min-h-11 flex-col items-center justify-center rounded-md border px-0.5 text-xs ${d === day ? "bg-primary text-primary-foreground" : "bg-card"}`}
            onClick={() => onDay(d)}
          >
            <span>{t(`agenda.weekdays.${weekday(d)}`)}</span>
            <span>{brShort(d)}</span>
          </button>
        ))}
      </div>
      <div role="tabpanel" id="agenda-panel" aria-labelledby={`agenda-tab-${day}`} className="flex flex-col gap-2">
        {off.map((n) => (
          <p key={n.unit.id + n.reason} className="break-words rounded-md bg-muted p-2">
            <PortalTag>{t("portalAgenda.noClass")}</PortalTag> {n.reason}
          </p>
        ))}
        {lessons.length === 0 && off.length === 0 && <p className="text-muted-foreground">{t("portalAgenda.noLessonsDay")}</p>}
        {lessons.map((l) => (
          <PortalLessonItem key={l.id} lesson={l} />
        ))}
      </div>
    </div>
  );
}
