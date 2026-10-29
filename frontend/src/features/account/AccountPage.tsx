import { useTranslation } from "react-i18next";
import { PageHeader } from "@/components/ui/page-header";
import { AccountFrame } from "./AccountFrame";
import { ChangeEmailForm } from "./ChangeEmailForm";
import { ChangePasswordForm } from "./ChangePasswordForm";
import { SessionsSection } from "./SessionsSection";
import { TwoFactorSection } from "./TwoFactorSection";
import { useAccountSessions } from "./useAccount";

// RF-ACE-04: "Minha conta" para qualquer perfil logado; as rotas agem só sobre a conta da sessão (R13).
export function AccountPage() {
  const { t } = useTranslation();
  const sessions = useAccountSessions();
  return (
    <AccountFrame>
      <div className="mx-auto max-w-3xl space-y-4">
        <PageHeader title={t("account.title")} />
        <ChangePasswordForm onChanged={sessions.reload} />
        <ChangeEmailForm />
        <TwoFactorSection />
        <SessionsSection {...sessions} />
      </div>
    </AccountFrame>
  );
}
