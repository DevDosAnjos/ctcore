import { useTranslation } from "react-i18next";
import { Route, Routes } from "react-router";
import { LoginPage } from "@/features/auth/LoginPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { AreaUnavailablePage } from "@/features/auth/AreaUnavailablePage";
import { InvitePage } from "@/features/auth/InvitePage";
import { ResetPasswordPage } from "@/features/auth/ResetPasswordPage";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { RequireSession } from "@/features/auth/RequireSession";
import { RequireTwoFactor, TWO_FACTOR_REQUIRED_PATH } from "@/features/auth/RequireTwoFactor";
import { TwoFactorRequiredPage } from "@/features/auth/TwoFactorRequiredPage";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { OrganizationSettingsPage } from "@/features/organization/OrganizationSettingsPage";
import { NewUnitPage } from "@/features/units/NewUnitPage";
import { UnitSettingsPage } from "@/features/units/UnitSettingsPage";
import { UnitsPage } from "@/features/units/UnitsPage";
import { RequireArea } from "@/features/auth/RequireArea";
import { useSession } from "@/features/auth/SessionProvider";
import { ThemeToggle } from "@/theme/ThemeToggle";

function Welcome() {
  const { t } = useTranslation();
  return (
    <>
      <h1 className="text-2xl font-semibold">{t("app.welcomeTitle")}</h1>
      <p className="mt-2 text-muted-foreground">{t("app.welcomeText")}</p>
    </>
  );
}

// Gestão (Coordenador, Administrador, Professor) ganha o menu pelas permissões do servidor; portal segue sem menu.
function Home() {
  const { t } = useTranslation();
  const { state } = useSession();
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role;
  if (role === "COORDINATOR" || role === "ADMINISTRATOR" || role === "TEACHER") {
    return (
      <ManagementLayout>
        <Welcome />
      </ManagementLayout>
    );
  }
  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <div className="flex items-center gap-2">
          <ChooseLinkTrigger />
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <Welcome />
      </main>
    </div>
  );
}

// Precisa estar dentro de um roteador e do SessionProvider (main.tsx).
export function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route path="/esqueci-senha" element={<ForgotPasswordPage />} />
      <Route path="/redefinir-senha/:code" element={<ResetPasswordPage />} />
      <Route path="/convite/:code" element={<InvitePage />} />
      <Route element={<RequireSession />}>
        <Route element={<RequireTwoFactor />}>
          <Route path={TWO_FACTOR_REQUIRED_PATH} element={<TwoFactorRequiredPage />} />
          <Route path="/" element={<Home />} />
          <Route path="/sem-acesso" element={<AreaUnavailablePage />} />
          <Route element={<RequireArea roles={["COORDINATOR", "ADMINISTRATOR"]} />}>
            <Route path="/configuracoes/organizacao" element={<OrganizationSettingsPage />} />
            <Route path="/configuracoes/unidade" element={<UnitSettingsPage />} />
          </Route>
          <Route element={<RequireArea roles={["COORDINATOR"]} />}>
            <Route path="/configuracoes/unidades" element={<UnitsPage />} />
            <Route path="/configuracoes/unidades/nova" element={<NewUnitPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  );
}
