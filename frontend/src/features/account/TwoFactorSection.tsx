import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { BackupCodesDialog } from "@/features/auth/BackupCodesDialog";
import { CodeField } from "@/features/auth/CodeField";
import { useSession } from "@/features/auth/SessionProvider";
import { TwoFactorSetupDialog } from "@/features/auth/TwoFactorSetupDialog";
import { ApiError, api } from "@/lib/api";
import { useReloadSession } from "./useAccount";

type Dialog = "setup" | "codes" | "disable" | null;

// RN-ACE-17: quem é obrigado (Coordenador) não vê "Desligar"; o servidor também recusa (403).
export function TwoFactorSection() {
  const { t } = useTranslation();
  const { state } = useSession();
  const reload = useReloadSession();
  const [dialog, setDialog] = useState<Dialog>(null);
  const [failed, setFailed] = useState(false);
  if (state.status !== "ready") return null;
  const { enabled, required } = state.session.twoFactor;

  async function changed() {
    setDialog(null);
    setFailed(false);
    try {
      await reload();
    } catch {
      setFailed(true);
    }
  }

  return (
    <Card title={t("account.twoFactor.title")} heading="h2">
      <div className="space-y-4">
        <p role="status" className="text-sm">
          {t(enabled ? "account.twoFactor.on" : "account.twoFactor.off")}
        </p>
        {failed && <ErrorState onRetry={() => void changed()} />}
        <div className="flex flex-wrap gap-2">
          {!enabled && <Button onClick={() => setDialog("setup")}>{t("account.twoFactor.enable")}</Button>}
          {enabled && (
            <Button variant="outline" onClick={() => setDialog("codes")}>
              {t("account.twoFactor.newCodes")}
            </Button>
          )}
          {enabled && !required && (
            <Button variant="outline" onClick={() => setDialog("disable")}>
              {t("account.twoFactor.disable")}
            </Button>
          )}
        </div>
      </div>
      {dialog === "setup" && <TwoFactorSetupDialog open onClose={() => setDialog(null)} onEnabled={() => void changed()} />}
      {dialog === "codes" && <BackupCodesDialog open onClose={() => setDialog(null)} />}
      {dialog === "disable" && <DisableDialog onClose={() => setDialog(null)} onDisabled={() => void changed()} />}
    </Card>
  );
}

function DisableDialog({ onClose, onDisabled }: { onClose: () => void; onDisabled: () => void }) {
  const { t } = useTranslation();
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [errors, setErrors] = useState<{ password?: string; code?: string; form?: string }>({});
  const [sending, setSending] = useState(false);
  const [phase, setPhase] = useState<"form" | "failed" | "forbidden">("form");

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
      await api<void>("/auth/two-factor", { method: "DELETE", body: { password, code } });
      onDisabled();
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const apiCode = err instanceof ApiError ? err.code : "";
      if (apiCode === "INVALID_CREDENTIALS") setErrors({ password: t("reauth.invalidPassword") });
      else if (apiCode === "INVALID_TWO_FACTOR_CODE") setErrors({ code: t("reauth.invalidCode") });
      else if (status === 429) setErrors({ form: t("twoFactor.errors.tooMany") });
      else if (status === 403) setPhase("forbidden");
      else setPhase("failed");
    } finally {
      setSending(false);
    }
  }

  return (
    <Modal open onOpenChange={(o) => !o && !sending && onClose()} title={t("account.twoFactor.disableTitle")}>
      {phase === "forbidden" ? (
        <ForbiddenState />
      ) : phase === "failed" ? (
        <ErrorState onRetry={() => void submit()} />
      ) : (
        <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
          <p className="text-sm">{t("account.twoFactor.disableIntro")}</p>
          {errors.form && (
            <p role="alert" className="text-sm text-destructive">
              {errors.form}
            </p>
          )}
          <div>
            <label htmlFor="off-password" className="block text-sm font-medium">
              {t("reauth.password")}
            </label>
            <input
              id="off-password"
              type="password"
              autoComplete="current-password"
              className="min-h-11 w-full rounded-md border bg-background px-2"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!errors.password || undefined}
              aria-describedby={errors.password ? "off-password-erro" : undefined}
            />
            {errors.password && (
              <p id="off-password-erro" role="alert" className="text-sm text-destructive">
                {errors.password}
              </p>
            )}
          </div>
          <CodeField id="off-code" kind="any" label={t("reauth.code")} value={code} onChange={setCode} error={errors.code} />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={sending}>
              {sending ? t("account.twoFactor.disabling") : t("account.twoFactor.disableSubmit")}
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
