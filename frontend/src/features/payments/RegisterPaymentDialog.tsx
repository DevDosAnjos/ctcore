import { useState } from "react";
import { useTranslation } from "react-i18next";
import { formatMoney } from "@/components/fields/masks";
import { Modal } from "@/components/Modal";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { PAYMENT_METHODS, registerPayment, type PaymentMethod, type PaymentReceipt } from "./usePayment";

// Janela mínima de pagamento no balcão (meio e confirmar); a #75 a amplia com data, observação e recibo por e-mail.
export function RegisterPaymentDialog({ chargeId, label, amountCents, onClose, onPaid }: { chargeId: string; label: string; amountCents: number; onClose: () => void; onPaid: (p: PaymentReceipt) => void }) {
  const { t } = useTranslation();
  const k = "payments.dialog.";
  const [method, setMethod] = useState<PaymentMethod>("CASH");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();
  const [paid, setPaid] = useState<PaymentReceipt>();

  async function confirm() {
    if (busy) return;
    setBusy(true);
    setError(undefined);
    try {
      const done = await registerPayment(chargeId, method);
      setPaid(done);
      onPaid(done);
    } catch (e) {
      setError(t(e instanceof ApiError && e.status === 409 ? `${k}notPayable` : `${k}error`));
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
          <div className="flex justify-end">
            <Button type="button" className="min-h-11" onClick={onClose}>
              {t("common.close")}
            </Button>
          </div>
        </div>
      ) : (
        <form
          className="flex flex-col gap-3"
          onSubmit={(e) => {
            e.preventDefault();
            void confirm();
          }}
        >
          <p>{t(`${k}charge`, { label, value: formatMoney(amountCents) })}</p>
          <div>
            <label htmlFor="payment-method" className="block text-sm font-medium">
              {t(`${k}method`)}
            </label>
            <select id="payment-method" className="min-h-11 w-full rounded-md border bg-background px-2" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
              {PAYMENT_METHODS.map((m) => (
                <option key={m} value={m}>
                  {t(`payments.method.${m}`)}
                </option>
              ))}
            </select>
          </div>
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
              {t(`${k}confirm`)}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
