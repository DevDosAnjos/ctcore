import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { centsToReais, createPlan, reaisToCents, updatePlan, usePlanImpact, usePlanOptions, type PlanInput, type PlanOptions, type PlanRow } from "./usePlans";

type Field = "unit" | "modality" | "name" | "value" | "frequency" | "form";
type Errors = Partial<Record<Field, string>>;
// Campo do servidor -> campo da tela.
const FIELD: Record<string, Field> = { unitId: "unit", modalityId: "modality", name: "name", valueCents: "value", weeklyFrequency: "frequency" };
const F = { unit: "unit", modality: "modality", frequency: "frequency" } as const;
const select = "min-h-11 w-full rounded-md border bg-background px-2 disabled:opacity-60";

// RF-PLA-03: criação (sem `plan`) e edição do plano. O servidor reconfere tudo (alcance, unicidade do nome, regra de outras unidades).
export function PlanFormDialog({ open, plan, onClose, onSaved }: { open: boolean; plan?: PlanRow; onClose: () => void; onSaved: (saved: PlanRow) => void }) {
  const { t } = useTranslation();
  const loaded = usePlanOptions(open);
  if (!open) return null;
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t(plan ? "plans.form.editTitle" : "plans.form.newTitle")}>
      {loaded.status === "loading" && <LoadingState />}
      {loaded.status === "forbidden" && <ForbiddenState />}
      {loaded.status === "error" && <ErrorState onRetry={loaded.retry} />}
      {loaded.options && <Form key={plan?.id ?? "new"} options={loaded.options} plan={plan} onClose={onClose} onSaved={onSaved} />}
    </Modal>
  );
}

