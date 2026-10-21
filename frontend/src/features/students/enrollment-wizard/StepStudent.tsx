import { useState } from "react";
import { useTranslation } from "react-i18next";
import { CpfField, PhoneField } from "@/components/fields/fields";
import { isValidCpf, isValidPhone, onlyDigits } from "@/components/fields/masks";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Text } from "@/features/units/Text";
import { ApiError } from "@/lib/api";
import { PickSelect } from "../ModalityPickers";
import { useResource } from "../useStudentRecord";
import type { StudentInput, StudentRef } from "../types";
import { createStudent, lookupByCpf } from "./api";
import type { WizardStudent } from "./types";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const STATES = ["AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"];

const isAdult = (birth: string) => {
  const limit = new Date(`${birth}T00:00:00`);
  limit.setFullYear(limit.getFullYear() + 18);
  return limit.getTime() <= Date.now();
};

function Avatar({ name, photoUrl }: { name: string; photoUrl: string | null }) {
  return photoUrl ? (
    <img src={photoUrl} alt="" className="size-12 shrink-0 rounded-full object-cover" />
  ) : (
    <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-muted font-semibold">
      {name.slice(0, 1).toUpperCase()}
    </span>
  );
}

type Phase = { kind: "search" } | { kind: "found"; student: WizardStudent } | { kind: "new" };

// Etapa 1 (RF-MAT-01, RN-ALU-06, RN-MAT-19): busca por CPF; o aluno existente aparece só com nome e foto; o novo é cadastrado pelo POST /students (#54).
export function StepStudent({ onConfirm }: { onConfirm: (s: WizardStudent) => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.student.";
  const [cpf, setCpf] = useState("");
  const [phase, setPhase] = useState<Phase>({ kind: "search" });
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<"error" | "forbidden" | null>(null);

  async function search() {
    if (busy || !isValidCpf(cpf)) return;
    setBusy(true);
    setFailure(null);
    try {
      const found = await lookupByCpf(cpf);
      setPhase({ kind: "found", student: { id: found.id, name: found.name, photoUrl: found.photoUrl, enrollmentStatus: found.enrollmentStatus } });
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) setPhase({ kind: "new" });
      else setFailure(e instanceof ApiError && e.status === 403 ? "forbidden" : "error");
    } finally {
      setBusy(false);
    }
  }

  if (failure === "forbidden") return <ForbiddenState />;
  if (failure === "error") return <ErrorState onRetry={() => void search()} />;
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">{t(`${k}intro`)}</p>
      <div className="flex flex-wrap items-end gap-2">
        <div className="min-w-0 flex-1 basis-48">
          <CpfField
            label={t(`${k}cpf`)}
            onChange={(v) => {
              setCpf(v);
              setPhase({ kind: "search" });
            }}
          />
        </div>
        <Button type="button" className="min-h-11" disabled={busy || !isValidCpf(cpf)} onClick={() => void search()}>
          {t(`${k}search`)}
        </Button>
      </div>
      {busy && <LoadingState />}
      {phase.kind === "found" && (
        <section aria-label={t(`${k}foundTitle`)} className="flex flex-col gap-2 rounded-md border p-3">
          <div className="flex items-center gap-3">
            <Avatar name={phase.student.name} photoUrl={phase.student.photoUrl} />
            <div className="min-w-0">
              <p className="break-words font-medium">{phase.student.name}</p>
              <p className="text-sm text-muted-foreground">{t(`${k}status.${phase.student.enrollmentStatus}`)}</p>
            </div>
          </div>
          <Button type="button" className="min-h-11 self-start" onClick={() => onConfirm(phase.student)}>
            {t(`${k}confirm`)}
          </Button>
        </section>
      )}
      {phase.kind === "new" && (
        <NewStudentForm
          cpf={cpf}
          onCreated={(s) => onConfirm({ id: s.id, name: s.name, photoUrl: s.photoUrl, enrollmentStatus: "NONE" })}
          onExisting={(ref) => setPhase({ kind: "found", student: { ...ref, enrollmentStatus: "NONE" } })}
        />
      )}
    </div>
  );
}

