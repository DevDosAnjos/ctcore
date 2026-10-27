import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import type { PaymentProof } from "./types";

// Comprovante da lista: miniatura que amplia numa janela. PDF e arquivo que não carrega viram "Abrir arquivo" ou "Imagem indisponível" (a URL assinada pode faltar).
export function ProofImage({ proof }: { proof: PaymentProof }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [broken, setBroken] = useState(false);
  const url = proof.imageUrl;
  if (!url || broken) return <span className="text-sm text-muted-foreground">{t("paymentProofs.image.unavailable")}</span>;
  if (proof.contentType === "application/pdf") {
    return (
      <a className="inline-flex min-h-11 items-center text-sm underline" href={url} target="_blank" rel="noreferrer">
        {t("paymentProofs.image.openFile")}
        <span className="sr-only"> {t("paymentProofs.image.alt", { student: proof.studentName })}</span>
      </a>
    );
  }
  return (
    <>
      <button type="button" className="inline-flex min-h-11 min-w-11 items-center rounded-md border p-1" aria-label={t("paymentProofs.image.zoom", { student: proof.studentName })} onClick={() => setOpen(true)}>
        <img src={url} alt="" className="size-14 object-cover" onError={() => setBroken(true)} />
      </button>
      {open && (
        <Modal open onOpenChange={setOpen} title={t("paymentProofs.image.viewTitle", { student: proof.studentName })} className="max-w-3xl">
          <img src={url} alt={t("paymentProofs.image.alt", { student: proof.studentName })} className="max-h-[70vh] w-full object-contain" />
        </Modal>
      )}
    </>
  );
}
