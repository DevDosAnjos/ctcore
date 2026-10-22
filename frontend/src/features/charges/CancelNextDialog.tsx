import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { CharCounter } from "@/components/CharCounter";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { formatCurrency } from "@/lib/format";
import { FieldError, NO_FAILURE, failureOf, type Failure } from "./CancelChargeDialog";
import type { PreviewLine } from "./types";
import { cancelNext } from "./useChargePreview";

// RN-COB-02, RN-COB-10: cancela a mensalidade do mês antes de gerar; motivo obrigatório (até 200). Envia o mês mostrado para duas pessoas não cancelarem dois meses.
export function CancelNextDialog({ line, onClose, onDone }: { line: PreviewLine; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<Failure>(NO_FAILURE);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy) return;
    if (!reason.trim()) {
      setErr({ fields: { reason: t("confirm.reasonMissing") }, form: "" });
      return;
    }
    setBusy(true);
    setErr(NO_FAILURE);
    try {
      await cancelNext(line.studentId, line.modalityRowId, reason.trim(), line.referenceMonth);
      onDone();
      onClose();
    } catch (x) {
      setErr(failureOf(x, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("charges.preview.cancel.title")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p>{t("charges.preview.cancel.summary", { student: line.student, modality: line.modality, amount: formatCurrency(line.amountCents) })}</p>
        <p className="text-sm text-muted-foreground">{t("charges.preview.cancel.effect")}</p>
        {err.form && (
          <p role="alert" className="text-sm text-destructive">
            {err.form}
          </p>
        )}
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.actions.reason")}
          <CharCounter value={reason} max={200} onChange={setReason} aria-required="true" aria-invalid={err.fields.reason ? true : undefined} className="w-full rounded-md border bg-background p-2 font-normal" />
        </label>
        <FieldError message={err.fields.reason} />
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy}>
            {t("charges.preview.cancel.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