function Form({ options, plan, onClose, onSaved }: { options: PlanOptions; plan?: PlanRow; onClose: () => void; onSaved: (saved: PlanRow) => void }) {
  const { t } = useTranslation();
  const [unitId, setUnitId] = useState(plan?.unit.id ?? (options.units.length === 1 ? options.units[0].id : ""));
  const unit = options.units.find((u) => u.id === unitId);
  const [modalityId, setModalityId] = useState(plan?.modality.id ?? "");
  const [name, setName] = useState(plan?.name ?? "");
  const [value, setValue] = useState(plan ? centsToReais(plan.valueCents) : "");
  const [frequency, setFrequency] = useState(plan?.weeklyFrequency?.toString() ?? "");
  const [other, setOther] = useState(plan?.validOtherUnits ?? false);
  const [unpaid, setUnpaid] = useState<"KEEP" | "ADJUST">("KEEP");
  const [paidDifference, setPaidDifference] = useState(false);
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);

  const cents = reaisToCents(value);
  const valueChanged = !!plan && cents !== null && cents !== plan.valueCents;
  const impact = usePlanImpact(plan?.id, valueChanged ? cents : null);

  function validate(): Errors {
    const found: Errors = {};
    const n = name.trim();
    if (!plan && !unitId) found.unit = t("plans.form.unitRequired");
    if (!modalityId) found.modality = t("plans.form.modalityRequired");
    if (n.length < 2 || n.length > 60) found.name = t("plans.form.nameInvalid");
    if (cents === null) found.value = t("plans.form.valueInvalid");
    return found;
  }

  async function submit() {
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length || cents === null) return;
    const input: PlanInput = {
      name: name.trim(),
      valueCents: cents,
      weeklyFrequency: frequency ? (Number(frequency) as 2 | 3) : null,
      validOtherUnits: options.crossUnitTraining === "plan" && other,
      ...(plan ? (valueChanged ? { unpaidCharges: unpaid, paidFutureDifference: paidDifference } : {}) : { unitId, modalityId }),
    };
    setBusy(true);
    try {
      const saved = plan ? await updatePlan(plan.id, input) : await createPlan(input);
      onClose();
      onSaved(saved);
    } catch (e) {
      const next: Errors = {};
      if (e instanceof ApiError && e.fields.length) for (const f of e.fields) next[FIELD[f.field] ?? "form"] = f.message;
      else next.form = t(e instanceof ApiError && e.status === 409 ? "plans.form.conflict" : "plans.form.error");
      setErrors(next);
    } finally {
      setBusy(false);
    }
  }

  const err = (field: Field) => errors[field] && (
    <p id={`plan-${field}-erro`} className="text-sm text-destructive">
      {errors[field]}
    </p>
  );
  const props = (field: Field) => ({ id: `plan-${field}`, "aria-invalid": errors[field] ? (true as const) : undefined, "aria-describedby": errors[field] ? `plan-${field}-erro` : undefined });

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
      <div>
        {plan ? (
          <>
            <span className="block text-sm font-medium">{t("plans.form.unit")}</span>
            <p className="min-h-11 py-2">{plan.unit.name}</p>
          </>
        ) : (
          <>
            <label htmlFor="plan-unit" className="block text-sm font-medium">
              {t("plans.form.unit")}
            </label>
            <select
              {...props(F.unit)}
              className={select}
              value={unitId}
              onChange={(e) => {
                setUnitId(e.target.value);
                setModalityId("");
              }}
            >
              <option value="">{t("plans.form.choose")}</option>
              {options.units.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name}
                </option>
              ))}
            </select>
          </>
        )}
        {err(F.unit)}
      </div>
      <div>
        {plan ? (
          <>
            <span className="block text-sm font-medium">{t("plans.form.modality")}</span>
            <p className="min-h-11 py-2">{plan.modality.name}</p>
          </>
        ) : (
          <>
            <label htmlFor="plan-modality" className="block text-sm font-medium">
              {t("plans.form.modality")}
            </label>
            <select {...props(F.modality)} className={select} value={modalityId} onChange={(e) => setModalityId(e.target.value)}>
              <option value="">{t("plans.form.choose")}</option>
              {(unit?.modalities ?? []).map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
            {unit && unit.modalities.length === 0 && <p className="text-sm text-muted-foreground">{t("plans.form.noModalities")}</p>}
          </>
        )}
        {err(F.modality)}
      </div>
      <Text label={t("plans.form.name")} value={name} onChange={setName} error={errors.name} autoComplete="off" />
      <Text label={t("plans.form.value")} value={value} onChange={setValue} error={errors.value} help={t("plans.form.valueHelp")} inputMode="decimal" autoComplete="off" />
      <div>
        <label htmlFor="plan-frequency" className="block text-sm font-medium">
          {t("plans.form.frequency")}
        </label>
        <select {...props(F.frequency)} className={select} value={frequency} onChange={(e) => setFrequency(e.target.value)}>
          <option value="2">{t("plans.form.frequencyOptions.2")}</option>
          <option value="3">{t("plans.form.frequencyOptions.3")}</option>
          <option value="">{t("plans.form.frequencyOptions.free")}</option>
        </select>
        {err(F.frequency)}
        <p className="text-sm text-muted-foreground">{t("plans.form.frequencyHelp")}</p>
      </div>
      {options.crossUnitTraining === "plan" ? (
        <label className="flex min-h-11 items-center gap-2">
          <input type="checkbox" checked={other} onChange={(e) => setOther(e.target.checked)} />
          {t("plans.form.validOtherUnits")}
        </label>
      ) : (
        <p className="text-sm text-muted-foreground">{t(`plans.form.crossUnit.${options.crossUnitTraining}`)}</p>
      )}
      {valueChanged && (
        <section aria-labelledby="plan-impact-title" className="flex flex-col gap-2 rounded-md border p-3">
          <h3 id="plan-impact-title" className="font-medium">
            {t("plans.form.impact.title")}
          </h3>
          {impact.status === "loading" && <p role="status">{t("plans.form.impact.loading")}</p>}
          {impact.status === "error" && <p role="alert" className="text-sm text-destructive">{t("plans.form.impact.error")}</p>}
          {impact.impact && <p>{t("plans.form.impact.students", { count: impact.impact.studentsAffected })}</p>}
          <p className="text-sm">{t("plans.form.impact.from")}</p>
          {(impact.impact?.unpaidCharges ?? 0) > 0 && (
            <fieldset>
              <legend className="text-sm font-medium">{t("plans.form.impact.unpaid")}</legend>
              {(["KEEP", "ADJUST"] as const).map((v) => (
                <label key={v} className="flex min-h-11 items-center gap-2">
                  <input type="radio" name="plan-unpaid" checked={unpaid === v} onChange={() => setUnpaid(v)} />
                  {t(v === "KEEP" ? "plans.form.impact.unpaidKeep" : "plans.form.impact.unpaidAdjust")}
                </label>
              ))}
            </fieldset>
          )}
          {(impact.impact?.paidFutureCharges ?? 0) > 0 && (
            <fieldset>
              <legend className="text-sm font-medium">{t("plans.form.impact.paid")}</legend>
              {([false, true] as const).map((v) => (
                <label key={String(v)} className="flex min-h-11 items-center gap-2">
                  <input type="radio" name="plan-paid" checked={paidDifference === v} onChange={() => setPaidDifference(v)} />
                  {t(v ? "plans.form.impact.paidCharge" : "plans.form.impact.paidNone")}
                </label>
              ))}
            </fieldset>
          )}
        </section>
      )}
      {errors.form && (
        <p role="alert" className="text-sm text-destructive">
          {errors.form}
        </p>
      )}
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" className="min-h-11" onClick={onClose} disabled={busy}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" className="min-h-11" disabled={busy}>
          {t("plans.form.save")}
        </Button>
      </div>
    </form>
  );
}
