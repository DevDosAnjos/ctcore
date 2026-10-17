import { useEffect, useState, type FormEvent } from "react";
import QRCode from "qrcode";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { BackupCodesPanel } from "./BackupCodesDialog";
import { CodeField } from "./CodeField";

type Setup = { secret: string; otpauthUri: string };
type Phase =
  | { name: "loading" }
  | { name: "failed" }
  | { name: "forbidden" }
  | { name: "form"; setup: Setup; qr: string }
  | { name: "codes"; codes: string[] };

/** Janela "Ativar duas etapas" (canvas DuasEtapas): QR e chave, código, e por fim os códigos de reserva. */
export function TwoFactorSetupDialog({ open, onClose, onEnabled }: { open: boolean; onClose: () => void; onEnabled: () => void }) {
  const { t } = useTranslation();
  return (
    <Modal open={open} onOpenChange={(o) => !o && onClose()} title={t("twoFactor.setup.title")}>
      <Body onClose={onClose} onEnabled={onEnabled} />
    </Modal>
  );
}

// Só existe com a janela aberta: cada abertura gera um segredo novo (o servidor substitui o pendente).
function Body({ onClose, onEnabled }: { onClose: () => void; onEnabled: () => void }) {
  const { t } = useTranslation();
  const [phase, setPhase] = useState<Phase>({ name: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    let live = true;
    api<Setup>("/auth/two-factor/setup", { method: "POST" })
      .then(async (setup) => {
        const qr = await QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 192 });
        if (live) setPhase({ name: "form", setup, qr });
      })
      .catch((err) => {
        if (live) setPhase(err instanceof ApiError && err.status === 403 ? { name: "forbidden" } : { name: "failed" });
      });
    return () => {
      live = false;
    };
  }, [attempt]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (sending) return;
    if (code.length !== 6) {
      setError(t("twoFactor.setup.codeRequired"));
      return;
    }
    setError(null);
    setSending(true);
    try {
      const res = await api<{ backupCodes: string[] }>("/auth/two-factor/enable", { method: "POST", body: { code } });
      setPhase({ name: "codes", codes: res.backupCodes });
    } catch (err) {
      if (err instanceof ApiError && err.code === "INVALID_TWO_FACTOR_CODE") setError(t("twoFactor.setup.invalid"));
      else if (err instanceof ApiError && err.status === 429) setError(t("twoFactor.errors.tooMany"));
      else setError(t("twoFactor.errors.failed"));
    } finally {
      setSending(false);
    }
  }

  switch (phase.name) {
    case "loading":
      return <LoadingState text={t("twoFactor.setup.loading")} />;
    case "forbidden":
      return <ForbiddenState />;
    case "failed":
      return (
        <ErrorState
          onRetry={() => {
            setPhase({ name: "loading" });
            setAttempt((a) => a + 1);
          }}
        />
      );
    case "codes":
      return <BackupCodesPanel codes={phase.codes} onDone={onEnabled} />;
    case "form":
      return (
        <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
          <p className="text-sm">{t("twoFactor.setup.intro")}</p>
          <img src={phase.qr} width={192} height={192} alt={t("twoFactor.setup.qrAlt")} className="mx-auto rounded-md bg-white" />
          <div>
            <p className="text-sm font-medium">{t("twoFactor.setup.keyLabel")}</p>
            <p className="break-all font-mono text-sm">{phase.setup.secret}</p>
          </div>
          <CodeField id="setup-code" label={t("twoFactor.setup.codeLabel")} value={code} onChange={setCode} error={error} />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={sending}>
              {sending ? t("twoFactor.setup.sending") : t("twoFactor.setup.submit")}
            </Button>
            <Button type="button" variant="ghost" onClick={onClose}>
              {t("common.cancel")}
            </Button>
          </div>
        </form>
      );
  }
}
