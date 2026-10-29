import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { Card } from "@/components/Card";
import { isValidPhone, onlyDigits } from "@/components/fields/masks";
import { PhoneField } from "@/components/fields/fields";
import { ForbiddenState, LoadingState } from "@/components/states";
import { Stepper } from "@/components/Stepper";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { Text } from "./Text";
import { createUnit, type NewUnit, type NewUnitResult } from "./useUnits";
import { PageHeader } from "@/components/ui/page-header";

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

type Form = {
  name: string;
  postalCode: string;
  address: string;
  city: string;
  latitude: string;
  longitude: string;
  phone: string;
  whatsapp: string;
  email: string;
  adminName: string;
  adminEmail: string;
};
const empty: Form = { name: "", postalCode: "", address: "", city: "", latitude: "", longitude: "", phone: "", whatsapp: "", email: "", adminName: "", adminEmail: "" };

// Latitude e longitude vazias valem como "usar o CEP"; preenchidas, as duas e dentro das faixas.
const coordinate = (v: string) => (v.trim() === "" ? null : Number(v.replace(",", ".")));
function coordinatesOk(f: Form) {
  const lat = coordinate(f.latitude);
  const lon = coordinate(f.longitude);
  if (lat === null && lon === null) return true;
  return lat !== null && lon !== null && Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180;
}

function body(f: Form): NewUnit {
  const opt = (v: string) => (v.trim() === "" ? undefined : v.trim());
  return {
    name: f.name.trim(),
    postalCode: opt(f.postalCode),
    address: opt(f.address),
    city: opt(f.city),
    latitude: coordinate(f.latitude) ?? undefined,
    longitude: coordinate(f.longitude) ?? undefined,
    phone: opt(f.phone),
    whatsapp: opt(f.whatsapp),
    email: opt(f.email),
    administrator: opt(f.adminName) ? { name: f.adminName.trim(), email: f.adminEmail.trim() } : undefined,
  };
}

