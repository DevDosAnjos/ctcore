import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";

/** Os 10 códigos, com copiar, baixar e a confirmação de que foram guardados (RN-ACE-18). */
export function BackupCodesPanel({ codes, onDone }: { codes: string[]; onDone: () => void }) {
  const { t } = useTranslation();
  const [saved, setSaved] = useState(false);
  const [copied, setCopied] = useState(false);
  const text = codes.join("\n");

  async function copy() {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }
  function download() {
    const url = URL.createObjectURL(new Blob([text + "\n"], { type: "text/plain" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = t("twoFactor.codes.fileName");
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-4">
      <p className="text-sm">{t("twoFactor.codes.warning")}</p>
      <ul aria-label={t("twoFactor.codes.list")} className="grid grid-cols-2 gap-2 font-mono">
        {codes.map((c) => (
          <li key={c} className="rounded-md bg-surface-soft px-2 py-1 text-center">
            {c}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="secondary" onClick={() => void copy()}>
          {t("twoFactor.codes.copy")}
        </Button>
        <Button type="button" variant="secondary" onClick={download}>
          {t("twoFactor.codes.download")}
        </Button>
      </div>
      {copied && (
        <Banner tone="ok">{t("twoFactor.codes.copied")}</Banner>
      )}
      <label className="flex min-h-11 items-center gap-2 text-sm">
        <input type="checkbox" className="size-5" checked={saved} onChange={(e) => setSaved(e.target.checked)} />
        {t("twoFactor.codes.saved")}
      </label>
      <Button type="button" className="w-full" disabled={!saved} onClick={onDone}>
        {t("twoFactor.codes.done")}
      </Button>
    </div>
  );
}

type Phase = "loading" | "failed" | "forbidden" | "codes";

/**
 * Janela dos códigos de reserva (canvas CodigosReserva). Com `codes`, mostra-os; sem eles, gera 10 novos ao abrir,
 * só com a sessão (os anteriores deixam de valer).
 */
export function BackupCodesDialog({ open, codes: initial, onClose }: { open: boolean; codes?: string[]; onClose: () => void }) {
  const { t } = useTranslation();
  const [codes, setCodes] = useState<string[] | null>(initial ?? null);
  const [phase, setPhase] = useState<Phase>(initial ? "codes" : "loading");
  const started = useRef(false);

  const generate = useCallback(async () => {
    setPhase("loading");
    try {
      const res = await api<{ backupCodes: string[] }>("/auth/two-factor/backup-codes", { method: "POST" });
      setCodes(res.backupCodes);
      setPhase("codes");
    } catch (err) {
      setPhase(err instanceof ApiError && err.status === 403 ? "forbidden" : "failed");
    }
  }, []);

  useEffect(() => {
    if (!open || initial || started.current) return;
    started.current = true;
    void generate();
  }, [open, initial, generate]);

  return (
    <Modal open={open} onOpenChange={(o) => !o && phase !== "codes" && onClose()} title={t("twoFactor.codes.title")} dismissible={phase !== "codes"}>
      {phase === "codes" && codes ? (
        <BackupCodesPanel codes={codes} onDone={onClose} />
      ) : phase === "forbidden" ? (
        <ForbiddenState />
      ) : phase === "failed" ? (
        <ErrorState onRetry={() => void generate()} />
      ) : (
        <LoadingState />
      )}
    </Modal>
  );
}
