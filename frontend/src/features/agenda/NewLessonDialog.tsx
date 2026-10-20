import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { EmptyState, ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { todayIso } from "./agendaDates";
import { createLesson, useActiveClasses } from "./useLessonActions";

interface Choice {
  id: string;
  name: string;
}

const select = "min-h-11 w-full rounded-md border bg-background px-2 disabled:opacity-60";

// RF-AGE-04: aula avulsa (sem turma). Modalidades e professores vêm da Agenda; o servidor reconfere tudo (RN-PRO-06, RN-AGE-07, RN-AGE-12).
export function NewLessonDialog({ unit, modalities, teachers, onClose, onSaved }: { unit: Choice; modalities: Choice[]; teachers: Choice[]; onClose: () => void; onSaved: () => void }) {
  const { t } = useTranslation();
  const classes = useActiveClasses(unit.id);
  const [title, setTitle] = useState("");
  const [modalityId, setModalityId] = useState("");
  const [date, setDate] = useState(todayIso);
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [teacherId, setTeacherId] = useState("");
  const [onlyClass, setOnlyClass] = useState(false);
  const [classId, setClassId] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const empty = modalities.length === 0 || teachers.length === 0;

  async function submit() {
    const found: Record<string, string> = {};
    if (!title.trim()) found.title = t("agenda.new.titleRequired");
    if (!modalityId) found.modalityId = t("agenda.new.modalityRequired");
    if (!teacherId) found.teacherId = t("agenda.new.teacherRequired");
    if (!date) found.date = t("agenda.edit.dateRequired");
    if (!from || !to || to <= from) found.endsAt = t("agenda.edit.timeOrder");
    if (onlyClass && !classId) found.visibilityClassId = t("agenda.new.classRequired");
    setErrors(found);
    if (Object.keys(found).length) return;
    setBusy(true);
    try {
      await createLesson({ unitId: unit.id, title: title.trim(), modalityId, date, startsAt: from, endsAt: to, teacherId, ...(onlyClass ? { visibilityClassId: classId } : {}) });
      onClose();
      onSaved();
    } catch (e) {
      const next: Record<string, string> = {};
      if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[f.field] = f.message;
      else next.form = t(`agenda.new.errors.${e instanceof ApiError && [403, 404, 422].includes(e.status) ? e.status : "generic"}`);
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("agenda.new.title")}>
      {empty && <EmptyState title={t("agenda.new.emptyTitle")} text={t("agenda.new.emptyText")} />}
      {!empty && classes.status === "loading" && <LoadingState />}
      {!empty && classes.status === "error" && <ErrorState onRetry={classes.retry} />}
      {!empty && classes.status === "ready" && (
        <form
          noValidate
          className="flex flex-col gap-3"
          aria-busy={busy}
          onSubmit={(e) => {
            e.preventDefault();
            if (!busy) void submit();
          }}
        >
          <p className="text-sm">{t("agenda.new.unit", { name: unit.name })}</p>
          <Text label={t("agenda.new.lessonTitle")} value={title} onChange={setTitle} error={errors.title} maxLength={80} autoComplete="off" />
          <Pick id="new-modality" label={t("agenda.new.modality")} value={modalityId} onChange={setModalityId} options={modalities} error={errors.modalityId} />
          <Text type="date" label={t("agenda.edit.date")} value={date} onChange={setDate} error={errors.date} />
          <div className="flex flex-wrap gap-3">
            <div className="min-w-32 flex-1">
              <Text type="time" label={t("agenda.edit.startsAt")} value={from} onChange={setFrom} error={errors.startsAt} />
            </div>
            <div className="min-w-32 flex-1">
              <Text type="time" label={t("agenda.edit.endsAt")} value={to} onChange={setTo} error={errors.endsAt} />
            </div>
          </div>
          <Pick id="new-teacher" label={t("agenda.new.teacher")} value={teacherId} onChange={setTeacherId} options={teachers} error={errors.teacherId} />
          <fieldset>
            <legend className="text-sm font-medium">{t("agenda.new.visibility")}</legend>
            <label className="flex min-h-11 items-center gap-2">
              <input type="radio" name="visibility" checked={!onlyClass} onChange={() => setOnlyClass(false)} />
              {t("agenda.new.visibilityAll")}
            </label>
            {classes.classes.length > 0 && (
              <label className="flex min-h-11 items-center gap-2">
                <input type="radio" name="visibility" checked={onlyClass} onChange={() => setOnlyClass(true)} />
                {t("agenda.new.visibilityClass")}
              </label>
            )}
            {onlyClass && <Pick id="new-class" label={t("agenda.new.class")} value={classId} onChange={setClassId} options={classes.classes} error={errors.visibilityClassId} />}
          </fieldset>
          <Err text={errors.form} />
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" className="min-h-11" disabled={busy}>
              {busy ? t("agenda.new.saving") : t("agenda.new.save")}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}

function Pick({ id, label, value, onChange, options, error }: { id: string; label: string; value: string; onChange: (v: string) => void; options: Choice[]; error?: string }) {
  const { t } = useTranslation();
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <select id={id} className={select} value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">{t("agenda.new.choose")}</option>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </select>
      <Err text={error} />
    </div>
  );
}

function Err({ text }: { text?: string }) {
  return text ? (
    <p role="alert" className="text-sm text-destructive">
      {text}
    </p>
  ) : null;
}
