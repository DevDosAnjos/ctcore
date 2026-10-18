import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CircleAlert, CircleCheck } from "lucide-react";
import { Card } from "@/components/Card";
import { EditConflictDialog, type Difference } from "@/components/EditConflictDialog";
import { LastChange } from "@/components/LastChange";
import { MoneyField, PhoneField } from "@/components/fields/fields";
import { formatCep, formatCpfCnpj, isValidCnpj, isValidPhone, onlyDigits } from "@/components/fields/masks";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { formatCurrency } from "@/lib/format";
import { useEditConflict } from "@/lib/useEditConflict";
import { Text } from "./Text";
import { ALL, useUnitScope } from "./UnitScopeProvider";
import {
  FEE_KEYS,
  TEACHER_KEYS,
  useUnitDetail,
  type FeeKey,
  type TeacherKey,
  type UnitDetail,
  type UnitUpdate,
} from "./useUnitSettings";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const MAX_REMINDERS = 8;

// Rascunho da tela: textos como o usuário vê; a conversão para o corpo do PUT fica em toBody.
type Draft = {
  name: string;
  cnpj: string;
  postalCode: string;
  address: string;
  city: string;
  latitude: string;
  longitude: string;
  phone: string;
  whatsapp: string;
  email: string;
  dueDays: number[];
  enrollmentFeeEnabled: boolean;
  enrollmentFeeCents: number | null;
  fees: Record<FeeKey, number | null>;
  pixKey: string;
  reminders: string;
  teacher: Record<TeacherKey, boolean>;
  directoryEnabled: boolean;
  trialLinkEnabled: boolean;
  selfRegistrationLinkEnabled: boolean;
};

function toDraft(d: UnitDetail): Draft {
  const s = d.settings;
  return {
    name: d.name,
    cnpj: d.cnpj ? formatCpfCnpj(d.cnpj) : "",
    postalCode: d.postalCode ? formatCep(d.postalCode) : "",
    address: d.address ?? "",
    city: d.city ?? "",
    latitude: d.latitude === null ? "" : String(d.latitude),
    longitude: d.longitude === null ? "" : String(d.longitude),
    phone: d.phone ?? "",
    whatsapp: d.whatsapp ?? "",
    email: d.email ?? "",
    dueDays: s.dueDays,
    enrollmentFeeEnabled: s.enrollmentFeeEnabled,
    enrollmentFeeCents: s.enrollmentFeeCents,
    fees: s.feesCents,
    pixKey: s.pixKey ?? "",
    reminders: s.reminderDays.join(", "),
    teacher: s.teacherPermissions,
    directoryEnabled: s.directoryEnabled,
    trialLinkEnabled: s.trialLinkEnabled,
    selfRegistrationLinkEnabled: s.selfRegistrationLinkEnabled,
  };
}

// Lista de inteiros separados por vírgula; null se algum item não for inteiro.
function parseDays(text: string): number[] | null {
  const parts = text.split(/[,;\s]+/).filter(Boolean);
  const numbers = parts.map((p) => Number(p));
  return numbers.every((n) => Number.isInteger(n)) ? numbers : null;
}

const coordinate = (v: string) => (v.trim() === "" ? null : Number(v.replace(",", ".")));
const blankToNull = (v: string) => (v.trim() === "" ? null : v.trim());

// PUT completo (D10). Com CEP novo e coordenadas intocadas, elas vão vazias para o servidor buscar as do CEP (D3).
function toBody(d: Draft, base: Draft): UnitUpdate {
  const cepChanged = onlyDigits(d.postalCode) !== onlyDigits(base.postalCode);
  const coordsTouched = d.latitude !== base.latitude || d.longitude !== base.longitude;
  const useCep = cepChanged && !coordsTouched;
  return {
    name: d.name.trim(),
    cnpj: blankToNull(onlyDigits(d.cnpj)),
    postalCode: blankToNull(onlyDigits(d.postalCode)),
    address: blankToNull(d.address),
    city: blankToNull(d.city),
    latitude: useCep ? null : coordinate(d.latitude),
    longitude: useCep ? null : coordinate(d.longitude),
    phone: blankToNull(d.phone),
    email: blankToNull(d.email),
    whatsapp: blankToNull(d.whatsapp),
    settings: {
      dueDays: d.dueDays,
      enrollmentFeeEnabled: d.enrollmentFeeEnabled,
      enrollmentFeeCents: d.enrollmentFeeEnabled ? (d.enrollmentFeeCents ?? 0) : 0,
      feesCents: Object.fromEntries(FEE_KEYS.map((k) => [k, d.fees[k] ?? 0])) as Record<FeeKey, number>,
      pixKey: blankToNull(d.pixKey),
      reminderDays: parseDays(d.reminders) ?? [],
      teacherPermissions: d.teacher,
      directoryEnabled: d.directoryEnabled,
      trialLinkEnabled: d.trialLinkEnabled,
      selfRegistrationLinkEnabled: d.selfRegistrationLinkEnabled,
    },
  };
}

