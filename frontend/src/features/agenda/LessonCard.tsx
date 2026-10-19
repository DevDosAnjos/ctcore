import { useTranslation } from "react-i18next";
import type { AgendaLesson } from "./useAgenda";

// D2: a tabela de modalidades não tem cor; ela vem de uma paleta fixa, escolhida pelo id. A cor nunca é a única indicação: a modalidade também vai em texto.
const HUES = [210, 30, 140, 280, 0, 175, 50, 320];
export const modalityHue = (id: string) => HUES[[...id].reduce((s, c) => s + c.charCodeAt(0), 0) % HUES.length];

export function LessonCard({ lesson }: { lesson: AgendaLesson }) {
  const { t } = useTranslation();
  const cancelled = lesson.status === "CANCELLED";
  const color = `hsl(${modalityHue(lesson.modality.id)} 60% 45%)`;
  return (
    <li className="flex min-w-0 flex-col gap-0.5 rounded-md border border-l-4 bg-card p-2 text-sm break-words" style={{ borderLeftColor: color }}>
      <span className="text-xs text-muted-foreground">
        {lesson.startsAt}–{lesson.endsAt}
      </span>
      <span className={cancelled ? "font-medium line-through" : "font-medium"}>{lesson.class.name}</span>
      <span>{lesson.teacher.name}</span>
      <span className="flex items-center gap-1 text-muted-foreground">
        <span aria-hidden="true" className="inline-block size-2 shrink-0 rounded-full" style={{ backgroundColor: color }} />
        {lesson.modality.name}
      </span>
      {lesson.substitute && <span className="w-fit rounded bg-secondary px-1.5 text-xs font-medium text-secondary-foreground">{t("agenda.substitute")}</span>}
      {cancelled && (
        <span className="flex flex-col text-xs">
          <span className="font-medium text-destructive">{t("agenda.cancelled")}</span>
          {lesson.cancelReason && <span className="text-muted-foreground">{lesson.cancelReason}</span>}
        </span>
      )}
    </li>
  );
}