function NewStudentForm({ cpf, onCreated, onExisting }: { cpf: string; onCreated: (s: { id: string; name: string; photoUrl: string | null }) => void; onExisting: (ref: StudentRef) => void }) {
  const { t } = useTranslation();
  const k = "enrollmentWizard.student.form.";
  const units = useResource<{ units: { id: string; name: string }[] }>("/units/reachable");
  const [f, setF] = useState({ name: "", birthDate: "", email: "", phone: "", unitId: "", postalCode: "", street: "", number: "", district: "", city: "", state: "" });
  const [g, setG] = useState({ name: "", cpf: "", email: "", phone: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [possible, setPossible] = useState<StudentRef>();
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string>();
  const set = (key: keyof typeof f) => (v: string) => setF((p) => ({ ...p, [key]: v }));
  const setGuardian = (key: keyof typeof g) => (v: string) => setG((p) => ({ ...p, [key]: v }));
  const minor = Boolean(f.birthDate) && !isAdult(f.birthDate);
  const required = t(`${k}required`);

  function validate() {
    const e: Record<string, string> = {};
    if (f.name.trim().length < 2) e.name = required;
    if (!f.birthDate) e.birthDate = required;
    if (!f.unitId) e.unitId = required;
    if (f.birthDate && !minor) {
      if (!f.email.trim() || !EMAIL.test(f.email.trim())) e.email = t(`${k}emailInvalid`);
      if (!isValidPhone(f.phone)) e.phone = t(`${k}phoneInvalid`);
    }
    if (onlyDigits(f.postalCode).length !== 8) e.postalCode = t(`${k}postalCodeInvalid`);
    for (const key of ["street", "number", "district", "city"] as const) if (!f[key].trim()) e[key] = required;
    if (!STATES.includes(f.state)) e.state = required;
    if (minor) {
      if (g.name.trim().length < 2) e.guardianName = t(`${k}guardianRequired`);
      if (!isValidCpf(g.cpf)) e.guardianCpf = t(`${k}guardianRequired`);
      if (!EMAIL.test(g.email.trim())) e.guardianEmail = t(`${k}emailInvalid`);
      if (!isValidPhone(g.phone)) e.guardianPhone = t(`${k}phoneInvalid`);
    }
    return e;
  }

  async function submit(confirmDuplicate: boolean) {
    if (busy) return;
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    setFormError(undefined);
    setPossible(undefined);
    const body: StudentInput = {
      registeredUnitId: f.unitId,
      name: f.name.trim(),
      birthDate: f.birthDate,
      postalCode: onlyDigits(f.postalCode),
      street: f.street.trim(),
      number: f.number.trim(),
      district: f.district.trim(),
      city: f.city.trim(),
      state: f.state,
      cpf: minor ? null : cpf,
      email: f.email.trim() || null,
      phone: f.phone || null,
      confirmDuplicate,
      ...(minor ? { guardians: [{ name: g.name.trim(), cpf: g.cpf, email: g.email.trim(), phone: g.phone, financial: true }] } : {}),
    };
    try {
      onCreated(await createStudent(body));
    } catch (e) {
      const err = e instanceof ApiError ? e : null;
      const field = (name: string) => err?.fields.find((x) => x.field === name)?.message ?? "";
      const ref = (): StudentRef => ({ id: field("id"), name: field("name"), photoUrl: field("photoUrl") || null });
      if (err?.code === "STUDENT_CPF_EXISTS") onExisting(ref());
      else if (err?.code === "STUDENT_POSSIBLE_DUPLICATE") setPossible(ref());
      else if (err && err.fields.length > 0) setErrors(Object.fromEntries(err.fields.map((x) => [x.field.split(".").pop()!, x.message])));
      else setFormError(t(`${k}error`));
    } finally {
      setBusy(false);
    }
  }

  if (units.status === "loading" || units.status === "idle") return <LoadingState />;
  if (units.status === "forbidden") return <ForbiddenState />;
  if (units.status === "error" || units.status === "notFound") return <ErrorState onRetry={units.reload} />;
  return (
    <form
      noValidate
      className="flex flex-col gap-3 rounded-md border p-3"
      onSubmit={(e) => {
        e.preventDefault();
        void submit(false);
      }}
    >
      <h2 className="text-lg font-semibold">{t(`${k}title`)}</h2>
      <p className="text-sm text-muted-foreground">{t(`${k}intro`)}</p>
      {formError && (
        <p role="alert" className="text-sm text-destructive">
          {formError}
        </p>
      )}
      {possible && (
        <div role="alert" className="flex flex-col gap-2 rounded-md border p-3 text-sm">
          <p>{t(`${k}possibleDuplicate`, { name: possible.name })}</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={() => onExisting(possible)}>
              {t(`${k}useExisting`)}
            </Button>
            <Button type="button" className="min-h-11" onClick={() => void submit(true)}>
              {t(`${k}createAnyway`)}
            </Button>
          </div>
        </div>
      )}
      <PickSelect label={t(`${k}unit`)} value={f.unitId} onChange={set("unitId")} options={units.data?.units ?? []} error={errors.unitId} />
      <Text label={t(`${k}name`)} value={f.name} onChange={set("name")} error={errors.name} autoComplete="off" />
      <Text label={t(`${k}birthDate`)} type="date" value={f.birthDate} onChange={set("birthDate")} error={errors.birthDate} />
      <Text label={t(`${k}email`)} type="email" value={f.email} onChange={set("email")} error={errors.email} />
      <PhoneField label={t(`${k}phone`)} onChange={set("phone")} />
      {errors.phone && <p className="text-sm text-destructive">{errors.phone}</p>}
      <Text label={t(`${k}postalCode`)} value={f.postalCode} onChange={set("postalCode")} error={errors.postalCode} inputMode="numeric" />
      <Text label={t(`${k}street`)} value={f.street} onChange={set("street")} error={errors.street} />
      <Text label={t(`${k}number`)} value={f.number} onChange={set("number")} error={errors.number} />
      <Text label={t(`${k}district`)} value={f.district} onChange={set("district")} error={errors.district} />
      <Text label={t(`${k}city`)} value={f.city} onChange={set("city")} error={errors.city} />
      <PickSelect label={t(`${k}state`)} value={f.state} onChange={set("state")} options={STATES.map((s) => ({ id: s, name: s }))} error={errors.state} />
      {minor && (
        <fieldset className="flex flex-col gap-3 rounded-md border p-3">
          <legend className="px-1 text-sm font-medium">{t(`${k}guardian`)}</legend>
          <p className="text-sm text-muted-foreground">{t(`${k}guardianHelp`)}</p>
          <Text label={t(`${k}guardianName`)} value={g.name} onChange={setGuardian("name")} error={errors.guardianName} />
          <CpfField label={t(`${k}guardianCpf`)} onChange={setGuardian("cpf")} />
          {errors.guardianCpf && <p className="text-sm text-destructive">{errors.guardianCpf}</p>}
          <Text label={t(`${k}guardianEmail`)} type="email" value={g.email} onChange={setGuardian("email")} error={errors.guardianEmail} />
          <PhoneField label={t(`${k}guardianPhone`)} onChange={setGuardian("phone")} />
          {errors.guardianPhone && <p className="text-sm text-destructive">{errors.guardianPhone}</p>}
        </fieldset>
      )}
      <Button type="submit" className="min-h-11 self-start" disabled={busy}>
        {t(`${k}submit`)}
      </Button>
    </form>
  );
}
