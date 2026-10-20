import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { br, todayIso } from "@/features/agenda/agendaDates";
import { useActiveClasses } from "@/features/agenda/useLessonActions";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { createDay, updateDay, usePreview, type Choice, type NoClassDayItem, type NoticeMode, type Suggestion } from "./useNoClassDays";

const MODES: NoticeMode[] = ["NONE", "NOW", "SCHEDULED"];
const FIELDS = ["unitIds", "startsOn", "endsOn", "reason", "classIds", "notice"];

// ISO do servidor (UTC) para o valor de <input type="datetime-local"> no fuso do navegador.
const localInput = (iso: string | null) => {
  if (!iso) return "";
  const d = new Date(iso);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
};

// RF-AGE-05: novo dia sem aula (ou sugestão confirmada) e edição. Só o Coordenador escolhe várias unidades; turmas só com uma unidade. O servidor reconfere tudo.
export function NoClassDayDialog({
  units,
  unit,
  multi,
  item,
  suggestion,
  onClose,
  onSaved,
}: {
  units: Choice[];
  unit: Choice;
  multi: boolean;
  item?: NoClassDayItem;
  suggestion?: Suggestion;
  onClose: () => void;
  onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [unitIds, setUnitIds] = useState<string[]>([item?.unit.id ?? unit.id]);
  const [startsOn, setStartsOn] = useState(item?.startsOn ?? suggestion?.date ?? todayIso());
  const [endsOn, setEndsOn] = useState(item?.endsOn ?? suggestion?.date ?? todayIso());
  const [reason, setReason] = useState(item?.reason ?? (suggestion ? t(`noClassDays.holidays.${suggestion.code}`) : ""));
  const [onlySome, setOnlySome] = useState(!!item && !item.allClasses);
  const [classIds, setClassIds] = useState<string[]>(item?.classes.map((c) => c.id) ?? []);
  const [mode, setMode] = useState<NoticeMode>(item?.notice.mode ?? "NONE");
  const [sendAt, setSendAt] = useState(localInput(item?.notice.sendAt ?? null));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const single = unitIds.length === 1;
  const classes = useActiveClasses(unitIds[0] ?? unit.id);
  const chosenClasses = single && onlySome ? classIds : [];
  const preview = usePreview(unitIds, startsOn, endsOn, chosenClasses);

  function toggleUnit(id: string, on: boolean) {
    const next = on ? [...unitIds, id] : unitIds.filter((u) => u !== id);
    setUnitIds(next);
    if (next.length !== 1) {
      setOnlySome(false);
      setClassIds([]);
    }
  }

  async function submit() {
    const found: Record<string, string> = {};
    if (unitIds.length === 0) found.unitIds = t("noClassDays.dialog.unitsRequired");
    if (!startsOn) found.startsOn = t("noClassDays.dialog.dateRequired");
    if (!endsOn || endsOn < startsOn) found.endsOn = t("noClassDays.dialog.endRequired");
    if (!reason.trim() || reason.trim().length > 120) found.reason = t("noClassDays.dialog.reasonRequired");
    if (single && onlySome && classIds.length === 0) found.classIds = t("noClassDays.dialog.classesRequired");
    if (mode === "SCHEDULED" && !sendAt) found.notice = t("noClassDays.dialog.sendAtRequired");
    setErrors(found);
    if (Object.keys(found).length) return;
    const body = {
      unitIds,
      startsOn,
      endsOn,
      reason: reason.trim(),
      ...(suggestion || item?.origin === "HOLIDAY" ? { holiday: true } : {}),
      classIds: chosenClasses,
      notice: { mode, ...(mode === "SCHEDULED" ? { sendAt: new Date(sendAt).toISOString() } : {}) },
    };
    setBusy(true);
    try {
      if (item) await updateDay(item.id, body);
      else await createDay(body);
      onClose();
      onSaved();
    } catch (e) {
      const next: Record<string, string> = {};
      if (e instanceof ApiError) for (const f of e.fields) next[f.field] = f.message;
      if (!Object.keys(next).some((k) => FIELDS.includes(k))) next.form = t(`noClassDays.dialog.errors.${e instanceof ApiError && [400, 403, 404, 409, 422].includes(e.status) ? e.status : "generic"}`);
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t(item ? "noClassDays.dialog.titleEdit" : "noClassDays.dialog.titleNew")}>
      <form
        noValidate
        className="flex flex-col gap-3"
        aria-busy={busy}
        onSubmit={(e) => {
          e.preventDefault();
          if (!busy) void submit();
        }}
      >
        {multi && !item && units.length > 1 ? (
          <fieldset>
            <legend className="text-sm font-medium">{t("noClassDays.dialog.units")}</legend>
            <p className="text-sm text-muted-foreground">{t("noClassDays.dialog.unitsHelp")}</p>
            {units.map((u) => (
              <label key={u.id} className="flex min-h-11 items-center gap-2">
                <input type="checkbox" checked={unitIds.includes(u.id)} onChange={(e) => toggleUnit(u.id, e.target.checked)} />
                {u.name}
              </label>
            ))}
            <Err text={errors.unitIds} />
          </fieldset>
        ) : (
          <p className="text-sm">{t("noClassDays.dialog.unit", { name: item?.unit.name ?? unit.name })}</p>
        )}
        <div className="flex flex-wrap gap-3">
          <div className="min-w-40 flex-1">
            <Text type="date" label={t("noClassDays.dialog.startsOn")} value={startsOn} onChange={setStartsOn} error={errors.startsOn} />
          </div>
          <div className="min-w-40 flex-1">
            <Text type="date" label={t("noClassDays.dialog.endsOn")} value={endsOn} onChange={setEndsOn} error={errors.endsOn} />
          </div>
        </div>
        <Text label={t("noClassDays.dialog.reason")} value={reason} onChange={setReason} error={errors.reason} maxLength={120} autoComplete="off" />
        <fieldset>
          <legend className="text-sm font-medium">{t("noClassDays.dialog.classes")}</legend>
          {single ? (
            <>
              <label className="flex min-h-11 items-center gap-2">
                <input type="radio" name="classes" checked={!onlySome} onChange={() => setOnlySome(false)} />
                {t("noClassDays.dialog.classesAll")}
              </label>
              {classes.classes.length > 0 && (
                <label className="flex min-h-11 items-center gap-2">
                  <input type="radio" name="classes" checked={onlySome} onChange={() => setOnlySome(true)} />
                  {t("noClassDays.dialog.classesSome")}
                </label>
              )}
              {onlySome &&
                classes.classes.map((c) => (
                  <label key={c.id} className="ml-6 flex min-h-11 items-center gap-2">
                    <input
                      type="checkbox"
                      checked={classIds.includes(c.id)}
                      onChange={(e) => setClassIds(e.target.checked ? [...classIds, c.id] : classIds.filter((x) => x !== c.id))}
                    />
                    {c.name}
                  </label>
                ))}
            </>
          ) : (
            <p className="text-sm text-muted-foreground">{t("noClassDays.dialog.classesOneUnit")}</p>
          )}
          <Err text={errors.classIds} />
        </fieldset>
        <section aria-live="polite" className="rounded-md border p-2">
          <h3 className="text-sm font-medium">{t("noClassDays.dialog.preview")}</h3>
          {preview.status === "loading" && <p className="text-sm text-muted-foreground">{t("noClassDays.dialog.previewLoading")}</p>}
          {preview.status === "error" && (
            <p role="alert" className="text-sm text-destructive">
              {t("noClassDays.dialog.previewError")}{" "}
              <button type="button" className="min-h-11 underline" onClick={preview.retry}>
                {t("noClassDays.dialog.previewRetry")}
              </button>
            </p>
          )}
          {preview.status === "ready" && preview.data && preview.data.units.every((u) => u.lessons.length === 0) && (
            <p className="text-sm text-muted-foreground">{t("noClassDays.dialog.previewEmpty")}</p>
          )}
          {preview.status === "ready" &&
            preview.data?.units
              .filter((u) => u.lessons.length > 0)
              .map((u) => (
                <div key={u.unit.id}>
                  <p className="text-sm font-medium">{t("noClassDays.dialog.previewUnit", { name: u.unit.name, count: u.lessons.length })}</p>
                  <ul className="max-h-40 overflow-y-auto text-sm">
                    {u.lessons.map((l) => (
                      <li key={l.id}>{t("noClassDays.dialog.previewLine", { date: br(l.date), from: l.startsAt, to: l.endsAt, name: l.class?.name ?? l.title })}</li>
                    ))}
                  </ul>
                </div>
              ))}
        </section>
        <fieldset>
          <legend className="text-sm font-medium">{t("noClassDays.dialog.noticeLegend")}</legend>
          {MODES.map((m) => (
            <label key={m} className="flex min-h-11 items-center gap-2">
              <input type="radio" name="notice" checked={mode === m} onChange={() => setMode(m)} />
              {t(`noClassDays.dialog.notices.${m}`)}
            </label>
          ))}
          {mode === "SCHEDULED" && <Text type="datetime-local" label={t("noClassDays.dialog.sendAt")} value={sendAt} onChange={setSendAt} error={errors.notice} />}
          {mode !== "SCHEDULED" && <Err text={errors.notice} />}
          <p className="text-sm text-muted-foreground">{t("noClassDays.dialog.noticeHelp")}</p>
        </fieldset>
        <p className="text-sm font-medium">{t("noClassDays.dialog.fee")}</p>
        <Err text={errors.form} />
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {busy ? t("noClassDays.dialog.saving") : t("noClassDays.dialog.save")}
          </Button>
        </div>
      </form>
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
