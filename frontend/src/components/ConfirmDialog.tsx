import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Field, Input, Textarea } from "@/components/ui/field";
import { SummaryBox } from "@/components/ui/summary-box";
import { Modal } from "./Modal";

// Confirmação (RNF-26): resumo, motivo (nenhum, opcional ou obrigatório) e texto digitado opcional (D4).
// O motivo e o texto não ficam guardados; vão só para onConfirm. Se onConfirm falhar, a janela segue aberta.
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  summary,
  summaryItems,
  reason = "none",
  typedText,
  confirmLabel,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  summary: string;
  summaryItems?: string[];
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

  // Todo fechamento (Cancelar, Esc, X, clique fora) limpa motivo e texto digitado.
  function handleOpenChange(o: boolean) {
    if (!o) reset();
    onOpenChange(o);
  }

  return (
    <Modal
      open={open}
      onOpenChange={handleOpenChange}
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={() => handleOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button disabled={!reasonOk || !typedOk || busy} onClick={confirm}>
            {confirmLabel ?? t("common.confirm")}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <p>{summary}</p>
        {summaryItems && <SummaryBox items={summaryItems} />}
        {reason !== "none" && (
          <Field label={t("confirm.reasonLabel")}>
            {(c) => <Textarea {...c} aria-required={reason === "required" || undefined} value={motivo} onChange={(e) => setMotivo(e.target.value)} />}
          </Field>
        )}
        {typedText !== undefined && (
          <Field label={t("confirm.typeLabel", { text: typedText })}>
            {(c) => <Input {...c} value={digitado} onChange={(e) => setDigitado(e.target.value)} autoComplete="off" />}
          </Field>
        )}
        <p role="status" className="text-sm text-muted-foreground">
          {missing}
        </p>
      </div>
    </Modal>

  );
}