function Wizard({ onDone, onForbidden }: { onDone: (r: NewUnitResult) => void; onForbidden: () => void }) {
  const { t } = useTranslation();
  const [f, setF] = useState<Form>(empty);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string>();
  const set = (k: keyof Form) => (v: string) => setF((p) => ({ ...p, [k]: v }));

  const nameError = f.name.trim() === "" ? t("units.new.nameRequired") : f.name.trim().length > 120 ? t("units.new.nameTooLong") : undefined;
  const cepBad = f.postalCode.trim() !== "" && onlyDigits(f.postalCode).length !== 8;
  const coordsBad = !coordinatesOk(f);
  const phoneBad = f.phone !== "" && !isValidPhone(f.phone);
  const whatsappBad = f.whatsapp !== "" && !isValidPhone(f.whatsapp);
  const emailBad = f.email.trim() !== "" && !EMAIL.test(f.email.trim());
  const hasName = f.adminName.trim() !== "";
  const hasMail = f.adminEmail.trim() !== "";
  const inviteIncomplete = hasName !== hasMail;
  const inviteMailBad = hasMail && !EMAIL.test(f.adminEmail.trim());

  async function finish() {
    if (busy) return;
    setBusy(true);
    setFailure(undefined);
    try {
      onDone(await createUnit(body(f)));
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) return onForbidden();
      setFailure(e instanceof ApiError && e.code === "UNIT_LIMIT_REACHED" ? t("units.new.limitReached") : t("units.new.error"));
      setBusy(false);
    }
  }

  const steps = [
    {
      id: "unit",
      title: t("units.new.steps.unit"),
      valid: !nameError,
      content: <Text label={t("units.new.name")} value={f.name} onChange={set("name")} help={t("units.new.nameHelp")} error={f.name !== "" ? nameError : undefined} autoComplete="off" />,
    },
    {
      id: "address",
      title: t("units.new.steps.address"),
      valid: !cepBad && !coordsBad,
      content: (
        <div className="flex flex-col gap-3">
          <Text label={t("units.new.postalCode")} value={f.postalCode} onChange={set("postalCode")} inputMode="numeric" autoComplete="postal-code" maxLength={9} error={cepBad ? t("units.new.postalCodeInvalid") : undefined} />
          <Text label={t("units.new.address")} value={f.address} onChange={set("address")} autoComplete="street-address" />
          <Text label={t("units.new.city")} value={f.city} onChange={set("city")} autoComplete="address-level2" />
          <details>
            <summary className="min-h-11 cursor-pointer py-2 text-sm font-medium">{t("units.new.coordinatesToggle")}</summary>
            <div className="flex flex-col gap-3">
              <p className="text-sm text-muted-foreground">{t("units.new.coordinatesHelp")}</p>
              <Text label={t("units.new.latitude")} value={f.latitude} onChange={set("latitude")} inputMode="decimal" />
              <Text label={t("units.new.longitude")} value={f.longitude} onChange={set("longitude")} inputMode="decimal" error={coordsBad ? t("units.new.coordinatesInvalid") : undefined} />
            </div>
          </details>
        </div>
      ),
    },
    {
      id: "contact",
      title: t("units.new.steps.contact"),
      valid: !phoneBad && !whatsappBad && !emailBad && !inviteIncomplete && !inviteMailBad,
      content: (
        <div className="flex flex-col gap-3">
          <PhoneField label={t("units.new.phone")} initial={f.phone} onChange={set("phone")} />
          <PhoneField label={t("units.new.whatsapp")} initial={f.whatsapp} onChange={set("whatsapp")} />
          <Text label={t("units.new.email")} value={f.email} onChange={set("email")} type="email" autoComplete="off" error={emailBad ? t("units.new.emailInvalid") : undefined} />
          <fieldset className="flex flex-col gap-3 rounded-md border p-3">
            <legend className="px-1 text-sm font-medium">{t("units.new.inviteTitle")}</legend>
            <Text label={t("units.new.inviteName")} value={f.adminName} onChange={set("adminName")} autoComplete="off" />
            <Text
              label={t("units.new.inviteEmail")}
              value={f.adminEmail}
              onChange={set("adminEmail")}
              type="email"
              autoComplete="off"
              error={inviteIncomplete ? t("units.new.inviteIncomplete") : inviteMailBad ? t("units.new.emailInvalid") : undefined}
            />
            <p role="status" className="text-sm">
              {hasName && hasMail && !inviteMailBad ? t("units.new.inviteSummary", { name: f.adminName.trim(), email: f.adminEmail.trim() }) : t("units.new.noInvite")}
            </p>
          </fieldset>
        </div>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-3">
      {busy && <LoadingState />}
      {failure && (
        <p role="alert" className="text-sm text-destructive">
          {failure} {t("units.new.retry")}
        </p>
      )}
      <Stepper steps={steps} onFinish={() => void finish()} />
    </div>
  );
}

function Created({ result }: { result: NewUnitResult }) {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={t("units.new.created", { name: result.unit.name })}>
      <p role="status">{result.invite === "FAILED" ? t("units.new.inviteFailed") : result.invite === "SENT" ? t("units.new.inviteSent") : null}</p>
      <Button asChild className="mt-3">
        <Link to="/configuracoes/unidades">{t("units.new.toList")}</Link>
      </Button>
    </Card>
  );
}

function Content({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <div className="flex max-w-xl flex-col gap-4">
      <PageHeader title={t("units.new.title")} />
      <Link to="/configuracoes/unidades" className="text-sm underline">
        {t("units.new.back")}
      </Link>
      {children}
    </div>
  );
}

// RF-ORG-03: assistente de nova unidade. Só o Coordenador (a rota já nega os demais; a barreira de dados é o servidor).
export function NewUnitPage() {
  const navigate = useNavigate();
  const [forbidden, setForbidden] = useState(false);
  const [result, setResult] = useState<NewUnitResult>();
  function done(r: NewUnitResult) {
    // Convite que falhou fica na tela com o aviso; nos demais casos volta à lista, que já traz a unidade nova.
    if (r.invite === "FAILED") setResult(r);
    else void navigate("/configuracoes/unidades");
  }
  return (
    <ManagementLayout activeId="units">
      <Content>{forbidden ? <ForbiddenState /> : result ? <Created result={result} /> : <Wizard onDone={done} onForbidden={() => setForbidden(true)} />}</Content>
    </ManagementLayout>
  );
}
