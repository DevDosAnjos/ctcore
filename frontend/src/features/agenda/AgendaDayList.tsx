import { useTranslation } from "react-i18next";
import { EmptyState } from "@/components/states";
import { br, weekday } from "./agendaDates";
import { noClassOn } from "./AgendaWeekGrid";
import { LessonCard } from "./LessonCard";
import type { AgendaData } from "./useAgenda";

// R14: a lista do dia, no celular. Mesmas marcações da grade.
export function AgendaDayList({ data, day }: { data: AgendaData; day: string }) {
  const { t } = useTranslation();
  const off = noClassOn(data, day);
  const lessons = data.lessons.filter((l) => l.date === day);
  return (
    <section className="flex flex-col gap-3" aria-label={t("agenda.dayLabel", { day: t(`agenda.weekdaysLong.${weekday(day)}`), date: br(day) })}>
      {off && (
        <p className="rounded-md bg-muted p-2 text-sm">
          <span className="font-medium">{t("agenda.noClass")}</span> · {off.reason}
        </p>
      )}
      {lessons.length === 0 ? (
        <EmptyState title={t("agenda.emptyTitle")} text={t("agenda.emptyText")} />
      ) : (
        <ul className="flex flex-col gap-2">
          {lessons.map((l) => (
            <LessonCard key={l.id} lesson={l} />
          ))}
        </ul>
      )}
    </section>
  );
}
