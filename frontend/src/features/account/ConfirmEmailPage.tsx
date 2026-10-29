import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { ErrorState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { useSession } from "@/features/auth/SessionProvider";
import { ApiError, api } from "@/lib/api";
import { AccountFrame } from "./AccountFrame";
import { useReloadSession } from "./useAccount";

// RN-ACE-15 (D8): o link só vale para quem está logado na conta dona; sem sessão, o RequireSession leva ao login e volta.
// A troca é um botão (e não automática) para o link não ser gasto por uma releitura da página.
export function ConfirmEmailPage() {
  const { t } = useTranslation();
  const { code = "" } = useParams();
  const { refresh } = useSession();
  const reload = useReloadSession();
  const [phase, setPhase] = useState<
    "ask" | "sending" | "done" | "invalid" | "failed"
  >("ask");

  async function confirm() {
    setPhase("sending");
    try {
      await api<void>(
        `/auth/email-change/${encodeURIComponent(code)}/confirm`,
        { method: "POST" },
      );
      await reload().catch(() => undefined);
      setPhase("done");
    } catch (err) {
      const status = err instanceof ApiError ? err.status : 0;
      if (status === 401) void refresh();
      else setPhase(status === 410 ? "invalid" : "failed");
    }
  }

  return (
    <AccountFrame>
      <div className="mx-auto max-w-md space-y-4">
        <PageHeader title={t("account.confirm.title")} />
        {phase === "failed" ? (
          <ErrorState onRetry={() => void confirm()} />
        ) : phase === "invalid" ? (
          <Banner tone="warn">{t("account.confirm.invalid")}</Banner>
        ) : phase === "done" ? (
          <Banner tone="ok">{t("account.confirm.done")}</Banner>
        ) : (
          <>
            <p>{t("account.confirm.text")}</p>
            <Button
              disabled={phase === "sending"}
              onClick={() => void confirm()}
            >
              {phase === "sending"
                ? t("account.confirm.sending")
                : t("account.confirm.submit")}
            </Button>
          </>
        )}
        <p>
          <Link className="underline" to="/conta">
            {t("account.confirm.back")}
          </Link>
        </p>
      </div>
    </AccountFrame>
  );
}
