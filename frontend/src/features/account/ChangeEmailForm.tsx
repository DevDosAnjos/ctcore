import { useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { CodeField } from "@/features/auth/CodeField";
import { useSession } from "@/features/auth/SessionProvider";
import { ApiError, api } from "@/lib/api";

// RN-ACE-15 (D2): o pedido exige a senha e, com as duas etapas, o código. O e-mail só muda ao abrir o link.
export function ChangeEmailForm() {
  const { t } = useTranslation();
  const { state } = useSession();
  const session = state.status === "ready" ? state.session : undefined;
  const withCode = !!session?.twoFactor.enabled;
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [code, setCode] = useState("");
  const [errors, setErrors] = useState<{
    email?: string;
    password?: string;
    code?: string;
    form?: string;
  }>({});
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (sending) return;
    setSentTo(null);
    if (!email.trim() || !password || (withCode && !code)) {
      setErrors({ form: t("account.email.required") });
      return;
    }
    setErrors({});
    setSending(true);
    try {
      await api<void>("/auth/email-change", {
        method: "POST",
        body: {
          newEmail: email.trim(),
          password,
          ...(withCode ? { code } : {}),
        },
      });
      setSentTo(email.trim());
      setEmail("");
      setPassword("");
      setCode("");
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const apiCode = err instanceof ApiError ? err.code : "";
      if (apiCode === "INVALID_CREDENTIALS")
        setErrors({ password: t("reauth.invalidPassword") });
      else if (apiCode === "INVALID_TWO_FACTOR_CODE")
        setErrors({ code: t("reauth.invalidCode") });
      else if (status === 400) setErrors({ email: t("account.email.invalid") });
      else if (status === 429)
        setErrors({ form: t("twoFactor.errors.tooMany") });
      else setErrors({ form: t("twoFactor.errors.failed") });
    } finally {
      setSending(false);
    }
  }

  return (
    <Card title={t("account.email.title")} heading="h2">
      <form
        noValidate
        onSubmit={submit}
        className="max-w-md space-y-4"
        aria-busy={sending}
      >
        <p className="text-sm">
          {t("account.email.intro", { email: session?.account.email ?? "" })}
        </p>
        {errors.form && <Banner tone="danger">{errors.form}</Banner>}
        <Field label={t("account.email.new")} error={errors.email} alertError>
          {(c) => (
            <Input
              {...c}
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>
        <Field
          label={t("account.email.password")}
          error={errors.password}
          alertError
        >
          {(c) => (
            <Input
              {...c}
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </Field>
        {withCode && (
          <CodeField
            id="acc-email-code"
            kind="any"
            label={t("reauth.code")}
            value={code}
            onChange={setCode}
            error={errors.code}
          />
        )}
        {sentTo && (
          <p role="status" className="text-sm">
            {t("account.email.sent", { email: sentTo })}
          </p>
        )}
        <Button type="submit" disabled={sending}>
          {sending ? t("account.email.sending") : t("account.email.submit")}
        </Button>
      </form>
    </Card>
  );
}
