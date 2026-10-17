import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ApiError, api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { PasswordFields } from "./PasswordFields";
import { isStrongPassword } from "./PasswordRules";
import { useSession, type SessionView } from "./SessionProvider";
import { SupportContact, platformContact } from "./SupportContact";

interface InviteInfo {
  email: string;
  name?: string | null;
  expiresAt: string;
  existingAccount: boolean;
}

type Load = { status: "loading" } | { status: "error" } | { status: "invalid"; reason: string } | { status: "ready"; info: InviteInfo };

const input = "min-h-11 w-full rounded-md border bg-background px-2";
const REASONS = ["EXPIRED", "USED", "CANCELED"];

function reasonOf(err: ApiError) {
  const reason = err.fields.find((f) => f.field === "reason")?.message ?? "";
  return REASONS.includes(reason) ? reason : "GENERIC";
}

// RF-ACE-02: o código vive só no caminho da URL; a senha só no estado do formulário (R11).
export function InvitePage() {
  const { t } = useTranslation();
  const { code = "" } = useParams();
  const { setSession } = useSession();
  const navigate = useNavigate();
  const [load, setLoad] = useState<Load>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [failed, setFailed] = useState(false);
  const passwordRef = useRef<HTMLInputElement>(null);
  const currentRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    let alive = true;
    api<InviteInfo>(`/auth/invitations/${encodeURIComponent(code)}`)
      .then((info) => alive && setLoad({ status: "ready", info }))
      .catch((err) => {
        if (!alive) return;
        setLoad(err instanceof ApiError && err.status === 410 ? { status: "invalid", reason: reasonOf(err) } : { status: "error" });
      });
    return () => {
      alive = false;
    };
  }, [code, attempt]);

  const existing = load.status === "ready" && load.info.existingAccount;
  const valid = existing ? password !== "" : isStrongPassword(password) && password === confirm;

  async function submit(e?: FormEvent) {
    e?.preventDefault();
    if (sending || !valid) return;
    setFailed(false);
    setNotice(null);
    setFieldError(null);
    setSending(true);
    try {
      setSession(await api<SessionView>(`/auth/invitations/${encodeURIComponent(code)}/accept`, { method: "POST", body: { password } }));
      navigate("/", { replace: true });
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      const errCode = err instanceof ApiError ? err.code : "";
      if (status === 410 && err instanceof ApiError) {
        setLoad({ status: "invalid", reason: reasonOf(err) });
      } else if (errCode === "PASSWORD_LEAKED" || errCode === "PASSWORD_WEAK") {
        setFieldError(t(errCode === "PASSWORD_LEAKED" ? "password.leaked" : "password.weak"));
        passwordRef.current?.focus();
      } else if (errCode === "INVALID_CREDENTIALS") {
        setNotice(t("auth.errors.invalid"));
        setPassword("");
        currentRef.current?.focus();
      } else if (status === 429) {
        setNotice(t("auth.errors.tooMany"));
      } else {
        setFailed(true);
      }
    } finally {
      setSending(false);
    }
  }

  function body() {
    if (load.status === "loading") return <LoadingState />;
    if (load.status === "error") {
      return (
        <ErrorState
          onRetry={() => {
            setLoad({ status: "loading" });
            setAttempt((n) => n + 1);
          }}
        />
      );
    }
    if (load.status === "invalid") {
      return (
        <section className="space-y-2">
          <h1 className="text-2xl font-semibold">{t("invite.invalid.title")}</h1>
          <p role="alert">{t(`invite.invalid.${load.reason}`)}</p>
          <p>{t("invite.help")}</p>
        </section>
      );
    }
    if (failed) return <ErrorState onRetry={() => void submit()} />;
    const { info } = load;
    return (
      <>
        <h1 className="text-2xl font-semibold">{t(existing ? "invite.titleExisting" : "invite.title")}</h1>
        <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
          {info.name && (
            <div>
              <label htmlFor="invite-name" className="block text-sm font-medium">
                {t("invite.name")}
              </label>
              <input id="invite-name" readOnly className={input} value={info.name} />
            </div>
          )}
          <div>
            <label htmlFor="invite-email" className="block text-sm font-medium">
              {t("invite.email")}
            </label>
            <input id="invite-email" type="email" readOnly autoComplete="username" className={input} value={info.email} />
          </div>
          <p className="text-sm text-muted-foreground">{t("invite.validUntil", { date: formatDateTime(info.expiresAt) })}</p>
          {notice && (
            <p role="alert" className="text-sm text-destructive">
              {notice}
            </p>
          )}
          {existing ? (
            <>
              <p className="text-sm">{t("invite.existingNotice")}</p>
              <div>
                <label htmlFor="invite-current" className="block text-sm font-medium">
                  {t("invite.currentPassword")}
                </label>
                <input
                  id="invite-current"
                  ref={currentRef}
                  type="password"
                  autoComplete="current-password"
                  className={input}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </>
          ) : (
            <PasswordFields
              password={password}
              confirm={confirm}
              onPassword={setPassword}
              onConfirm={setConfirm}
              error={fieldError}
              inputRef={passwordRef}
              idPrefix="invite"
            />
          )}
          <Button type="submit" className="w-full" disabled={sending || !valid}>
            {sending ? t("invite.sending") : t(existing ? "invite.submitExisting" : "invite.submit")}
          </Button>
        </form>
      </>
    );
  }

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-md space-y-6 px-4 py-6">
        <div className="space-y-4 rounded-lg border bg-card p-4 text-card-foreground">{body()}</div>
        <SupportContact contact={platformContact()} />
      </main>
    </div>
  );
}
