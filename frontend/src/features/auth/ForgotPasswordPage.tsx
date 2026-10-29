import { useState, useRef, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ErrorState } from "@/components/states";
import { AuthShell } from "@/components/layout/AuthShell";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ApiError, api } from "@/lib/api";
import { Turnstile } from "./Turnstile";


// RF-ACE-03 / RN-ACE-11: a tela nunca diz se o e-mail existe; mostra só a mensagem do servidor.
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [failed, setFailed] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const emailRef = useRef<HTMLInputElement>(null);

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending) return;
    setNotice(null);
    setFailed(false);
    if (!email.trim()) {
      setError(t("auth.errors.emailRequired"));
      emailRef.current?.focus();
      return;
    }
    setError(null);
    setSending(true);
    try {
      const body = { email: email.trim(), ...(token ? { challengeToken: token } : {}) };
      const res = await api<{ message: string }>("/auth/password-reset", { method: "POST", body });
      setDone(res.message);
    } catch (err) {
      if (err instanceof ApiError && err.status === 429) setNotice(t("auth.errors.tooMany"));
      else setFailed(true);
    } finally {
      setSending(false);
    }
  }

  const title = t("forgot.title");
  if (done !== null) {
    return (
      <AuthShell title={title}>
        <Banner tone="ok">{done}</Banner>
        <p className="text-sm">
          <Link className="underline" to="/entrar">
            {t("forgot.back")}
          </Link>
        </p>
      </AuthShell>
    );
  }
  if (failed) {
    return (
      <AuthShell title={title}>
        <ErrorState onRetry={() => void submit()} />
      </AuthShell>
    );
  }
  return (
    <AuthShell title={title} subtitle={t("forgot.intro")}>
      <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
        {notice && <Banner tone="danger">{notice}</Banner>}
        <Field label={t("auth.email")} error={error ?? undefined}>
          {(c) => <Input {...c} ref={emailRef} type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />}
        </Field>
        <Turnstile onToken={setToken} resetKey={0} />
        <Button type="submit" size="lg" className="w-full" disabled={sending}>
          {sending ? t("forgot.sending") : t("forgot.submit")}
        </Button>
        <p className="text-sm">
          <Link className="underline" to="/entrar">
            {t("forgot.back")}
          </Link>
        </p>
      </form>
    </AuthShell>
  );
}
