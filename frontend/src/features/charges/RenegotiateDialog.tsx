import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { CharCounter } from "@/components/CharCounter";
import { MoneyField } from "@/components/fields/fields";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDate } from "@/lib/format";
import { failureOf, FieldError, NO_FAILURE, type Failure } from "./CancelChargeDialog";
import type { ChargeRow } from "./types";
import { renegotiateCharges } from "./useChargeActions";

// Parcelas mensais a partir de `first` (dia mantido, limitado ao último dia do mês); o resto dos centavos vai para a primeira (RN-COB-09).
export function previewInstallments(total: number, n: number, first: string) {
  const [y, m, d] = first.split("-").map(Number);
  const base = Math.floor(total / n);
  return Array.from({ length: n }, (_, i) => {
    const month = new Date(Date.UTC(y, m - 1 + i, 1));
    const last = new Date(Date.UTC(y, m + i, 0)).getUTCDate();
    const dueOn = `${month.getUTCFullYear()}-${String(month.getUTCMonth() + 1).padStart(2, "0")}-${String(Math.min(d, last)).padStart(2, "0")}`;
    return { amountCents: i === 0 ? total - base * (n - 1) : base, dueOn };
  });
}

// RN-COB-09: renegocia as cobranças em aberto do mesmo aluno; o total só é editável com uma original.
export function RenegotiateDialog({ charges, onClose, onDone }: { charges: ChargeRow[]; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation();
  const sum = charges.reduce((s, c) => s + c.amountCents, 0);
  const single = charges.length === 1;
  const [total, setTotal] = useState<number | null>(sum);
  const [firstDueOn, setFirstDueOn] = useState("");
  const [installments, setInstallments] = useState(1);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<Failure>(NO_FAILURE);
  const amount = single ? total : sum;
  const valid = !!amount && amount >= installments && !!firstDueOn && !!reason.trim();
  const rows = amount && firstDueOn ? previewInstallments(amount, installments, firstDueOn) : [];

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (busy || !valid) return;
    setBusy(true);
    setErr(NO_FAILURE);
    try {
      await renegotiateCharges({ chargeIds: charges.map((c) => c.id), ...(single && amount !== sum ? { amountCents: amount! } : {}), firstDueOn, installments, reason: reason.trim() });
      onDone();
      onClose();
    } catch (x) {
      setErr(failureOf(x, t));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !busy && onClose()} title={t("charges.renegotiate.title")}>
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <p className="text-sm text-muted-foreground">{t("charges.renegotiate.effect")}</p>
        {err.form && (
          <p role="alert" className="text-sm text-destructive">
            {err.form}
          </p>
        )}
        <ul className="flex flex-col gap-1 text-sm">
          {charges.map((c) => (
            <li key={c.id}>{t("charges.renegotiate.original", { student: c.student.name, amount: formatCurrency(c.amountCents), due: formatDate(c.dueOn) })}</li>
          ))}
        </ul>
        <MoneyField label={t("charges.renegotiate.total")} initial={sum} onChange={setTotal} disabled={!single} />
        {!single && <p className="text-sm text-muted-foreground">{t("charges.renegotiate.totalLocked")}</p>}
        <FieldError message={err.fields.amountCents} />
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.renegotiate.firstDueOn")}
          <input type="date" className="min-h-11 rounded-md border bg-background px-2 font-normal" value={firstDueOn} onChange={(e) => setFirstDueOn(e.target.value)} aria-invalid={err.fields.firstDueOn ? true : undefined} />
        </label>
        <FieldError message={err.fields.firstDueOn} />
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.renegotiate.installments")}
          <select className="min-h-11 rounded-md border bg-background px-2 font-normal" value={installments} onChange={(e) => setInstallments(Number(e.target.value))}>
            {[1, 2, 3, 4, 5, 6].map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </label>
        <FieldError message={err.fields.installments} />
        {rows.length > 0 && (
          <div>
            <p className="text-sm font-medium">{t("charges.renegotiate.preview")}</p>
            <ol className="text-sm">
              {rows.map((r, i) => (
                <li key={i}>{t("charges.renegotiate.line", { n: i + 1, amount: formatCurrency(r.amountCents), due: formatDate(r.dueOn) })}</li>
              ))}
            </ol>
            <p className="text-sm font-medium">{t("charges.renegotiate.sum", { amount: formatCurrency(rows.reduce((s, r) => s + r.amountCents, 0)) })}</p>
          </div>
        )}
        <label className="flex flex-col gap-1 text-sm font-medium">
          {t("charges.actions.reason")}
          <CharCounter value={reason} max={200} onChange={setReason} aria-required="true" className="w-full rounded-md border bg-background p-2 font-normal" />
        </label>
        <FieldError message={err.fields.reason} />
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" className="min-h-11" disabled={busy || !valid}>
            {t("charges.renegotiate.submit")}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
