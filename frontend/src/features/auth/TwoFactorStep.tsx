import { useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { CodeField } from "./CodeField";
import type { SessionView } from "./SessionProvider";

// RF-ACE-01 (canvas LoginDuasEtapas): segundo passo do login com o bilhete devolvido pela senha certa.
export function TwoFactorStep({
  ticket,
  onDone,
  onBack,
  onExpired,
}: {
  ticket: string;
  onDone: (session: SessionView) => void;
  onBack: () => void;
  onExpired: () => void;
}) {
  const { t } = useTranslation();
  const [code, setCode] = useState("");
  const [backup, setBackup] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [failed, setFailed] = useState(false);
  const ref = useRef<HTMLInputElement>(null);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending) return;
    if (!code) {
      setError(t("twoFactor.login.required"));
      return;
    }
    setError(null);
    setFailed(false);
    setSending(true);
    try {
      onDone(await api<SessionView>("/auth/login/two-factor", { method: "POST", body: { ticket, code } }));
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const apiCode = err instanceof ApiError ? err.code : "";
      if (apiCode === "TWO_FACTOR_TICKET_INVALID") onExpired();
      else if (apiCode === "INVALID_TWO_FACTOR_CODE") {
        setError(t("twoFactor.login.invalid"));
        ref.current?.focus();
      } else if (status === 429) setError(t("twoFactor.errors.tooMany"));
      else setFailed(true);
    } finally {
      setSending(false);
    }
  }

  if (failed) return <ErrorState onRetry={() => void submit()} />;
  return (
    <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
      <p className="text-sm text-muted-foreground">{t(backup ? "twoFactor.login.introBackup" : "twoFactor.login.intro")}</p>
      <CodeField
        id="login-code"
        key={backup ? "backup" : "totp"}
        inputRef={ref}
        label={t(backup ? "twoFactor.login.backupLabel" : "twoFactor.login.codeLabel")}
        kind={backup ? "any" : "totp"}
        value={code}
        onChange={setCode}
        error={error}
      />
      <Button type="submit" size="lg" className="w-full" disabled={sending}>
        {sending ? t("twoFactor.login.sending") : t("twoFactor.login.submit")}
      </Button>
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            setBackup((b) => !b);
            setCode("");
            setError(null);
          }}
        >
          {t(backup ? "twoFactor.login.useApp" : "twoFactor.login.useBackup")}
        </Button>
        <Button type="button" variant="ghost" onClick={onBack}>
          {t("twoFactor.login.back")}
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">{t("twoFactor.login.lost")}</p>
    </form>
  );
}
