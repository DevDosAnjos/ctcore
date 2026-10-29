import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { EditConflictDialog, type Difference } from "@/components/EditConflictDialog";
import { LastChange } from "@/components/LastChange";
import { formatCpfCnpj, isValidCpfOrCnpj, onlyDigits } from "@/components/fields/masks";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { useEditConflict } from "@/lib/useEditConflict";
import { ColorField } from "./ColorField";
import { isHexColor, passesContrast } from "./contrastRatio";
import { LogoCropDialog } from "./LogoCropDialog";
import { checkLogoFile, LOGO_TYPES } from "./logoFile";
import { TransferCoordinationDialog } from "./TransferCoordinationDialog";
import { useOrganization, type OrganizationView } from "./useOrganization";
import { PageHeader } from "@/components/ui/page-header";

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

function DataCard({ view, api, canEdit }: { view: OrganizationView; api: Api; canEdit: boolean }) {
  const { t } = useTranslation();
  const { save, adopt } = api;
  const saveButton = useRef<HTMLButtonElement>(null);
  const conflict = useEditConflict(api.fetchCurrent);
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

  // Devolve false quando outra pessoa salvou antes (409): a janela de conflito abre e a edição fica como está (RNF-15).
  async function submit(version?: number): Promise<boolean> {
    setBusy(true);
    setErrors({});
    setNotice(undefined);
    try {
      await save({ name: name.trim(), color: color === "" ? null : color.toLowerCase(), holderDocument: holder === "" ? null : onlyDigits(holder) }, version);
      setNotice({ kind: "ok", text: t("organization.saved") });
    } catch (e) {
      if (e instanceof ApiError && e.code === "EDIT_CONFLICT") {
        conflict.open();
        return false;
      }
      const fields = e instanceof ApiError ? e.fields : [];
      setErrors(Object.fromEntries(fields.map((f) => [f.field, f.message])));
      setNotice({ kind: "error", text: t("organization.saveError") });
    } finally {
      setBusy(false);
    }
    return true;
  }

  const shownValue = (v: string) => (v === "" ? t("editConflict.empty") : v);
  function differences(current: OrganizationView): Difference[] {
    const fields: [string, string, string, (v: string) => string][] = [
      [t("organization.name"), name.trim(), current.name, (v) => v],
      [t("organization.color"), color.toLowerCase(), current.color ?? "", (v) => v],
      [t("organization.holder"), onlyDigits(holder), current.holderDocument ?? "", formatCpfCnpj],
    ];
    return fields
      .filter(([, mine, now]) => mine !== now)
      .map(([label, mine, now, show]) => ({ label, mine: shownValue(show(mine)), current: shownValue(show(now)) }));
  }
  function reload(current: OrganizationView) {
    setName(current.name);
    setColor(current.color ?? "");
    setHolder(current.holderDocument ? formatCpfCnpj(current.holderDocument) : "");
    setHolderTouched(false);
    setErrors({});
    setNotice(undefined);
    adopt(current);
    conflict.close();
  }

  return (
    <Card heading="h2" title={t("organization.dataTitle")}>
      <form
        className="flex max-w-xl flex-col gap-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          if (!blocked) void submit();
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
            <Button ref={saveButton} type="submit" disabled={blocked}>
              {busy ? t("organization.saving") : t("organization.save")}
            </Button>
          </div>
        )}
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

function TransferCard({ onDone }: { onDone: () => void }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <Card heading="h2" title={t("organization.transfer.title")}>
      <div className="flex flex-col items-start gap-3">
        <p className="text-sm text-muted-foreground">{t("organization.transfer.summary")}</p>
        <Button variant="outline" onClick={() => setOpen(true)}>
          {t("organization.transfer.open")}
        </Button>
      </div>
      <TransferCoordinationDialog open={open} onClose={() => setOpen(false)} onDone={onDone} />
    </Card>
  );
}

function Content({ canEdit }: { canEdit: boolean }) {
  const { t } = useTranslation();
  const api = useOrganization();
  const [notice, setNotice] = useState<Notice>();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.view) return <ErrorState onRetry={api.retry} />;
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("organization.title")} />
      {!canEdit && <p className="text-muted-foreground">{t("organization.readOnly")}</p>}
      <LastChange value={api.view.lastChange} />
      <DataCard view={api.view} api={api} canEdit={canEdit} />
      <LogoCard view={api.view} api={api} canEdit={canEdit} />
      {canEdit && <TransferCard onDone={() => setNotice({ kind: "ok", text: t("organization.transfer.done") })} />}
      <NoticeText notice={notice} />
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
