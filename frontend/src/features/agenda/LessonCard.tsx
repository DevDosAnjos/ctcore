import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import type { AgendaLesson } from "./useAgenda";

// D2: a tabela de modalidades não tem cor; ela vem de uma paleta fixa, escolhida pelo id. A cor nunca é a única indicação: a modalidade também vai em texto.
const HUES = [210, 30, 140, 280, 0, 175, 50, 320];
export const modalityHue = (id: string) => HUES[[...id].reduce((s, c) => s + c.charCodeAt(0), 0) % HUES.length];

// RF-AGE-03 (D5): o código é traduzido; "Outro" e a aula cancelada por dia sem aula mostram o texto que o servidor guardou.
export function cancelReasonText(lesson: Pick<AgendaLesson, "cancelReason" | "cancelReasonCode">, t: (key: string) => string) {
  return lesson.cancelReasonCode && lesson.cancelReasonCode !== "OTHER" ? t(`agenda.cancelReasons.${lesson.cancelReasonCode}`) : lesson.cancelReason;
}

// RF-AGE-04: a aula avulsa mostra o título no lugar do nome da turma.
export const lessonName = (lesson: Pick<AgendaLesson, "class" | "title">) => lesson.class?.name ?? lesson.title ?? "";

// RF-AGE-02: o cartão inteiro leva ao painel da aula.
export function LessonCard({ lesson }: { lesson: AgendaLesson }) {
  const { t } = useTranslation();
  const cancelled = lesson.status === "CANCELLED";
  const color = `hsl(${modalityHue(lesson.modality.id)} 60% 45%)`;
  return (
    <li className="min-w-0 rounded-md border border-l-4 bg-card text-sm break-words" style={{ borderLeftColor: color }}>
      <Link to={`/agenda/aulas/${lesson.id}`} className="flex min-h-11 min-w-0 flex-col gap-0.5 p-2">
        <span className="text-xs text-muted-foreground">
          {lesson.startsAt}–{lesson.endsAt}
        </span>
        <span className={cancelled ? "font-medium line-through" : "font-medium"}>{lessonName(lesson)}</span>
        <span>{lesson.teacher.name}</span>
        <span className="flex items-center gap-1 text-muted-foreground">
          <span aria-hidden="true" className="inline-block size-2 shrink-0 rounded-full" style={{ backgroundColor: color }} />
          {lesson.modality.name}
        </span>
        {!lesson.class && <span className="w-fit rounded border px-1.5 text-xs font-medium">{t("agenda.adhoc")}</span>}
        {lesson.substitute && <span className="w-fit rounded bg-secondary px-1.5 text-xs font-medium text-secondary-foreground">{t("agenda.substitute")}</span>}
        {cancelled && (
          <span className="flex flex-col text-xs">
            <span className="font-medium text-destructive">{t("agenda.cancelled")}</span>
            {cancelReasonText(lesson, t) && <span className="text-muted-foreground">{cancelReasonText(lesson, t)}</span>}
          </span>
        )}
      </Link>
    </li>
  );
}
