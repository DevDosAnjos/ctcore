import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useParams } from "react-router";
import { ErrorState, LoadingState } from "@/components/states";
import { AuthShell } from "@/components/layout/AuthShell";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { Field, Input } from "@/components/ui/field";
import { ApiError, api } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import { PasswordFields } from "./PasswordFields";
import { isStrongPassword } from "./PasswordRules";
import { useSession, type SessionView } from "./SessionProvider";

interface InviteInfo {
  email: string;
  name?: string | null;
  expiresAt: string;
  existingAccount: boolean;
}

type Load = { status: "loading" } | { status: "error" } | { status: "invalid"; reason: string } | { status: "ready"; info: InviteInfo };

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

  if (load.status === "loading") {
    return (
      <AuthShell title={t("invite.title")}>
        <LoadingState />
      </AuthShell>
    );
  }
  if (load.status === "error") {
    return (
      <AuthShell title={t("invite.title")}>
        <ErrorState
          onRetry={() => {
            setLoad({ status: "loading" });
            setAttempt((n) => n + 1);
          }}
        />
      </AuthShell>
    );
  }
  if (load.status === "invalid") {
    return (
      <AuthShell title={t("invite.invalid.title")} focusTitle>
        <Banner tone="warn">{t(`invite.invalid.${load.reason}`)}</Banner>
        <p className="text-sm text-muted-foreground">{t("invite.help")}</p>
      </AuthShell>
    );
  }
  if (failed) {
    return (
      <AuthShell title={t(existing ? "invite.titleExisting" : "invite.title")}>
        <ErrorState onRetry={() => void submit()} />
      </AuthShell>
    );
  }
  const { info } = load;
  return (
    <AuthShell title={t(existing ? "invite.titleExisting" : "invite.title")}>
      <form noValidate onSubmit={submit} className="space-y-4" aria-busy={sending}>
        {info.name && <Field label={t("invite.name")}>{(c) => <Input {...c} readOnly value={info.name ?? ""} />}</Field>}
        <Field label={t("invite.email")}>{(c) => <Input {...c} type="email" readOnly autoComplete="username" value={info.email} />}</Field>
        <p className="text-sm text-muted-foreground">{t("invite.validUntil", { date: formatDateTime(info.expiresAt) })}</p>
        {notice && <Banner tone="danger">{notice}</Banner>}
        {existing ? (
          <>
            <p className="text-sm">{t("invite.existingNotice")}</p>
            <Field label={t("invite.currentPassword")}>
              {(c) => <Input {...c} ref={currentRef} type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />}
            </Field>
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
        <Button type="submit" size="lg" className="w-full" disabled={sending || !valid}>
          {sending ? t("invite.sending") : t(existing ? "invite.submitExisting" : "invite.submit")}
        </Button>
      </form>
    </AuthShell>
  );
}
