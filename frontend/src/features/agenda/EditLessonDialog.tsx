import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import type { LessonDetail } from "./useLesson";
import { editLesson, useUnitTeachers } from "./useLessonActions";

const select = "min-h-11 w-full rounded-md border bg-background px-2 disabled:opacity-60";

// RF-AGE-03: editar data, horário e professor; esta aula ou esta e as seguintes. O servidor reconfere tudo (RN-AGE-04, RN-AGE-08).
export function EditLessonDialog({ lesson, onClose, onSaved }: { lesson: LessonDetail | null; onClose: () => void; onSaved: () => void }) {
  if (!lesson) return null;
  return <Body key={lesson.id} lesson={lesson} onClose={onClose} onSaved={onSaved} />;
}

function Body({ lesson, onClose, onSaved }: { lesson: LessonDetail; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const options = useUnitTeachers(lesson.unit.id, lesson.date);
  const completed = lesson.status === "COMPLETED";
  const [scope, setScope] = useState<"THIS" | "FOLLOWING">("THIS");
  const [date, setDate] = useState(lesson.date);
  const [from, setFrom] = useState(lesson.startsAt);
  const [to, setTo] = useState(lesson.endsAt);
  const [teacherId, setTeacherId] = useState(lesson.teacher.id);
  const [reason, setReason] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const teachers = options.teachers.some((x) => x.id === lesson.teacher.id) ? options.teachers : [lesson.teacher, ...options.teachers];

  async function submit() {
    const found: Record<string, string> = {};
    if (!date) found.date = t("agenda.edit.dateRequired");
    if (!from || !to || to <= from) found.endsAt = t("agenda.edit.timeOrder");
    if (completed && !reason.trim()) found.reason = t("agenda.edit.reasonRequired");
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await editLesson(lesson.id, { scope, date, startsAt: from, endsAt: to, teacherId, ...(completed ? { reason: reason.trim() } : {}) });
      onClose();
      onSaved();
    } catch (e) {
      const next: Record<string, string> = {};
      if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[f.field] = f.message;
      else next.form = t(`agenda.edit.errors.${e instanceof ApiError && [403, 404, 409, 422].includes(e.status) ? e.status : "generic"}`);
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }


  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("agenda.edit.title")}>
      {options.status === "loading" && <LoadingState />}
      {options.status === "forbidden" && <ForbiddenState />}
      {options.status === "error" && <ErrorState onRetry={options.retry} />}
      {options.status === "ready" && (
        <form
          noValidate
          className="flex flex-col gap-3"
          aria-busy={busy}
          onSubmit={(e) => {
            e.preventDefault();
            if (!busy) void submit();
          }}
        >
          <p className="text-sm">{lesson.class.name}</p>
          {completed && <p className="text-sm">{t("agenda.edit.completedNote")}</p>}
          <Text type="date" label={t("agenda.edit.date")} value={date} onChange={setDate} error={errors.date} disabled={completed} />
          <div className="flex flex-wrap gap-3">
            <div className="min-w-32 flex-1">
              <Text type="time" label={t("agenda.edit.startsAt")} value={from} onChange={setFrom} error={errors.startsAt} disabled={completed} />
            </div>
            <div className="min-w-32 flex-1">
              <Text type="time" label={t("agenda.edit.endsAt")} value={to} onChange={setTo} error={errors.endsAt} disabled={completed} />
            </div>
          </div>
          <div>
            <label htmlFor="lesson-teacher" className="block text-sm font-medium">
              {t("agenda.edit.teacher")}
            </label>
            <select id="lesson-teacher" className={select} value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
              {teachers.map((x) => (
                <option key={x.id} value={x.id}>
                  {x.name}
                </option>
              ))}
            </select>
            <Err text={errors.teacherId} />
            <p className="text-sm text-muted-foreground">{t("agenda.edit.teacherHelp")}</p>
          </div>
          {completed ? (
            <Text label={t("agenda.edit.reason")} value={reason} onChange={setReason} error={errors.reason} maxLength={200} autoComplete="off" />
          ) : (
            <fieldset>
              <legend className="text-sm font-medium">{t("agenda.edit.scope")}</legend>
              {(["THIS", "FOLLOWING"] as const).map((s) => (
                <label key={s} className="flex min-h-11 items-center gap-2">
                  <input type="radio" name="scope" checked={scope === s} onChange={() => setScope(s)} />
                  {t(`agenda.edit.scopes.${s}`)}
                </label>
              ))}
              <Err text={errors.scope} />
            </fieldset>
          )}
          <Err text={errors.form} />
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" className="min-h-11" disabled={busy}>
              {busy ? t("agenda.edit.saving") : t("agenda.edit.save")}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

function Err({ text }: { text?: string }) {
  return text ? (
    <p role="alert" className="text-sm text-destructive">
      {text}
    </p>
  ) : null;
}
