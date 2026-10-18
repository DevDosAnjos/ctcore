import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { RelativeDate } from "@/components/RelativeDate";
import { ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { api } from "@/lib/api";
import type { AccountSessionItem } from "./useAccount";

// RF-ACE-04: a lista nunca traz token nem IP; a sessão em uso não se encerra aqui (D7, existe "Sair").
export function SessionsSection({
  status,
  items,
  reload,
}: {
  status: "loading" | "ready" | "error";
  items: AccountSessionItem[];
  reload: () => void;
}) {
  const { t } = useTranslation();
  const [confirmAll, setConfirmAll] = useState(false);
  const [failed, setFailed] = useState(false);
  const others = items.filter((s) => !s.current);

  async function end(id: string) {
    setFailed(false);
    try {
      await api<void>(`/auth/sessions/${id}`, { method: "DELETE" });
    } catch {
      setFailed(true);
    }
    reload();
  }

  async function endAll() {
    await api<void>("/auth/sessions", { method: "DELETE" }); // se falhar, a janela segue aberta
    reload();
  }

  return (
    <Card
      title={t("account.sessions.title")}
      heading="h2"
      actions={
        others.length > 0 && (
          <Button variant="outline" onClick={() => setConfirmAll(true)}>
            {t("account.sessions.endAll")}
          </Button>
        )
      }
    >
      {status === "loading" ? (
        <LoadingState />
      ) : status === "error" ? (
        <ErrorState onRetry={reload} />
      ) : (
        <>
          {failed && (
            <p role="alert" className="mb-2 text-sm text-destructive">
              {t("account.sessions.endFailed")}
            </p>
          )}
          <ul className="divide-y">
            {items.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <div className="min-w-0">
                  <p className="font-medium">
                    {s.device}
                    {s.current && <span className="ml-2 rounded-md border px-2 py-0.5 text-xs">{t("account.sessions.current")}</span>}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {s.city ? `${s.city} · ` : ""}
                    {t("account.sessions.lastUse")} <RelativeDate value={s.lastUsedAt} />
                  </p>
                </div>
                {!s.current && (
                  <Button variant="outline" onClick={() => void end(s.id)} aria-label={t("account.sessions.endOne", { device: s.device })}>
                    {t("account.sessions.end")}
                  </Button>
                )}
              </li>
            ))}
          </ul>
          {others.length === 0 && <p className="mt-2 text-sm text-muted-foreground">{t("account.sessions.empty")}</p>}
        </>
      )}
      <ConfirmDialog
        open={confirmAll}
        onOpenChange={setConfirmAll}
        title={t("account.sessions.endAllTitle")}
        summary={t("account.sessions.endAllSummary")}
        confirmLabel={t("account.sessions.endAll")}
        onConfirm={endAll}
      />
    </Card>
  );
}
