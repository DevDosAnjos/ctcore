import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { ModalityDialog } from "@/features/students/ModalityPickers";
import { formatCurrency } from "@/lib/format";
import type { PaymentProof } from "./types";
import { confirmProof, refuseProof } from "./usePaymentProofs";

type Props = { proof: PaymentProof; onClose: () => void; onDone: () => void };

// RN-COB-13: Confirmar vira pagamento com recibo; o e-mail do recibo é opção da gestão (padrão marcado, como no balcão).
export function ConfirmProofDialog({ proof, onClose, onDone }: Props) {
  const { t } = useTranslation();
  const [sendReceipt, setSendReceipt] = useState(true);
  return (
    <ModalityDialog
      title={t("paymentProofs.confirm.title")}
      effect={t("paymentProofs.confirm.effect")}
      state="ready"
      submitLabel={t("paymentProofs.confirm.submit")}
      canSubmit
      work={() => confirmProof(proof.id, sendReceipt)}
      onClose={onClose}
      onDone={onDone}
      errorPrefix="paymentProofs.errors."
    >
      {() => (
        <>
          <p className="break-words text-sm font-medium">{t("paymentProofs.confirm.who", { student: proof.studentName, reference: proof.reference, amount: formatCurrency(proof.amountCents) })}</p>
          <label className="flex min-h-11 items-center gap-2 text-sm">
            <input type="checkbox" className="size-5" checked={sendReceipt} onChange={(e) => setSendReceipt(e.target.checked)} />
            {t("paymentProofs.confirm.sendReceipt")}
          </label>
        </>
      )}
    </ModalityDialog>
  );
}

// RN-COB-13: Recusar exige motivo (3 a 200); o aluno recebe o motivo por e-mail e a cobrança volta a aberta.
export function RefuseProofDialog({ proof, onClose, onDone }: Props) {
  const { t } = useTranslation();
  const id = useId();
  const [reason, setReason] = useState("");
  const trimmed = reason.trim();
  return (
    <ModalityDialog
      title={t("paymentProofs.refuse.title")}
      effect={t("paymentProofs.refuse.effect")}
      state="ready"
      submitLabel={t("paymentProofs.refuse.submit")}
      canSubmit={trimmed.length >= 3}
      work={() => refuseProof(proof.id, trimmed)}
      onClose={onClose}
      onDone={onDone}
      errorPrefix="paymentProofs.errors."
    >
      {(errors) => (
        <>
          <p className="break-words text-sm font-medium">{t("paymentProofs.confirm.who", { student: proof.studentName, reference: proof.reference, amount: formatCurrency(proof.amountCents) })}</p>
          <div>
            <label htmlFor={id} className="block text-sm font-medium">
              {t("paymentProofs.refuse.reason")}
            </label>
            <textarea
              id={id}
              className="w-full rounded-md border bg-background p-2"
              rows={3}
              maxLength={200}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              aria-invalid={errors.reason ? true : undefined}
              aria-describedby={errors.reason ? `${id}-erro` : undefined}
            />
            {errors.reason && (
              <p id={`${id}-erro`} className="text-sm text-destructive">
                {errors.reason}
              </p>
            )}
          </div>
        </>
      )}
    </ModalityDialog>
  );
}
