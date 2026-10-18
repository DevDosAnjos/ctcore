import { useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { Button } from "@/components/ui/button";
import { PasswordFields } from "@/features/auth/PasswordFields";
import { isStrongPassword } from "@/features/auth/PasswordRules";
import { ApiError, api } from "@/lib/api";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

// RF-ACE-04 / RN-ACE-20: a troca encerra as outras sessões; a tela avisa a lista para reler.
export function ChangePasswordForm({ onChanged }: { onChanged: () => void }) {
  const { t } = useTranslation();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [currentError, setCurrentError] = useState<string | null>(null);
  const [newError, setNewError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);
  const [sending, setSending] = useState(false);
  const currentRef = useRef<HTMLInputElement>(null);
  const newRef = useRef<HTMLInputElement>(null);
  const valid = current !== "" && isStrongPassword(password) && password === confirm;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (sending || !valid) return;
    setCurrentError(null);
    setNewError(null);
    setFormError(null);
    setDone(false);
    setSending(true);
    try {
      await api<void>("/auth/password", { method: "POST", body: { currentPassword: current, newPassword: password } });
      setCurrent("");
      setPassword("");
      setConfirm("");
      setDone(true);
      onChanged();
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const code = err instanceof ApiError ? err.code : "";
      if (code === "PASSWORD_LEAKED" || code === "PASSWORD_WEAK") {
        setNewError(t(code === "PASSWORD_LEAKED" ? "password.leaked" : "password.weak"));
        newRef.current?.focus();
      } else if (status === 400) {
        setCurrentError(t("account.password.currentWrong"));
        currentRef.current?.focus();
      } else if (status === 429) setFormError(t("twoFactor.errors.tooMany"));
      else setFormError(t("twoFactor.errors.failed"));
    } finally {
      setSending(false);
    }
  }

  return (
    <Card title={t("account.password.title")} heading="h2">
      <form noValidate onSubmit={submit} className="max-w-md space-y-4" aria-busy={sending}>
        <div>
          <label htmlFor="acc-atual" className="block text-sm font-medium">
            {t("account.password.current")}
          </label>
          <input
            id="acc-atual"
            ref={currentRef}
            type="password"
            autoComplete="current-password"
            className={input}
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            aria-invalid={!!currentError || undefined}
            aria-describedby={currentError ? "acc-atual-erro" : undefined}
          />
          {currentError && (
            <p id="acc-atual-erro" role="alert" className="text-sm text-destructive">
              {currentError}
            </p>
          )}
        </div>
        <PasswordFields password={password} confirm={confirm} onPassword={setPassword} onConfirm={setConfirm} error={newError} inputRef={newRef} idPrefix="acc" />
        {formError && (
          <p role="alert" className="text-sm text-destructive">
            {formError}
          </p>
        )}
        {done && (
          <p role="status" className="text-sm">
            {t("account.password.done")}
          </p>
        )}
        <Button type="submit" disabled={sending || !valid}>
          {sending ? t("account.password.sending") : t("account.password.submit")}
        </Button>
      </form>
    </Card>
  );
}
