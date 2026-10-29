import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AuthShell } from "@/components/layout/AuthShell";
import { Button } from "@/components/ui/button";
import { ErrorState } from "@/components/states";
import { api } from "@/lib/api";
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
    <AuthShell title={t("twoFactor.required.title")} subtitle={t("twoFactor.required.text")}>
      {failed && <ErrorState onRetry={() => void logout()} />}
      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setOpen(true)}>{t("twoFactor.required.activate")}</Button>
        <Button variant="secondary" onClick={() => void logout()}>
          {t("twoFactor.required.logout")}
        </Button>
      </div>
      <TwoFactorSetupDialog open={open} onClose={() => setOpen(false)} onEnabled={() => void refresh()} />
    </AuthShell>
  );
}
