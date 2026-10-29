import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { createClass, updateClass, useClassOptions, type ClassDetail, type ClassInput, type ClassOptions } from "./useClasses";
import { Select, Input } from "@/components/ui/field";
import { Banner } from "@/components/ui/banner";

type Field = "unit" | "name" | "modality" | "ageRange" | "schedule" | "capacity" | "head" | "assistants" | "link" | "form";
type Errors = Partial<Record<Field, string>>;
type Day = { on: boolean; from: string; to: string };
// Campo do servidor -> campo da tela.
const FIELD: Record<string, Field> = { unitId: "unit", name: "name", modalityId: "modality", ageRangeId: "ageRange", schedule: "schedule", capacity: "capacity", headTeacherId: "head", assistantIds: "assistants", groupLink: "link" };
const F = { unit: "unit", modality: "modality", ageRange: "ageRange", schedule: "schedule", head: "head", assistants: "assistants" } as const;
const DAYS = [1, 2, 3, 4, 5, 6, 7];
const select = "min-h-11";

// RF-TUR-03: criação (sem `klass`) e edição da turma. O servidor reconfere tudo (alcance, unicidade do nome, titular ativo).
export function ClassFormDialog({ open, klass, onClose, onSaved }: { open: boolean; klass?: ClassDetail; onClose: () => void; onSaved: (saved: ClassDetail) => void }) {
  const { t } = useTranslation();
  const loaded = useClassOptions(open);
  if (!open) return null;
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t(klass ? "classes.form.editTitle" : "classes.form.newTitle")}>
      {loaded.status === "loading" && <LoadingState />}
      {loaded.status === "forbidden" && <ForbiddenState />}
      {loaded.status === "error" && <ErrorState onRetry={loaded.retry} />}
      {loaded.options && <Form key={klass?.id ?? "new"} options={loaded.options} klass={klass} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

const initialDays = (klass?: ClassDetail): Record<number, Day> =>
  Object.fromEntries(DAYS.map((d) => {
    const s = klass?.schedule.find((x) => x.weekday === d);
    return [d, { on: !!s, from: s?.startsAt ?? "19:00", to: s?.endsAt ?? "20:00" }];
  }));

function Form({ options, klass, onClose, onSaved }: { options: ClassOptions; klass?: ClassDetail; onClose: () => void; onSaved: (saved: ClassDetail) => void }) {
  const { t } = useTranslation();
  const [unitId, setUnitId] = useState(klass?.unit.id ?? (options.units.length === 1 ? options.units[0].id : ""));
  const unit = options.units.find((u) => u.id === unitId);
  const [name, setName] = useState(klass?.name ?? "");
  const [ageRangeId, setAgeRangeId] = useState(klass?.ageRange?.id ?? "");
  const [days, setDays] = useState(() => initialDays(klass));
  const [capacity, setCapacity] = useState(klass?.capacity?.toString() ?? "");
  // Titular inativo (ou que saiu da unidade) não é escolha: o campo volta vazio e pede um novo (RN-PRO-05).
  const [modalityId, setModalityId] = useState(klass?.modality.id ?? "");
  const unitTeachers = unit?.teachers ?? [];
  const teachers = unitTeachers.filter((x) => x.modalityIds.includes(modalityId));
  const headGone = !!klass && !teachers.some((x) => x.id === klass.head.id);
  const headCannotTeach = headGone && unitTeachers.some((x) => x.id === klass?.head.id);
  const [headId, setHeadId] = useState(headGone ? "" : (klass?.head.id ?? ""));
  const [assistants, setAssistants] = useState(() => (klass?.assistants ?? []).map((a) => a.id).filter((id) => teachers.some((x) => x.id === id)));
  const [link, setLink] = useState(klass?.groupLink ?? "");
  const [notify, setNotify] = useState(true);
  const [errors, setErrors] = useState<Errors>(headGone ? { head: t(headCannotTeach ? "classes.form.headCannotTeach" : "classes.form.headGone", { name: klass.head.name }) } : {});
  const [busy, setBusy] = useState(false);

  const schedule = DAYS.filter((d) => days[d].on).map((d) => ({ weekday: d, startsAt: days[d].from, endsAt: days[d].to }));
  const changed = !!klass && JSON.stringify(schedule) !== JSON.stringify(klass.schedule);

  function pickUnit(id: string) {
    setUnitId(id);
    const next = options.units.find((u) => u.id === id);
    if (!next?.modalities.some((m) => m.id === modalityId)) setModalityId("");
    const modalityKept = !!next?.modalities.some((m) => m.id === modalityId);
    const can = (tid: string) => modalityKept && !!next?.teachers.some((x) => x.id === tid && x.modalityIds.includes(modalityId));
    if (!can(headId)) setHeadId("");
    setAssistants((a) => a.filter(can));
  }

  // RN-TUR-05: ao trocar a modalidade, titular e auxiliares que não a ensinam saem da escolha.
  function pickModality(id: string) {
    setModalityId(id);
    const teaches = (tid: string) => unitTeachers.some((x) => x.id === tid && x.modalityIds.includes(id));
    if (!teaches(headId)) setHeadId("");
    setAssistants((a) => a.filter(teaches));
  }

  function setDay(d: number, patch: Partial<Day>) {
    setDays((all) => ({ ...all, [d]: { ...all[d], ...patch } }));
  }

  function validate(): Errors {
    const found: Errors = {};
    const n = name.trim();
    if (!klass && !unitId) found.unit = t("classes.form.unitRequired");
    if (n.length < 2 || n.length > 80) found.name = t("classes.form.nameInvalid");
    if (!modalityId) found.modality = t("classes.form.modalityRequired");
    if (!headId) found.head = t("classes.form.headRequired");
    if (schedule.length === 0) found.schedule = t("classes.form.scheduleRequired");
    else if (schedule.some((s) => !s.startsAt || !s.endsAt || s.endsAt <= s.startsAt)) found.schedule = t("classes.form.scheduleOrder");
    const cap = capacity.trim();
    if (cap && !(/^\d+$/.test(cap) && Number(cap) >= 1 && Number(cap) <= 200)) found.capacity = t("classes.form.capacityInvalid");
    if (link.trim() && !/^https?:\/\/\S+$/.test(link.trim())) found.link = t("classes.form.linkInvalid");
    return found;
  }

  async function submit() {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length) return;
    const input: ClassInput = {
      name: name.trim(),
      modalityId,
      ageRangeId: ageRangeId || null,
      schedule,
      capacity: capacity.trim() ? Number(capacity) : null,
      headTeacherId: headId,
      assistantIds: assistants.filter((a) => a !== headId),
      groupLink: link.trim() || null,
      ...(klass ? (changed ? { notifyClass: notify } : {}) : { unitId }),
    };
    setBusy(true);
    try {
      const saved = klass ? await updateClass(klass.id, input) : await createClass(input);
      onClose();
      onSaved(saved);
    } catch (e) {
      const next: Errors = {};
      if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[FIELD[f.field] ?? "form"] = f.message;
      else next.form = t(e instanceof ApiError && e.status === 409 ? "classes.form.conflict" : "classes.form.error");
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  const err = (field: Field) => errors[field] && (
    <p id={`class-${field}-erro`} className="text-sm text-danger-foreground">
      {errors[field]}
    </p>
  );
  const props = (field: Field) => ({ id: `class-${field}`, "aria-invalid": errors[field] ? (true as const) : undefined, "aria-describedby": errors[field] ? `class-${field}-erro` : undefined });

  return (
    <form
      noValidate
      className="flex flex-col gap-3"
      aria-busy={busy}
      onSubmit={(e) => {
        e.preventDefault();
        if (!busy) void submit();
      }}
    >
      <Text label={t("classes.form.name")} value={name} onChange={setName} error={errors.name} autoComplete="off" />
      <div>
        {klass ? (
          <>
            <span className="block text-sm font-medium">{t("classes.form.unit")}</span>
            <p className="min-h-11 py-2">{klass.unit.name}</p>
          </>
        ) : (
          <>
            <label htmlFor="class-unit" className="block text-sm font-medium">
              {t("classes.form.unit")}
            </label>
            <Select {...props(F.unit)} className={select} value={unitId} onChange={(e) => pickUnit(e.target.value)}>
            <option value="">{t("classes.form.choose")}</option>
            {options.units.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
            </Select>
          </>
        )}
        {err(F.unit)}
      </div>
      <div>
        <label htmlFor="class-modality" className="block text-sm font-medium">
          {t("classes.form.modality")}
        </label>
        <Select {...props(F.modality)} className={select} value={modalityId} onChange={(e) => pickModality(e.target.value)}>
          <option value="">{t("classes.form.choose")}</option>
          {(unit?.modalities ?? []).map((m) => (
            <option key={m.id} value={m.id}>
              {m.name}
            </option>
          ))}
        </Select>
        {err(F.modality)}
      </div>
      <div>
        <label htmlFor="class-ageRange" className="block text-sm font-medium">
          {t("classes.form.ageRange")}
        </label>
        <Select {...props(F.ageRange)} className={select} value={ageRangeId} onChange={(e) => setAgeRangeId(e.target.value)}>
          <option value="">{t("classes.form.noAgeRange")}</option>
          {options.ageRanges.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </Select>
        {err(F.ageRange)}
      </div>
      <fieldset aria-describedby={errors.schedule ? "class-schedule-erro" : undefined}>
        <legend className="text-sm font-medium">{t("classes.form.schedule")}</legend>
        {DAYS.map((d) => {
          const day = t(`classes.weekdaysLong.${d}`);
          return (
            <div key={d} className="flex flex-wrap items-center gap-2">
              <label className="flex min-h-11 min-w-40 items-center gap-2">
                <input type="checkbox" checked={days[d].on} onChange={(e) => setDay(d, { on: e.target.checked })} />
                {day}
              </label>
              {days[d].on && (
                <>
                  <label className="flex items-center gap-1 text-sm">
                    {t("classes.form.from", { day })}
                    <Input type="time" className="min-h-11" value={days[d].from} onChange={(e) => setDay(d, { from: e.target.value })} />
                  </label>
                  <label className="flex items-center gap-1 text-sm">
                    {t("classes.form.to", { day })}
                    <Input type="time" className="min-h-11" value={days[d].to} onChange={(e) => setDay(d, { to: e.target.value })} />
                  </label>
                </>
              )}
            </div>
          );
        })}
        {err(F.schedule)}
      </fieldset>
      {changed && (
        <label className="flex min-h-11 items-center gap-2">
          <input type="checkbox" checked={notify} onChange={(e) => setNotify(e.target.checked)} />
          {t("classes.form.notify")}
        </label>
      )}
      <Text label={t("classes.form.capacity")} type="number" inputMode="numeric" value={capacity} onChange={setCapacity} error={errors.capacity} help={t("classes.form.capacityHelp")} />
      <div>
        <label htmlFor="class-head" className="block text-sm font-medium">
          {t("classes.form.head")}
        </label>
        <Select {...props(F.head)} className={select} value={headId} onChange={(e) => setHeadId(e.target.value)}>
          <option value="">{t("classes.form.choose")}</option>
          {teachers.map((x) => (
            <option key={x.id} value={x.id}>
              {x.name}
            </option>
          ))}
        </Select>
        {err(F.head)}
        <p className="text-sm text-muted-foreground">{t("classes.form.teachersHelp")}</p>
      </div>
      <fieldset aria-describedby={errors.assistants ? "class-assistants-erro" : undefined}>
        <legend className="text-sm font-medium">{t("classes.form.assistants")}</legend>
        {teachers.filter((x) => x.id !== headId).map((x) => (
          <label key={x.id} className="flex min-h-11 items-center gap-2">
            <input
              type="checkbox"
              checked={assistants.includes(x.id)}
              onChange={(e) => setAssistants((a) => (e.target.checked ? [...a, x.id] : a.filter((y) => y !== x.id)))}
            />
            {x.name}
          </label>
        ))}
        {err(F.assistants)}
      </fieldset>
      <Text label={t("classes.form.link")} value={link} onChange={setLink} error={errors.link} autoComplete="off" inputMode="url" />
      {errors.form && (
        <Banner tone="danger">
          {errors.form}
        </Banner>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" className="min-h-11" disabled={busy}>
          {t("classes.form.save")}
        </Button>
      </div>
    </form>
  );
}
