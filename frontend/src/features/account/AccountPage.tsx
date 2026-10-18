import { useTranslation } from "react-i18next";
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
        <h1 className="text-2xl font-semibold">{t("account.title")}</h1>
        <ChangePasswordForm onChanged={sessions.reload} />
        <ChangeEmailForm />
        <TwoFactorSection />
        <SessionsSection {...sessions} />
      </div>
    </AccountFrame>
  );
}
