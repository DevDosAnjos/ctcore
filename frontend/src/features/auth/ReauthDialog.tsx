import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { ErrorState, ForbiddenState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ApiError, api } from "@/lib/api";
import { CodeField } from "./CodeField";
import { useSession } from "./SessionProvider";

export type CriticalAction =
  | "TRANSFER_COORDINATION"
  | "CANCEL_SUBSCRIPTION"
  | "FULL_EXPORT"
  | "DISCONNECT_MERCADO_PAGO"
  | "ANONYMIZE_STUDENT"
  | "DEACTIVATE_UNIT";

/**
 * Confirmação de ação crítica (RN-ACE-21): pede a senha e, com as duas etapas ligadas, o código.
 * Devolve o token de uso único (5 minutos) à tela que chamou; ela o manda em `X-Reauth-Token`.
 */
export function ReauthDialog({
  open,
  action,
  onClose,
  onConfirmed,
}: {
  open: boolean;
  action: CriticalAction;
  onClose: () => void;
  onConfirmed: (reauthToken: string) => void;
}) {
  const { t } = useTranslation();
  const { state } = useSession();
  const withCode = state.status === "ready" && state.session.twoFactor.enabled;
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [errors, setErrors] = useState<{ password?: string; code?: string; form?: string }>({});
  const [sending, setSending] = useState(false);
  const [phase, setPhase] = useState<"form" | "failed" | "forbidden">("form");

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending) return;
    if (!password || (withCode && !code)) {
      setErrors({ form: t("reauth.required") });
      return;
    }
    setErrors({});
    setPhase("form");
    setSending(true);
    try {
      const res = await api<{ reauthToken: string }>("/auth/reauth", {
        method: "POST",
        body: { action, password, ...(withCode ? { code } : {}) },
      });
      onConfirmed(res.reauthToken);
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

  function close() {
    setPassword("");
    setCode("");
    setErrors({});
    setPhase("form");
    onClose();
  }

  return (
    <Modal open={open} onOpenChange={(o) => !o && !sending && close()} title={t("reauth.title")}>
      {phase === "forbidden" ? (
        <ForbiddenState />
      ) : phase === "failed" ? (
        <ErrorState onRetry={() => void submit()} />
      ) : (
        <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
          <p className="text-sm">{t(withCode ? "reauth.introCode" : "reauth.introPassword")}</p>
          {errors.form && (
            <Banner tone="danger">{errors.form}</Banner>
          )}
          <Field label={t("reauth.password")} error={errors.password} alertError>
            {(c) => <Input {...c} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
          </Field>
          {withCode && <CodeField id="reauth-code" kind="any" label={t("reauth.code")} value={code} onChange={setCode} error={errors.code} />}
          <div className="flex flex-wrap gap-2">
            <Button type="submit" disabled={sending}>
              {sending ? t("reauth.sending") : t("reauth.submit")}
            </Button>
            <Button type="button" variant="ghost" onClick={close}>
              {t("common.cancel")}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  );
}
