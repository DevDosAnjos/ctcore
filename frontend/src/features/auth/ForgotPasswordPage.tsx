import { useState, useRef, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { SupportContact, platformContact } from "./SupportContact";
import { Turnstile } from "./Turnstile";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

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

  function body() {
    if (done !== null) {
      return (
        <>
          <p role="status">{done}</p>
          <p className="text-sm">
            <Link className="underline" to="/entrar">
              {t("forgot.back")}
            </Link>
          </p>
        </>
      );
    }
    if (failed) return <ErrorState onRetry={() => void submit()} />;
    return (
      <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
        <p className="text-sm text-muted-foreground">{t("forgot.intro")}</p>
        {notice && (
          <p role="alert" className="text-sm text-destructive">
            {notice}
          </p>
        )}
        <div>
          <label htmlFor="forgot-email" className="block text-sm font-medium">
            {t("auth.email")}
          </label>
          <input
            id="forgot-email"
            ref={emailRef}
            type="email"
            autoComplete="username"
            className={input}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={!!error || undefined}
            aria-describedby={error ? "forgot-email-erro" : undefined}
          />
          {error && (
            <p id="forgot-email-erro" className="text-sm text-destructive">
              {error}
            </p>
          )}
        </div>
        <Turnstile onToken={setToken} resetKey={0} />
        <Button type="submit" className="w-full" disabled={sending}>
          {sending ? t("forgot.sending") : t("forgot.submit")}
        </Button>
        <p className="text-sm">
          <Link className="underline" to="/entrar">
            {t("forgot.back")}
          </Link>
        </p>
      </form>
    );
  }

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-md space-y-6 px-4 py-6">
        <div className="space-y-4 rounded-lg border bg-card p-4 text-card-foreground">
          <h1 className="text-2xl font-semibold">{t("forgot.title")}</h1>
          {body()}
        </div>
        <SupportContact contact={platformContact()} />
      </main>
    </div>
  );
}
