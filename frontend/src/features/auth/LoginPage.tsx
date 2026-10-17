import { useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useSearchParams } from "react-router";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { safeNext } from "./safeNext";
import { useSession, type SessionView } from "./SessionProvider";
import { SupportContact, platformContact } from "./SupportContact";
import { Turnstile } from "./Turnstile";
import { TwoFactorStep } from "./TwoFactorStep";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

export function LoginPage() {
  const { t } = useTranslation();
  const { state, setSession } = useSession();
  const [params] = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [show, setShow] = useState(false);
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [notice, setNotice] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [failed, setFailed] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [resetKey, setResetKey] = useState(0);
  const [ticket, setTicket] = useState<string | null>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  if (state.status === "ready") return <Navigate to={safeNext(params.get("next"))} replace />;

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending) return;
    const next = {
      email: email.trim() ? undefined : t("auth.errors.emailRequired"),
      password: password ? undefined : t("auth.errors.passwordRequired"),
    };
    setErrors(next);
    setNotice(null);
    if (next.email || next.password) return;
    setFailed(false);
    setSending(true);
    try {
      const body = { email: email.trim(), password, ...(token ? { challengeToken: token } : {}) };
      const res = await api<SessionView | { twoFactorRequired: true; ticket: string }>("/auth/login", { method: "POST", body });
      if ("twoFactorRequired" in res) {
        setTicket(res.ticket);
        setPassword("");
      } else setSession(res);
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const code = err instanceof ApiError ? err.code : "";
      if (code === "INVALID_CREDENTIALS") {
        setNotice(t("auth.errors.invalid"));
        setPassword("");
        passwordRef.current?.focus();
      } else if (status === 429) {
        setNotice(t("auth.errors.tooMany"));
      } else if (code === "CHALLENGE_FAILED") {
        setNotice(t("auth.errors.challenge"));
        setResetKey((k) => k + 1);
      } else {
        setFailed(true);
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-md space-y-6 px-4 py-6">
        <div className="space-y-4 rounded-lg border bg-card p-4 text-card-foreground">
          <h1 className="text-2xl font-semibold">{t("auth.title")}</h1>
          {ticket ? (
            <TwoFactorStep
              ticket={ticket}
              onDone={setSession}
              onBack={() => setTicket(null)}
              onExpired={() => {
                setTicket(null);
                setNotice(t("twoFactor.login.expired"));
              }}
            />
          ) : failed ? (
            <ErrorState onRetry={() => void submit()} />
          ) : (
            <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
              {notice && (
                <p role="alert" className="text-sm text-destructive">
                  {notice}
                </p>
              )}
              <div>
                <label htmlFor="login-email" className="block text-sm font-medium">
                  {t("auth.email")}
                </label>
                <input
                  id="login-email"
                  type="email"
                  autoComplete="username"
                  className={input}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  aria-invalid={!!errors.email || undefined}
                  aria-describedby={errors.email ? "login-email-erro" : undefined}
                />
                {errors.email && (
                  <p id="login-email-erro" className="text-sm text-destructive">
                    {errors.email}
                  </p>
                )}
              </div>
              <div>
                <label htmlFor="login-password" className="block text-sm font-medium">
                  {t("auth.password")}
                </label>
                <div className="flex gap-2">
                  <input
                    id="login-password"
                    ref={passwordRef}
                    type={show ? "text" : "password"}
                    autoComplete="current-password"
                    className={input}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    aria-invalid={!!errors.password || undefined}
                    aria-describedby={errors.password ? "login-password-erro" : undefined}
                  />
                  <Button type="button" variant="outline" onClick={() => setShow((s) => !s)}>
                    {show ? t("auth.hide") : t("auth.show")}
                  </Button>
                </div>
                {errors.password && (
                  <p id="login-password-erro" className="text-sm text-destructive">
                    {errors.password}
                  </p>
                )}
              </div>
              <Turnstile onToken={setToken} resetKey={resetKey} />
              <Button type="submit" className="w-full" disabled={sending}>
                {sending ? t("auth.sending") : t("auth.submit")}
              </Button>
              <p className="text-sm">
                <Link className="underline" to="/esqueci-senha">
                  {t("auth.forgot")}
                </Link>
              </p>
            </form>
          )}
        </div>
        <SupportContact contact={platformContact()} />
      </main>
    </div>
  );
}
