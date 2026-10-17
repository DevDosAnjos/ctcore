import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/states";
import { api } from "@/lib/api";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { useSession } from "./SessionProvider";
import { TwoFactorSetupDialog } from "./TwoFactorSetupDialog";

// R16: configuração obrigatória das duas etapas; só sai ativando ou saindo da conta.
export function TwoFactorRequiredPage() {
  const { t } = useTranslation();
  const { refresh } = useSession();
  const [open, setOpen] = useState(false);
  const [failed, setFailed] = useState(false);

  async function logout() {
    setFailed(false);
    try {
      await api<void>("/auth/logout", { method: "POST" });
      await refresh();
    } catch {
      setFailed(true);
    }
  }

  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-md space-y-4 px-4 py-6">
        <h1 className="text-2xl font-semibold">{t("twoFactor.required.title")}</h1>
        <p className="text-muted-foreground">{t("twoFactor.required.text")}</p>
        {failed && <ErrorState onRetry={() => void logout()} />}
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setOpen(true)}>{t("twoFactor.required.activate")}</Button>
          <Button variant="outline" onClick={() => void logout()}>
            {t("twoFactor.required.logout")}
          </Button>
        </div>
      </main>
      <TwoFactorSetupDialog open={open} onClose={() => setOpen(false)} onEnabled={() => void refresh()} />
    </div>
  );
}
