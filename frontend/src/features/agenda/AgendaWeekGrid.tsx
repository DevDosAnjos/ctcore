import { useTranslation } from "react-i18next";
import { br, brShort, weekDays } from "./agendaDates";
import { LessonCard } from "./LessonCard";
import type { AgendaData } from "./useAgenda";

export const noClassOn = (data: AgendaData, day: string) => data.noClassDays.find((n) => n.startsOn <= day && day <= n.endsOn);

// R13: segunda a domingo em colunas, horários de início em linhas. Dia sem aula fica sombreado e com o motivo em texto.
export function AgendaWeekGrid({ data, anchor }: { data: AgendaData; anchor: string }) {
  const { t } = useTranslation();
  const days = weekDays(anchor);
  const times = [...new Set(data.lessons.map((l) => l.startsAt))].sort();
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[56rem] table-fixed border-collapse text-left">
        <caption className="sr-only">{t("agenda.gridCaption", { from: br(days[0]), to: br(days[6]) })}</caption>
        <thead>
          <tr>
            <th scope="col" className="w-16 p-2">
              <span className="sr-only">{t("agenda.time")}</span>
            </th>
            {days.map((d, i) => {
              const off = noClassOn(data, d);
              return (
                <th key={d} scope="col" className={`border-b p-2 align-top text-sm ${off ? "bg-muted" : ""}`}>
                  <span className="block">{t(`agenda.weekdays.${i + 1}`)}</span>
                  <span className="block font-normal text-muted-foreground">{brShort(d)}</span>
                  {off && (
                    <span className="mt-1 block text-xs font-medium">
                      {t("agenda.noClass")}
                      <span className="block font-normal text-muted-foreground">{off.reason}</span>
                    </span>
                  )}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {times.map((time) => (
            <tr key={time}>
              <th scope="row" className="border-b p-2 align-top text-sm font-medium">
                {time}
              </th>
              {days.map((d) => {
                const found = data.lessons.filter((l) => l.date === d && l.startsAt === time);
                return (
                  <td key={d} className={`border-b p-1 align-top ${noClassOn(data, d) ? "bg-muted" : ""}`}>
                    {found.length > 0 && (
                      <ul className="flex flex-col gap-1">
                        {found.map((l) => (
                          <LessonCard key={l.id} lesson={l} />
                        ))}
                      </ul>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
