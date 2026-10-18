import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { CodeField } from "./CodeField";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

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
          <li key={c} className="rounded-md border px-2 py-1 text-center">
            {c}
          </li>
        ))}
      </ul>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" onClick={() => void copy()}>
          {t("twoFactor.codes.copy")}
        </Button>
        <Button type="button" variant="outline" onClick={download}>
          {t("twoFactor.codes.download")}
        </Button>
      </div>
      {copied && (
        <p role="status" className="text-sm text-muted-foreground">
          {t("twoFactor.codes.copied")}
        </p>
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

type Phase = "form" | "failed" | "forbidden" | "codes";

/**
 * Janela dos códigos de reserva (canvas CodigosReserva). Com `codes`, mostra-os; sem eles, pede senha
 * e código e gera 10 novos (os anteriores deixam de valer).
 */
export function BackupCodesDialog({ open, codes: initial, onClose }: { open: boolean; codes?: string[]; onClose: () => void }) {
  const { t } = useTranslation();
  const [codes, setCodes] = useState<string[] | null>(initial ?? null);
  const [phase, setPhase] = useState<Phase>(initial ? "codes" : "form");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [errors, setErrors] = useState<{ password?: string; code?: string; form?: string }>({});
  const [sending, setSending] = useState(false);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending) return;
    if (!password || !code) {
      setErrors({ form: t("twoFactor.codes.required") });
      return;
    }
    setErrors({});
    setPhase("form");
    setSending(true);
    try {
      const res = await api<{ backupCodes: string[] }>("/auth/two-factor/backup-codes", { method: "POST", body: { password, code } });
      setCodes(res.backupCodes);
      setPhase("codes");
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const apiCode = err instanceof ApiError ? err.code : "";
      if (apiCode === "INVALID_CREDENTIALS") setErrors({ password: t("twoFactor.codes.invalidPassword") });
      else if (apiCode === "INVALID_TWO_FACTOR_CODE") setErrors({ code: t("twoFactor.codes.invalidCode") });
      else if (status === 429) setErrors({ form: t("twoFactor.errors.tooMany") });
      else if (status === 403) setPhase("forbidden");
      else setPhase("failed");
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal open={open} onOpenChange={(o) => !o && phase !== "codes" && onClose()} title={t("twoFactor.codes.title")} dismissible={phase !== "codes"}>
      {phase === "codes" && codes ? (
        <BackupCodesPanel codes={codes} onDone={onClose} />
      ) : phase === "forbidden" ? (
        <ForbiddenState />
      ) : phase === "failed" ? (
        <ErrorState onRetry={() => void submit()} />
      ) : (
        <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
          <p className="text-sm">{t("twoFactor.codes.regenIntro")}</p>
          {errors.form && (
            <p role="alert" className="text-sm text-destructive">
              {errors.form}
            </p>
          )}
          <div>
            <label htmlFor="codes-password" className="block text-sm font-medium">
              {t("twoFactor.codes.password")}
            </label>
            <input
              id="codes-password"
              type="password"
              autoComplete="current-password"
              className={input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!errors.password || undefined}
              aria-describedby={errors.password ? "codes-password-erro" : undefined}
            />
            {errors.password && (
              <p id="codes-password-erro" role="alert" className="text-sm text-destructive">
                {errors.password}
              </p>
            )}
          </div>
          <CodeField id="codes-code" kind="any" label={t("twoFactor.codes.code")} value={code} onChange={setCode} error={errors.code} />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={sending}>
              {sending ? t("twoFactor.codes.sending") : t("twoFactor.codes.submit")}
            </Button>
            <Button type="button" variant="ghost" onClick={onClose}>
              {t("common.cancel")}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
