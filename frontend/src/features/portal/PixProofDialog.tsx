import { useId, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/format";
import { sendProof, usePixInstructions, type PixInstructions } from "./usePixProof";

type Props = { chargeIds: string[]; onClose: () => void; onSent?: () => void };

function Form({ data, chargeIds, onClose, onSent }: { data: PixInstructions } & Props) {
  const { t } = useTranslation();
  const fileId = useId();
  const [file, setFile] = useState<File>();
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [fieldError, setFieldError] = useState<string>();
  const [formError, setFormError] = useState<string>();
  const [copy, setCopy] = useState<"copied" | "failed">();

  async function copyKey() {
    try {
      await navigator.clipboard.writeText(data.pixKey);
      setCopy("copied");
    } catch {
      setCopy("failed");
    }
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !file) return;
    setBusy(true);
    setFieldError(undefined);
    setFormError(undefined);
    try {
      await sendProof(chargeIds, file);
      setSent(true);
      onSent?.();
    } catch (err) {
      const failure = err instanceof ApiError ? err : null;
      const onFile = failure?.fields.find((f) => f.field === "file");
      if (onFile) setFieldError(onFile.message);
      else setFormError(t(`pixProof.errors.${failure?.status ?? "generic"}`, { defaultValue: t("pixProof.errors.generic") }));
    } finally {
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <div className="flex flex-col gap-3">
        <p role="status">{t("pixProof.done")}</p>
        <div className="flex justify-end">
          <Button type="button" className="min-h-11" onClick={onClose}>
            {t("common.close")}
          </Button>
        </div>
      </div>
    );
  }
  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">{t("pixProof.effect")}</p>
      {formError && (
        <p role="alert" className="text-sm text-danger-foreground">
          {formError}
        </p>
      )}
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t("pixProof.unit")}</dt>
        <dd className="break-words">{data.unit}</dd>
        <dt className="text-muted-foreground">{t("pixProof.amount")}</dt>
        <dd className="font-medium">{formatCurrency(data.amountCents)}</dd>
        <dt className="text-muted-foreground">{t("pixProof.key")}</dt>
        <dd className="break-all font-medium">{data.pixKey}</dd>
      </dl>
      {data.charges && (
        <ul aria-label={t("pixProof.charges")} className="flex flex-col gap-1 text-sm">
          {data.charges.map((c) => (
            <li key={c.chargeId} className="flex flex-wrap justify-between gap-x-3">
              <span className="break-words">
                {c.studentName} · {formatDate(c.dueOn)}
              </span>
              <span>{formatCurrency(c.amountCents)}</span>
            </li>
          ))}
        </ul>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" className="min-h-11" onClick={() => void copyKey()}>
          {t("pixProof.copy")}
        </Button>
        {copy && <span role="status" className="text-sm">{t(copy === "copied" ? "pixProof.copied" : "pixProof.copyFailed")}</span>}
      </div>
      <div>
        <label htmlFor={fileId} className="block text-sm font-medium">
          {t("pixProof.file")}
        </label>
        <input
          id={fileId}
          type="file"
          accept="image/jpeg,image/png,image/webp,application/pdf"
          className="min-h-11 w-full text-sm"
          aria-invalid={fieldError ? true : undefined}
          aria-describedby={fieldError ? `${fileId}-erro` : undefined}
          onChange={(e) => setFile(e.target.files?.[0])}
        />
        {fieldError && (
          <p id={`${fileId}-erro`} className="text-sm text-danger-foreground">
            {fieldError}
          </p>
        )}
      </div>
      <div className="flex flex-wrap justify-end gap-2">
        <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
          {t("common.cancel")}
        </Button>
        <Button type="submit" className="min-h-11" disabled={busy || !file}>
          {t("pixProof.submit")}
        </Button>
      </div>
    </form>
  );
}

// RF-COB-05, RF-POR-05: janela "Pix na chave com comprovante" para uma ou várias cobranças da mesma unidade; o servidor confere quem paga e o arquivo.
export function PixProofDialog({ chargeIds, onClose, onSent }: Props) {
  const { t } = useTranslation();
  const { state, reload } = usePixInstructions(chargeIds);
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t("pixProof.title")}>
      {state.status === "loading" && <LoadingState />}
      {state.status === "forbidden" && <ForbiddenState />}
      {state.status === "error" && <ErrorState onRetry={reload} />}
      {state.status === "notFound" && <EmptyState title={t("pixProof.errors.404")} />}
      {state.status === "noKey" && <EmptyState title={t("pixProof.noKeyTitle")} text={t("pixProof.noKeyText")} />}
      {state.status === "badSelection" && <EmptyState title={t("pixProof.badSelectionTitle")} text={t("pixProof.badSelectionText")} />}
      {state.status === "notPayable" && <EmptyState title={t("pixProof.notPayableTitle")} text={t("pixProof.notPayableText")} />}
      {state.status === "ready" && <Form data={state.data} chargeIds={chargeIds} onClose={onClose} onSent={onSent} />}
    </Modal>
  );
}
