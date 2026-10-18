import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { formatCpfCnpj, isValidCpfOrCnpj, onlyDigits } from "@/components/fields/masks";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { ColorField } from "./ColorField";
import { isHexColor, passesContrast } from "./contrastRatio";
import { LogoCropDialog } from "./LogoCropDialog";
import { checkLogoFile, LOGO_TYPES } from "./logoFile";
import { useOrganization, type OrganizationView } from "./useOrganization";

type Notice = { kind: "ok" | "error"; text: string };
type Api = ReturnType<typeof useOrganization>;

const inputClass = "min-h-11 w-full rounded-md border bg-background px-2 disabled:opacity-60";

function NoticeText({ notice }: { notice?: Notice }) {
  if (!notice) return null;
  return (
    <p role={notice.kind === "ok" ? "status" : "alert"} className={notice.kind === "ok" ? "text-sm" : "text-sm text-destructive"}>
      {notice.text}
    </p>
  );
}

function DataCard({ view, save, canEdit }: { view: OrganizationView; save: Api["save"]; canEdit: boolean }) {
  const { t } = useTranslation();
  const [name, setName] = useState(view.name);
  const [color, setColor] = useState(view.color ?? "");
  const [holder, setHolder] = useState(view.holderDocument ? formatCpfCnpj(view.holderDocument) : "");
  const [holderTouched, setHolderTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState<Notice>();

  const nameError = name.trim() === "" ? t("organization.nameRequired") : name.trim().length > 120 ? t("organization.nameTooLong") : undefined;
  const colorBad = color !== "" && (!isHexColor(color) || !passesContrast(color));
  const holderBad = holder !== "" && !isValidCpfOrCnpj(holder);
  const blocked = !!nameError || colorBad || holderBad || busy;
  const nameShown = canEdit ? (errors.name ?? nameError) : undefined;
  const holderShown = errors.holderDocument ?? (holderTouched && holderBad ? t("organization.holderInvalid") : undefined);

  async function submit() {
    if (blocked) return;
    setBusy(true);
    setErrors({});
    setNotice(undefined);
    try {
      await save({ name: name.trim(), color: color === "" ? null : color.toLowerCase(), holderDocument: holder === "" ? null : onlyDigits(holder) });
      setNotice({ kind: "ok", text: t("organization.saved") });
    } catch (e) {
      const fields = e instanceof ApiError ? e.fields : [];
      setErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])));
      setNotice({ kind: "error", text: t("organization.saveError") });
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card heading="h2" title={t("organization.dataTitle")}>
      <form
        className="flex max-w-xl flex-col gap-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <div>
          <label htmlFor="org-name" className="block text-sm font-medium">
            {t("organization.name")}
          </label>
          <input
            id="org-name"
            className={inputClass}
            value={name}
            disabled={!canEdit}
            maxLength={120}
            aria-invalid={nameShown ? true : undefined}
            aria-describedby={nameShown ? "org-name-erro" : undefined}
            onChange={(e) => setName(e.target.value)}
          />
          {nameShown && (
            <p id="org-name-erro" role="alert" className="text-sm text-destructive">
              {nameShown}
            </p>
          )}
        </div>
        <ColorField value={color} onChange={setColor} disabled={!canEdit} error={errors.color} />
        <div>
          <label htmlFor="org-holder" className="block text-sm font-medium">
            {t("organization.holder")}
          </label>
          <input
            id="org-holder"
            className={inputClass}
            inputMode="numeric"
            autoComplete="off"
            value={holder}
            disabled={!canEdit}
            aria-invalid={holderShown ? true : undefined}
            aria-describedby="org-holder-ajuda"
            onChange={(e) => setHolder(formatCpfCnpj(e.target.value))}
            onBlur={() => setHolderTouched(true)}
          />
          <p id="org-holder-ajuda" className="text-sm text-muted-foreground">
            {holder === "" ? t("organization.holderEmpty") : undefined}
          </p>
          {holderShown && (
            <p role="alert" className="text-sm text-destructive">
              {holderShown}
            </p>
          )}
        </div>
        <NoticeText notice={notice} />
        {canEdit && (
          <div>
            <Button type="submit" disabled={blocked}>
              {busy ? t("organization.saving") : t("organization.save")}
            </Button>
          </div>
        )}
      </form>
    </Card>
  );
}

