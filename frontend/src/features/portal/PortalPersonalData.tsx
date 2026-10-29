import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { formatPhone, isValidPhone } from "@/components/fields/masks";
import { Card } from "@/components/Card";
import { Button } from "@/components/ui/button";
import { LogoCropDialog } from "@/features/organization/LogoCropDialog";
import { checkLogoFile, LOGO_TYPES } from "@/features/organization/logoFile";
import { formatDate } from "@/lib/format";
import { PortalContactDialog } from "./PortalContactDialog";
import { removePortalPhoto, uploadPortalPhoto, type InfoPersonal } from "./usePortalInfo";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");
const phone = (raw: string | null) => (raw ? (isValidPhone(raw) ? formatPhone(raw) : raw) : null);

// RF-POR-07, RN-ALU-11: nome, CPF, nascimento e e-mail só leitura; telefone, endereço, emergência e foto o aluno ou responsável edita.
export function PortalPersonalData({ studentId, personal, onChanged }: { studentId: string; personal: InfoPersonal; onChanged: () => void }) {
  const { t } = useTranslation();
  const k = "portalInfo.personal.";
  const none = t("portalInfo.none");
  const fileInput = useRef<HTMLInputElement>(null);
  const [editing, setEditing] = useState(false);
  const [cropping, setCropping] = useState<File | null>(null);
  const [notice, setNotice] = useState<string>();
  const [busy, setBusy] = useState(false);
  const { address: a, emergency: e } = personal;

  async function run(action: () => Promise<unknown>) {
    setBusy(true);
    setNotice(undefined);
    try {
      await action();
      onChanged();
    } catch {
      setNotice(t(`${k}photoFailed`));
    } finally {
      setBusy(false);
    }
  }
  function pick(file: File | undefined) {
    if (!file) return;
    const problem = checkLogoFile(file);
    if (problem) return setNotice(t(problem === "type" ? `${k}photoTypeError` : `${k}photoSizeError`));
    setNotice(undefined);
    setCropping(file);
  }
  const row = (label: string, value: string | null) => (
    <div className="flex flex-col sm:flex-row sm:gap-2">
      <dt className="font-medium sm:w-44 sm:shrink-0">{label}</dt>
      <dd className="min-w-0 break-words">{value ?? none}</dd>
    </div>
  );
  return (
    <Card
      heading="h2"
      title={t(`${k}title`)}
      actions={
        personal.canEdit && (
          <Button variant="outline" className="min-h-11" onClick={() => setEditing(true)}>
            {t(`${k}edit`)}
          </Button>
        )
      }
    >
      <div className="mb-3 flex flex-wrap items-center gap-3">
        {personal.photoUrl ? (
          <img src={personal.photoUrl} alt="" className="size-16 shrink-0 rounded-full object-cover" />
        ) : (
          <span aria-hidden="true" className="flex size-16 shrink-0 items-center justify-center rounded-full bg-muted text-xl font-semibold">
            {initials(personal.name)}
          </span>
        )}
        {personal.canEdit && (
          <div className="flex min-w-0 flex-col gap-1">
            <input
              ref={fileInput}
              type="file"
              hidden
              aria-label={t(`${k}photoFile`)}
              accept={LOGO_TYPES.join(",")}
              onChange={(ev) => {
                pick(ev.target.files?.[0]);
                ev.target.value = "";
              }}
            />
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="outline" className="min-h-11" disabled={busy} onClick={() => fileInput.current?.click()}>
                {personal.photoUrl ? t(`${k}photoReplace`) : t(`${k}photoChoose`)}
              </Button>
              {personal.photoUrl && (
                <Button type="button" variant="outline" className="min-h-11" disabled={busy} onClick={() => void run(() => removePortalPhoto(studentId))}>
                  {t(`${k}photoRemove`)}
                </Button>
              )}
            </div>
            {notice && (
              <p role="alert" className="text-sm text-danger-foreground">
                {notice}
              </p>
            )}
          </div>
        )}
      </div>
      <dl className="flex flex-col gap-2 text-sm">
        {row(t(`${k}name`), personal.name)}
        {row(t(`${k}birthDate`), formatDate(personal.birthDate))}
        {row(t(`${k}cpf`), personal.maskedCpf)}
        {row(t(`${k}email`), personal.email)}
        {row(t(`${k}phone`), phone(personal.phone))}
        {row(
          t(`${k}address`),
          a.street ? t(`${k}addressLine`, { street: a.street, number: a.number, complement: a.complement ? ` ${a.complement}` : "", district: a.district, city: a.city, state: a.state, postalCode: a.postalCode }) : null,
        )}
        {row(t(`${k}emergency`), e.name ? t(`${k}emergencyLine`, { name: e.name, relationship: e.relationship ?? "", phone: phone(e.phone) ?? "" }) : null)}
      </dl>
      <p className="mt-3 text-sm text-muted-foreground">{t(`${k}readOnlyHint`)}</p>
      {editing && <PortalContactDialog studentId={studentId} personal={personal} onClose={() => setEditing(false)} onSaved={onChanged} />}
      {cropping && (
        <LogoCropDialog
          file={cropping}
          labels={{ title: t(`${k}cropTitle`), hint: t(`${k}cropHint`), preview: t(`${k}cropPreview`), error: t(`${k}photoTypeError`) }}
          onCancel={() => setCropping(null)}
          onConfirm={async (blob) => {
            setCropping(null);
            await run(() => uploadPortalPhoto(studentId, blob));
          }}
        />
      )}
    </Card>
  );
}
