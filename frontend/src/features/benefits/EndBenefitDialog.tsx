import { useId, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { EmptyState, ErrorState, ForbiddenState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { ApiError } from "@/lib/api";
import type { BenefitRow } from "./useBenefits";
import { endBenefit } from "./useGrantBenefit";

type Outcome = "form" | "error" | "forbidden" | "gone";

// RF-BEN-03: encerrar o benefício com motivo. O servidor decide (400 no campo, 403, 409 se já encerrado por outra pessoa); a barreira é dele.
export function EndBenefitDialog({ benefit, onClose, onDone }: { benefit: BenefitRow; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const k = "benefits.end.";
  const id = useId();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [outcome, setOutcome] = useState<Outcome>("form");
  const [fieldError, setFieldError] = useState<string>();
  const canSubmit = reason.trim().length >= 2 && !busy;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    setBusy(true);
    setFieldError(undefined);
    try {
      await endBenefit(benefit.id, reason.trim());
      onDone();
      onClose();
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      if (status === 400 && err instanceof ApiError && err.fields.length) setFieldError(err.fields[0].message);
      else if (status === 403) setOutcome("forbidden");
      else if (status === 409) {
        setOutcome("gone");
        onDone();
      } else setOutcome("error");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t(`${k}title`)}>
      {outcome === "forbidden" && <ForbiddenState />}
      {outcome === "error" && <ErrorState onRetry={() => setOutcome("form")} />}
      {outcome === "gone" && <EmptyState title={t(`${k}goneTitle`)} text={t(`${k}goneText`)} action={<Button type="button" className="min-h-11" onClick={onClose}>{t("common.close")}</Button>} />}
      {outcome === "form" && (
        <form onSubmit={submit} noValidate className="flex flex-col gap-3">
          <p className="text-sm">{t(`${k}summary`, { student: benefit.student.name, modality: benefit.modality.name })}</p>
          <p className="text-sm text-muted-foreground">{t(`${k}effect`)}</p>
          <div>
            <label htmlFor={id} className="block text-sm font-medium">
              {t(`${k}reason`)}
            </label>
            <Input
              id={id}
              maxLength={200}
              value={reason}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={fieldError ? `${id}-erro` : undefined}
              onChange={(e) => setReason(e.target.value)}
            />
            {fieldError && (
              <p id={`${id}-erro`} className="text-sm text-danger-foreground">
                {fieldError}
              </p>
            )}
          </div>
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" className="min-h-11" disabled={!canSubmit}>
              {busy ? t(`${k}sending`) : t(`${k}submit`)}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
