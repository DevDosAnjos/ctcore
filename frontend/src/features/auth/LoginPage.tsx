import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useSearchParams } from "react-router";
import { ErrorState } from "@/components/states";
import { AuthShell } from "@/components/layout/AuthShell";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ApiError, api } from "@/lib/api";
import { formatTime } from "@/lib/format";
import { safeNext } from "./safeNext";
import { useSession, type SessionView } from "./SessionProvider";
import { Turnstile } from "./Turnstile";
import { TwoFactorStep } from "./TwoFactorStep";

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
  const [lockedUntil, setLockedUntil] = useState<number | null>(null);
  const passwordRef = useRef<HTMLInputElement>(null);

  // RN-ACE-10: o aviso sai e "Entrar" volta quando a trava acaba.
  useEffect(() => {
    if (lockedUntil === null) return;
    const id = setTimeout(() => setLockedUntil(null), Math.max(0, lockedUntil - Date.now()));
    return () => clearTimeout(id);
  }, [lockedUntil]);

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
    setLockedUntil(null);
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
        const retryAt = err instanceof ApiError ? Date.parse(err.fields.find((f) => f.field === "retryAt")?.message ?? "") : NaN;
        if (retryAt > Date.now()) setLockedUntil(retryAt);
        else setNotice(t("auth.errors.tooMany"));
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
    <AuthShell title={t(ticket ? "twoFactor.login.title" : "auth.title")}>
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
          {lockedUntil !== null && (
            <Banner tone="warn">
              <div className="space-y-2">
                <p>{t("auth.errors.locked", { time: formatTime(lockedUntil) })}</p>
                <Button asChild variant="secondary" size="sm">
                  <Link to="/esqueci-senha">{t("auth.createPassword")}</Link>
                </Button>
              </div>
            </Banner>
          )}
          {notice && <Banner tone="danger">{notice}</Banner>}
          <Field label={t("auth.email")} error={errors.email}>
            {(c) => <Input {...c} type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />}
          </Field>
          <Field label={t("auth.password")} error={errors.password}>
            {(c) => (
              <div className="flex gap-2">
                <Input
                  {...c}
                  ref={passwordRef}
                  type={show ? "text" : "password"}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
                <Button type="button" variant="secondary" onClick={() => setShow((s) => !s)}>
                  {show ? t("auth.hide") : t("auth.show")}
                </Button>
              </div>
            )}
          </Field>
          <Turnstile onToken={setToken} resetKey={resetKey} />
          <Button type="submit" size="lg" className="w-full" disabled={sending || lockedUntil !== null}>
            {sending ? t("auth.sending") : t("auth.submit")}
          </Button>
          <p className="text-sm">
            <Link className="underline" to="/esqueci-senha">
              {t("auth.forgot")}
            </Link>
          </p>
        </form>
      )}
    </AuthShell>
  );
}
