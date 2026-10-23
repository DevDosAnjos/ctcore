import { useState } from "react";
import { useTranslation } from "react-i18next";
import { formatMoney } from "@/components/fields/masks";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { PAYMENT_METHODS, receiptStaffPdfUrl, registerPayment, type PaymentMethod, type PaymentReceipt } from "./usePayment";

const MAX_OBSERVATION = 200;
const input = "min-h-11 w-full rounded-md border bg-background px-2";
const localDay = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;

// RF-COB-04: pagamento no balcão (meio, data, observação, recibo por e-mail). O valor é só leitura: o da cobrança.
export function RegisterPaymentDialog({ chargeId, label, amountCents, onClose, onPaid }: { chargeId: string; label: string; amountCents: number; onClose: () => void; onPaid: (p: PaymentReceipt) => void }) {
  const { t } = useTranslation();
  const k = "payments.dialog.";
  const today = localDay(new Date());
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [day, setDay] = useState(today);
  const [observation, setObservation] = useState("");
  const [sendReceipt, setSendReceipt] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [paid, setPaid] = useState<PaymentReceipt>();

  async function confirm() {
    if (busy) return;
    if (!day || day > today) return setError(t(`${k}dayInvalid`));
    setBusy(true);
    setError(undefined);
    try {
      const done = await registerPayment(chargeId, {
        method,
        amountCents,
        sendReceipt,
        ...(day === today ? {} : { paidAt: new Date(`${day}T12:00:00`).toISOString() }),
        ...(observation.trim() ? { observation: observation.trim() } : {}),
      });
      setPaid(done);
      onPaid(done);
    } catch (e) {
      const status = e instanceof ApiError ? e.status : 0;
      setError(t(status === 409 ? `${k}notPayable` : status === 400 ? `${k}invalid` : status === 422 ? `${k}amountChanged` : `${k}error`));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={t(`${k}title`)}>
      {paid ? (
        <div className="flex flex-col gap-3">
          <p role="status">{t(`${k}done`)}</p>
          <p>{t(`${k}receiptNumber`, { number: paid.receipt.number })}</p>
          <p>{t(`${k}receiptCode`, { code: paid.receipt.code })}</p>
          {paid.receiptEmail && paid.receiptEmail !== "NOT_REQUESTED" && <p>{t(`${k}email.${paid.receiptEmail}`)}</p>}
          <div className="flex flex-wrap justify-end gap-2">
            <Button asChild variant="outline" className="min-h-11">
              <a href={receiptStaffPdfUrl(paid.receipt.code)} target="_blank" rel="noreferrer">
                {t(`${k}download`)}
              </a>
            </Button>
            <Button type="button" className="min-h-11" onClick={onClose}>
              {t("common.close")}
            </Button>
          </div>
        </div>
      ) : (
        <form
          className="flex flex-col gap-3"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            void confirm();
          }}
        >
          <p>{t(`${k}charge`, { label, value: formatMoney(amountCents) })}</p>
          <div>
            <label htmlFor="payment-amount" className="block text-sm font-medium">
              {t(`${k}amount`)}
            </label>
            <input id="payment-amount" className={input} value={formatMoney(amountCents)} readOnly />
          </div>
          <div>
            <label htmlFor="payment-method" className="block text-sm font-medium">
              {t(`${k}method`)}
            </label>
            <select id="payment-method" className={input} value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              {PAYMENT_METHODS.map((m) => (
                <option key={m} value={m}>
                  {t(`payments.method.${m}`)}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="payment-day" className="block text-sm font-medium">
              {t(`${k}day`)}
            </label>
            <input id="payment-day" type="date" className={input} value={day} max={today} onChange={(e) => setDay(e.target.value)} />
          </div>
          <div>
            <label htmlFor="payment-observation" className="block text-sm font-medium">
              {t(`${k}observation`)}
            </label>
            <textarea
              id="payment-observation"
              className="min-h-20 w-full rounded-md border bg-background px-2 py-1"
              value={observation}
              maxLength={MAX_OBSERVATION}
              onChange={(e) => setObservation(e.target.value)}
              aria-describedby="payment-observation-count"
            />
            <p id="payment-observation-count" className="text-sm text-muted-foreground">
              {t(`${k}observationCount`, { count: observation.length, max: MAX_OBSERVATION })}
            </p>
          </div>
          <label className="flex min-h-11 items-center gap-2 text-sm">
            <input type="checkbox" className="size-5" checked={sendReceipt} onChange={(e) => setSendReceipt(e.target.checked)} />
            {t(`${k}sendReceipt`)}
          </label>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            <Button type="button" variant="outline" className="min-h-11" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" className="min-h-11" disabled={busy}>
              {t(busy ? `${k}sending` : `${k}confirm`)}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
