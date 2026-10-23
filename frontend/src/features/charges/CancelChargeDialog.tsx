import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { CharCounter } from "@/components/CharCounter";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { formatCurrency, formatDate } from "@/lib/format";
import type { ChargeRow } from "./types";
import { cancelCharge } from "./useChargeActions";

export type Failure = { fields: Record<string, string>; form: string };
export const FieldError = ({ message }: { message?: string }) => (message ? <p className="text-sm text-destructive">{message}</p> : null);
export const NO_FAILURE: Failure = { fields: {}, form: "" };

// Erro do servidor de uma janela de ação: o 400 vai junto do campo; 409, 404 e o resto viram a mensagem do alto.
export function failureOf(err: unknown, t: (k: string) => string): Failure {
  const e = err instanceof ApiError ? err : null;
  const fields = Object.fromEntries((e?.fields ?? []).map((f) => [f.field, f.message]));
  return { fields, form: e?.fields.length ? "" : t(e?.status === 409 ? "charges.actions.conflict" : e?.status === 404 ? "charges.actions.gone" : "charges.actions.error") };
}

// RN-COB-10: cancela a cobrança em aberto com motivo obrigatório (até 200).
export function CancelChargeDialog({ charge, onClose, onDone }: { charge: ChargeRow; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<Failure>(NO_FAILURE);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !reason.trim()) return;
    setBusy(true);
    setErr(NO_FAILURE);
    try {
      await cancelCharge(charge.id, reason.trim());
      onDone();
      onClose();
    } catch (x) {
      setErr(failureOf(x, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("charges.cancel.title")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p>{t("charges.cancel.summary", { student: charge.student.name, amount: formatCurrency(charge.amountCents), due: formatDate(charge.dueOn) })}</p>
        <p className="text-sm text-muted-foreground">{t("charges.cancel.effect")}</p>
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
          <Button type="submit" className="min-h-11" disabled={busy || !reason.trim()}>
            {t("charges.cancel.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
