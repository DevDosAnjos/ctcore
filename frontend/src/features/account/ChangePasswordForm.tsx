import { useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { PasswordFields } from "@/features/auth/PasswordFields";
import { isStrongPassword } from "@/features/auth/PasswordRules";
import { ApiError, api } from "@/lib/api";

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
  const valid =
    current !== "" && isStrongPassword(password) && password === confirm;

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (sending || !valid) return;
    setCurrentError(null);
    setNewError(null);
    setFormError(null);
    setDone(false);
    setSending(true);
    try {
      await api<void>("/auth/password", {
        method: "POST",
        body: { currentPassword: current, newPassword: password },
      });
      setCurrent("");
      setPassword("");
      setConfirm("");
      setDone(true);
      onChanged();
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const code = err instanceof ApiError ? err.code : "";
      if (code === "PASSWORD_LEAKED" || code === "PASSWORD_WEAK") {
        setNewError(
          t(code === "PASSWORD_LEAKED" ? "password.leaked" : "password.weak"),
        );
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
      <form
        noValidate
        onSubmit={submit}
        className="max-w-md space-y-4"
        aria-busy={sending}
      >
        <Field
          label={t("account.password.current")}
          error={currentError ?? undefined}
          alertError
        >
          {(c) => (
            <Input
              {...c}
              ref={currentRef}
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
            />
          )}
        </Field>
        <PasswordFields
          password={password}
          confirm={confirm}
          onPassword={setPassword}
          onConfirm={setConfirm}
          error={newError}
          inputRef={newRef}
          idPrefix="acc"
        />
        {formError && <Banner tone="danger">{formError}</Banner>}
        {done && <Banner tone="ok">{t("account.password.done")}</Banner>}
        <Button type="submit" disabled={sending || !valid}>
          {sending
            ? t("account.password.sending")
            : t("account.password.submit")}
        </Button>
      </form>
    </Card>
  );
}