function Check({ label, checked, onChange, help, disabled }: { label: string; checked: boolean; onChange: (v: boolean) => void; help?: string; disabled?: boolean }) {
  return (
    <label className="flex min-h-11 items-start gap-2 py-1">
      <input type="checkbox" className="mt-1 size-5" checked={checked} disabled={disabled} onChange={(e) => onChange(e.target.checked)} />
      <span>
        <span className="block text-sm font-medium">{label}</span>
        {help && <span className="block text-sm text-muted-foreground">{help}</span>}
      </span>
    </label>
  );
}

function Err({ text }: { text?: string }) {
  return text ? <p className="text-sm text-destructive">{text}</p> : null;
}

function Form({ detail, api }: { detail: UnitDetail; api: ReturnType<typeof useUnitDetail> }) {
  const { t } = useTranslation();
  const { save, adopt } = api;
  const saveButton = useRef<HTMLButtonElement>(null);
  const conflict = useEditConflict(api.fetchCurrent);
  const [nonce, setNonce] = useState(0); // "Recarregar" remonta os campos, que guardam o valor inicial por conta própria
  const [base, setBase] = useState(() => toDraft(detail));
  const [d, setD] = useState(base);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState<{ ok: boolean; text: string }>();
  const set = <K extends keyof Draft>(k: K) => (v: Draft[K]) => setD((p) => ({ ...p, [k]: v }));

  const lat = coordinate(d.latitude);
  const lon = coordinate(d.longitude);
  const reminders = parseDays(d.reminders);
  const problems = {
    name: d.name.trim() === "" ? t("unitSettings.nameRequired") : d.name.trim().length > 120 ? t("unitSettings.nameTooLong") : undefined,
    cnpj: d.cnpj !== "" && !isValidCnpj(d.cnpj) ? t("unitSettings.cnpjInvalid") : undefined,
    postalCode: d.postalCode !== "" && onlyDigits(d.postalCode).length !== 8 ? t("unitSettings.postalCodeInvalid") : undefined,
    coordinates:
      (lat === null) !== (lon === null) || (lat !== null && (!Number.isFinite(lat) || Math.abs(lat) > 90)) || (lon !== null && (!Number.isFinite(lon) || Math.abs(lon) > 180))
        ? t("unitSettings.coordinatesInvalid")
        : undefined,
    phone: d.phone !== "" && !isValidPhone(d.phone) ? t("fields.phoneInvalid") : undefined,
    whatsapp: d.whatsapp !== "" && !isValidPhone(d.whatsapp) ? t("fields.phoneInvalid") : undefined,
    email: d.email.trim() !== "" && !EMAIL.test(d.email.trim()) ? t("unitSettings.emailInvalid") : undefined,
    dueDays: d.dueDays.length === 0 ? t("unitSettings.dueDaysRequired") : undefined,
    reminders:
      !reminders || reminders.some((n) => n < -30 || n > 60) || new Set(reminders).size !== reminders.length || reminders.length > MAX_REMINDERS
        ? t("unitSettings.remindersInvalid", { max: MAX_REMINDERS })
        : undefined,
  };
  const dirty = JSON.stringify(d) !== JSON.stringify(base);
  const blocked = Object.values(problems).some(Boolean);
  const shown = (field: string, problem?: string) => errors[field] ?? problem;

  // Devolve false quando outra pessoa salvou antes (409): a janela de conflito abre e a edição fica como está (RNF-15).
  async function submit(version?: number): Promise<boolean> {
    setBusy(true);
    setErrors({});
    setNotice(undefined);
    try {
      const saved = await save(toBody(d, base), version);
      const next = toDraft(saved);
      setBase(next);
      setD(next);
      setNotice({ ok: true, text: t("unitSettings.saved") });
    } catch (e) {
      if (e instanceof ApiError && e.code === "EDIT_CONFLICT") {
        conflict.open();
        return false;
      }
      const fields = e instanceof ApiError ? e.fields : [];
      setErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])));
      setNotice({ ok: false, text: t("unitSettings.saveError") });
    } finally {
      setBusy(false);
    }
    return true;
  }

  function differences(current: UnitDetail): Difference[] {
    const c = toDraft(current);
    const text = (v: unknown) =>
      typeof v === "boolean" ? t(v ? "editConflict.yes" : "editConflict.no") : Array.isArray(v) ? v.join(", ") : v === null || v === "" ? t("editConflict.empty") : String(v);
    const money = (v: unknown) => (typeof v === "number" ? formatCurrency(v) : text(v));
    const fields: [string, unknown, unknown, (v: unknown) => string][] = [
      [t("unitSettings.name"), d.name.trim(), c.name, text],
      [t("unitSettings.cnpj"), d.cnpj, c.cnpj, text],
      [t("unitSettings.postalCode"), d.postalCode, c.postalCode, text],
      [t("unitSettings.address"), d.address, c.address, text],
      [t("unitSettings.city"), d.city, c.city, text],
      [t("unitSettings.latitude"), d.latitude, c.latitude, text],
      [t("unitSettings.longitude"), d.longitude, c.longitude, text],
      [t("unitSettings.phone"), d.phone, c.phone, text],
      [t("unitSettings.whatsapp"), d.whatsapp, c.whatsapp, text],
      [t("unitSettings.email"), d.email.trim(), c.email, text],
      [t("unitSettings.dueDays"), d.dueDays, c.dueDays, text],
      [t("unitSettings.enrollmentFee"), d.enrollmentFeeEnabled, c.enrollmentFeeEnabled, text],
      [t("unitSettings.enrollmentFeeValue"), d.enrollmentFeeCents, c.enrollmentFeeCents, money],
      ...FEE_KEYS.map((k): [string, unknown, unknown, (v: unknown) => string] => [t(`unitSettings.fees.${k}`), d.fees[k], c.fees[k], money]),
      [t("unitSettings.pixKey"), d.pixKey, c.pixKey, text],
      [t("unitSettings.reminders"), d.reminders, c.reminders, text],
      ...TEACHER_KEYS.map((k): [string, unknown, unknown, (v: unknown) => string] => [t(`unitSettings.teacher.${k}`), d.teacher[k], c.teacher[k], text]),
      [t("unitSettings.directory"), d.directoryEnabled, c.directoryEnabled, text],
      [t("unitSettings.trialLink"), d.trialLinkEnabled, c.trialLinkEnabled, text],
      [t("unitSettings.selfRegistrationLink"), d.selfRegistrationLinkEnabled, c.selfRegistrationLinkEnabled, text],
    ];
    return fields
      .filter(([, mine, now]) => JSON.stringify(mine) !== JSON.stringify(now))
      .map(([label, mine, now, show]) => ({ label, mine: show(mine), current: show(now) }));
  }

  function reload(current: UnitDetail) {
    const next = toDraft(current);
    setBase(next);
    setD(next);
    setErrors({});
    setNotice(undefined);
    adopt(current);
    setNonce((n) => n + 1);
    conflict.close();
  }

  const connected = detail.settings.paymentAccount === "CONNECTED";
  const toggleDay = (day: number) =>
    set("dueDays")(d.dueDays.includes(day) ? d.dueDays.filter((x) => x !== day) : [...d.dueDays, day].sort((a, b) => a - b));

  return (
    <>
    <form
      key={nonce}
      className="flex max-w-3xl flex-col gap-4"
      noValidate
      onSubmit={(e) => {
        e.preventDefault();
        if (!blocked && dirty && !busy) void submit();
      }}
    >
      <LastChange value={detail.lastChange} />
      <Card heading="h2" title={t("unitSettings.sections.data")}>
        <div className="flex flex-col gap-3">
          <Text label={t("unitSettings.name")} value={d.name} onChange={set("name")} maxLength={120} autoComplete="off" error={shown("name", problems.name)} />
          <Text label={t("unitSettings.shortName")} value={detail.shortName} onChange={() => undefined} readOnly help={t("unitSettings.shortNameHelp")} />
          <Text label={t("unitSettings.cnpj")} value={d.cnpj} onChange={(v) => set("cnpj")(formatCpfCnpj(v))} inputMode="numeric" autoComplete="off" error={shown("cnpj", problems.cnpj)} />
          <Text label={t("unitSettings.postalCode")} value={d.postalCode} onChange={(v) => set("postalCode")(formatCep(v))} inputMode="numeric" autoComplete="postal-code" error={shown("postalCode", problems.postalCode)} />
          <Text label={t("unitSettings.address")} value={d.address} onChange={set("address")} autoComplete="street-address" error={errors.address} />
          <Text label={t("unitSettings.city")} value={d.city} onChange={set("city")} autoComplete="address-level2" error={errors.city} />
          <details>
            <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium">{t("unitSettings.coordinatesToggle")}</summary>
            <div className="flex flex-col gap-3">
              <p className="text-sm text-muted-foreground">{t("unitSettings.coordinatesHelp")}</p>
              <Text label={t("unitSettings.latitude")} value={d.latitude} onChange={set("latitude")} inputMode="decimal" error={errors.latitude} />
              <Text label={t("unitSettings.longitude")} value={d.longitude} onChange={set("longitude")} inputMode="decimal" error={shown("longitude", problems.coordinates)} />
            </div>
          </details>
          <PhoneField label={t("unitSettings.phone")} initial={d.phone} onChange={set("phone")} />
          <Err text={errors.phone} />
          <PhoneField label={t("unitSettings.whatsapp")} initial={d.whatsapp} onChange={set("whatsapp")} />
          <Err text={errors.whatsapp} />
          <Text label={t("unitSettings.email")} value={d.email} onChange={set("email")} type="email" autoComplete="off" error={shown("email", problems.email)} />
        </div>
      </Card>

      <Card heading="h2" title={t("unitSettings.sections.billing")}>
        <div className="flex flex-col gap-3">
          <fieldset>
            <legend className="text-sm font-medium">{t("unitSettings.dueDays")}</legend>
            <p className="text-sm text-muted-foreground">{t("unitSettings.dueDaysHelp")}</p>
            <div className="mt-1 grid grid-cols-4 gap-1 sm:grid-cols-7">
              {Array.from({ length: 28 }, (_, i) => i + 1).map((day) => (
                <label key={day} className="flex min-h-11 items-center gap-2 rounded-md border px-2">
                  <input type="checkbox" className="size-5" checked={d.dueDays.includes(day)} onChange={() => toggleDay(day)} />
                  <span>{day}</span>
                </label>
              ))}
            </div>
            <Err text={shown("settings.dueDays", problems.dueDays)} />
          </fieldset>
          <Check label={t("unitSettings.enrollmentFee")} checked={d.enrollmentFeeEnabled} onChange={set("enrollmentFeeEnabled")} />
          <MoneyField
            label={t("unitSettings.enrollmentFeeValue")}
            initial={d.enrollmentFeeCents}
            onChange={set("enrollmentFeeCents")}
            disabled={!d.enrollmentFeeEnabled}
          />
          <Err text={errors["settings.enrollmentFeeCents"]} />
        </div>
      </Card>

      <Card heading="h2" title={t("unitSettings.sections.payments")}>
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">{t("unitSettings.feesHelp")}</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            {FEE_KEYS.map((key) => (
              <div key={key}>
                <MoneyField label={t(`unitSettings.fees.${key}`)} initial={d.fees[key]} onChange={(v) => setD((p) => ({ ...p, fees: { ...p.fees, [key]: v } }))} />
                <Err text={errors[`settings.feesCents.${key}`]} />
              </div>
            ))}
          </div>
          <Text label={t("unitSettings.pixKey")} value={d.pixKey} onChange={set("pixKey")} autoComplete="off" help={t("unitSettings.pixKeyHelp")} error={errors["settings.pixKey"]} />
          <div className="flex items-start gap-2 rounded-md border p-3">
            {connected ? <CircleCheck aria-hidden className="mt-0.5 size-5 shrink-0" /> : <CircleAlert aria-hidden className="mt-0.5 size-5 shrink-0" />}
            <div>
              <p className="text-sm font-medium">{connected ? t("unitSettings.mercadoPagoConnected") : t("unitSettings.mercadoPagoNotConnected")}</p>
              <p className="text-sm text-muted-foreground">{t("unitSettings.mercadoPagoLater")}</p>
            </div>
          </div>
        </div>
      </Card>

      <Card heading="h2" title={t("unitSettings.sections.reminders")}>
        <Text label={t("unitSettings.reminders")} value={d.reminders} onChange={set("reminders")} inputMode="text" autoComplete="off" help={t("unitSettings.remindersHelp")} error={shown("settings.reminderDays", problems.reminders)} />
      </Card>

      <Card heading="h2" title={t("unitSettings.sections.teacher")}>
        <fieldset>
          <legend className="text-sm text-muted-foreground">{t("unitSettings.teacherHelp")}</legend>
          {TEACHER_KEYS.map((key) => (
            <Check key={key} label={t(`unitSettings.teacher.${key}`)} checked={d.teacher[key]} onChange={(v) => setD((p) => ({ ...p, teacher: { ...p.teacher, [key]: v } }))} />
          ))}
        </fieldset>
      </Card>

      <Card heading="h2" title={t("unitSettings.sections.publicity")}>
        <div className="flex flex-col">
          <Check label={t("unitSettings.directory")} help={t("unitSettings.directoryHelp")} checked={d.directoryEnabled} onChange={set("directoryEnabled")} />
          <Check label={t("unitSettings.trialLink")} checked={d.trialLinkEnabled} onChange={set("trialLinkEnabled")} />
          <Check label={t("unitSettings.selfRegistrationLink")} checked={d.selfRegistrationLinkEnabled} onChange={set("selfRegistrationLinkEnabled")} />
        </div>
      </Card>

      {notice && (
        <p role={notice.ok ? "status" : "alert"} className={notice.ok ? "text-sm" : "text-sm text-destructive"}>
          {notice.text}
        </p>
      )}
      <div>
        <Button ref={saveButton} type="submit" disabled={blocked || !dirty || busy}>
          {busy ? t("unitSettings.saving") : t("unitSettings.save")}
        </Button>
      </div>
    </form>
    <EditConflictDialog
      state={conflict.state}
      differences={conflict.state?.status === "ready" ? differences(conflict.state.current) : []}
      busy={busy}
      returnFocusRef={saveButton}
      onRetry={conflict.open}
      onClose={conflict.close}
      onReload={() => conflict.state?.status === "ready" && reload(conflict.state.current)}
      onOverwrite={() => {
        if (conflict.state?.status !== "ready") return;
        void submit(conflict.state.current.version).then((done) => done && conflict.close());
      }}
    />
    </>
  );
}

function Detail({ id }: { id: string }) {
  const api = useUnitDetail(id);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.detail) return <ErrorState onRetry={api.retry} />;
  return <Form key={api.detail.id} detail={api.detail} api={api} />;
}

function Content() {
  const { t } = useTranslation();
  const scope = useUnitScope();
  if (scope.status === "loading") return <LoadingState />;
  if (scope.status === "forbidden") return <ForbiddenState />;
  if (scope.status === "error") return <ErrorState onRetry={scope.retry} />;
  if (scope.selected === null) return <EmptyState title={t("unitSettings.emptyTitle")} text={t("unitSettings.emptyText")} />;
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("unitSettings.title")}</h1>
      {/* RN-ORG-08: em "Todas as unidades" nada é alterado; a unidade vem do seletor do topo (RF-ORG-05). */}
      {scope.selected === ALL ? <p role="status">{t("unitScope.chooseToEdit")}</p> : <Detail id={scope.selected} />}
    </div>
  );
}

// RF-ORG-04: Coordenador e Administrador (a rota já nega os demais; a barreira de dados é o servidor).
export function UnitSettingsPage() {
  return (
    <ManagementLayout activeId="unitSettings">
      <Content />
    </ManagementLayout>
  );
}
