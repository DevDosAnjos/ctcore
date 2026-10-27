import { useTranslation } from "react-i18next";
import { brShort, weekDays, weekday } from "@/features/agenda/agendaDates";
import { PortalLessonItem } from "./PortalLessonItem";
import { PortalTag } from "./PortalTag";
import type { PortalScheduleData } from "./usePortalSchedule";

// RF-POR-04: grade de 7 colunas (segunda a domingo) por horário de início; o dia sem aula fica sombreado e com texto.
export function PortalAgendaGrid({ data }: { data: PortalScheduleData }) {
  const { t } = useTranslation();
  const days = weekDays(data.weekStart);
  const starts = [...new Set(data.lessons.map((l) => l.startsAt))].sort();
  const off = (d: string) => data.noClassDays.filter((n) => n.date === d);
  return (
    <table aria-label={t("portalAgenda.gridLabel")} className="w-full table-fixed border-separate border-spacing-1">
      <thead>
        <tr>
          <th scope="col" className="w-14 text-left text-sm font-medium">
            {t("portalAgenda.timeColumn")}
          </th>
          {days.map((d) => (
            <th key={d} scope="col" className={`rounded-md p-1 text-sm font-medium ${off(d).length ? "bg-muted" : ""}`}>
              <span className={d === data.today ? "underline" : ""}>
                {t(`agenda.weekdays.${weekday(d)}`)} {brShort(d)}
              </span>
              {off(d).map((n) => (
                <span key={n.unit.id + n.reason} className="mt-1 block break-words font-normal">
                  <PortalTag>{t("portalAgenda.noClass")}</PortalTag> {n.reason}
                </span>
              ))}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {starts.map((s) => (
          <tr key={s} className="align-top">
            <th scope="row" className="text-left text-sm font-medium">
              {s}
            </th>
            {days.map((d) => (
              <td key={d} className={`rounded-md p-0.5 ${off(d).length ? "bg-muted" : ""}`}>
                <div className="flex flex-col gap-1">
                  {data.lessons
                    .filter((l) => l.date === d && l.startsAt === s)
                    .map((l) => (
                      <PortalLessonItem key={l.id} lesson={l} />
                    ))}
                </div>
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
