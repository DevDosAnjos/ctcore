import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Modal } from "./Modal";

// Confirmação (RNF-26): resumo, motivo (nenhum, opcional ou obrigatório) e texto digitado opcional (D4).
// O motivo e o texto não ficam guardados; vão só para onConfirm. Se onConfirm falhar, a janela segue aberta.
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  summary,
  reason = "none",
  typedText,
  confirmLabel,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  summary: string;
  reason?: "none" | "optional" | "required";
  typedText?: string;
  confirmLabel?: string;
  onConfirm: (reason: string) => void | Promise<void>;
}) {
  const { t } = useTranslation();
  const [motivo, setMotivo] = useState("");
  const [digitado, setDigitado] = useState("");
  const [busy, setBusy] = useState(false);
  const sent = useRef(false);

  const reasonOk = reason !== "required" || motivo.trim() !== "";
  const typedOk = typedText === undefined || digitado === typedText;
  const missing = !reasonOk ? t("confirm.reasonMissing") : !typedOk ? t("confirm.typeMismatch") : "";

  function reset() {
    setMotivo("");
    setDigitado("");
  }

  async function confirm() {
    if (sent.current || !reasonOk || !typedOk) return;
    sent.current = true;
    setBusy(true);
    try {
      await onConfirm(motivo.trim());
      reset();
      onOpenChange(false);
    } catch {
      // a tela que chamou mostra o erro; a janela fica aberta para tentar de novo
    } finally {
      sent.current = false;
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      onOpenChange={(o) => {
        if (!o) reset();
        onOpenChange(o);
      }}
      title={title}
    >
      <div className="flex flex-col gap-3">
        <p>{summary}</p>
        {reason !== "none" && (
          <label className="flex flex-col gap-1">
            {t("confirm.reasonLabel")}
            <textarea
              className="rounded-md border bg-background p-2"
              value={motivo}
              onChange={(e) => setMotivo(e.target.value)}
              aria-required={reason === "required"}
            />
          </label>
        )}
        {typedText !== undefined && (
          <label className="flex flex-col gap-1">
            {t("confirm.typeLabel", { text: typedText })}
            <input
              className="min-h-11 rounded-md border bg-background px-3"
              value={digitado}
              onChange={(e) => setDigitado(e.target.value)}
              autoComplete="off"
            />
          </label>
        )}
        <p role="status" className="text-sm text-muted-foreground">
          {missing}
        </p>
        <div className="flex justify-end gap-2">
          <Button variant="outline" className="min-h-11" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button className="min-h-11" disabled={!reasonOk || !typedOk || busy} onClick={confirm}>
            {confirmLabel ?? t("common.confirm")}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
