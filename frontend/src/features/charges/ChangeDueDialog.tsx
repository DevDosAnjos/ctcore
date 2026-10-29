import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { Banner } from "@/components/ui/banner";
import { Input } from "@/components/ui/field";
import { Button } from "@/components/ui/button";
import { DEFAULT_TZ, formatDate } from "@/lib/format";
import { FieldError, NO_FAILURE, failureOf, type Failure } from "./CancelChargeDialog";
import type { PreviewLine } from "./types";
import { changeNextDue } from "./useChargePreview";

export const todayIso = () => new Date().toLocaleDateString("sv-SE", { timeZone: DEFAULT_TZ });
const monthEnd = (ref: string) => new Date(Date.UTC(Number(ref.slice(0, 4)), Number(ref.slice(5, 7)), 0)).toISOString().slice(0, 10);

// RN-COB-02: troca o vencimento só desta mensalidade, de hoje até o último dia do mês de referência.
export function ChangeDueDialog({ line, dueOn: current, onClose, onDone }: { line: PreviewLine; dueOn: string; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [dueOn, setDueOn] = useState(current);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<Failure>(NO_FAILURE);
  const max = monthEnd(line.referenceMonth);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !dueOn) return;
    setBusy(true);
    setErr(NO_FAILURE);
    try {
      await changeNextDue(line.studentId, line.modalityRowId, dueOn);
      onDone();
      onClose();
    } catch (x) {
      setErr(failureOf(x, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("charges.preview.changeDue.title")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p>{t("charges.preview.changeDue.summary", { student: line.student, modality: line.modality })}</p>
        <p className="text-sm text-muted-foreground">{t("charges.preview.changeDue.effect", { max: formatDate(max) })}</p>
        {err.form && (
          <Banner tone="danger">{err.form}</Banner>
        )}
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.preview.changeDue.field")}
          <Input type="date" min={todayIso()} max={max} value={dueOn} onChange={(e) => setDueOn(e.target.value)} aria-invalid={err.fields.dueOn ? true : undefined} />
        </label>
        <FieldError message={err.fields.dueOn} />
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy || !dueOn}>
            {t("charges.preview.changeDue.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