function LogoCard({ view, api, canEdit }: { view: OrganizationView; api: Api; canEdit: boolean }) {
  const { t } = useTranslation();
  const input = useRef<HTMLInputElement>(null);
  const [cropping, setCropping] = useState<File | null>(null);
  const [removing, setRemoving] = useState(false);
  const [notice, setNotice] = useState<Notice>();

  function pick(file: File | undefined) {
    if (!file) return;
    const problem = checkLogoFile(file);
    if (problem) {
      setNotice({ kind: "error", text: t(problem === "type" ? "organization.logoTypeError" : "organization.logoSizeError") });
      return;
    }
    setNotice(undefined);
    setCropping(file);
  }

  return (
    <Card heading="h2" title={t("organization.logoTitle")}>
      <div className="flex flex-col gap-3">
        {view.logoUrl ? (
          <img src={view.logoUrl} alt={t("organization.logoAlt")} width={128} height={128} className="size-32 rounded-md border object-cover" />
        ) : (
          <p className="text-muted-foreground">{t("organization.logoEmpty")}</p>
        )}
        <p className="text-sm text-muted-foreground">{t("organization.logoHint")}</p>
        <NoticeText notice={notice} />
        {canEdit && (
          <div className="flex flex-wrap gap-2">
            <input
              ref={input}
              type="file"
              hidden
              aria-label={t("organization.logoFile")}
              accept={LOGO_TYPES.join(",")}
              onChange={(e) => {
                pick(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
            <Button variant="outline" onClick={() => input.current?.click()}>
              {view.logoUrl ? t("organization.logoReplace") : t("organization.logoUpload")}
            </Button>
            {view.logoUrl && (
              <Button variant="outline" onClick={() => setRemoving(true)}>
                {t("organization.logoRemove")}
              </Button>
            )}
          </div>
        )}
      </div>
      {cropping && (
        <LogoCropDialog
          file={cropping}
          onCancel={() => setCropping(null)}
          onConfirm={async (logo) => {
            await api.uploadLogo(logo);
            setCropping(null);
            setNotice({ kind: "ok", text: t("organization.logoUpdated") });
          }}
        />
      )}
      <ConfirmDialog
        open={removing}
        onOpenChange={setRemoving}
        title={t("organization.logoRemove")}
        summary={t("organization.logoRemoveSummary")}
        confirmLabel={t("organization.logoRemove")}
        onConfirm={async () => {
          try {
            await api.removeLogo();
            setNotice({ kind: "ok", text: t("organization.logoRemoved") });
          } catch (e) {
            setNotice({ kind: "error", text: t("organization.logoError") });
            throw e;
          }
        }}
      />
    </Card>
  );
}

function Content({ canEdit }: { canEdit: boolean }) {
  const { t } = useTranslation();
  const api = useOrganization();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.view) return <ErrorState onRetry={api.retry} />;
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("organization.title")}</h1>
      {!canEdit && <p className="text-muted-foreground">{t("organization.readOnly")}</p>}
      <DataCard view={api.view} save={api.save} canEdit={canEdit} />
      <LogoCard view={api.view} api={api} canEdit={canEdit} />
    </div>
  );
}

// RF-ORG-01: o Coordenador edita; o Administrador só vê (D8). A barreira é o servidor; aqui só se esconde o que não funciona.
export function OrganizationSettingsPage() {
  const { state } = useSession();
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role;
  return (
    <ManagementLayout activeId="organizationSettings">
      <Content canEdit={role === "COORDINATOR"} />
    </ManagementLayout>
  );
}
