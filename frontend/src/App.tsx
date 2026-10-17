import { useTranslation } from "react-i18next";
import { Route, Routes } from "react-router";
import { LoginPage } from "@/features/auth/LoginPage";
import { ForgotPasswordPage } from "@/features/auth/ForgotPasswordPage";
import { AreaUnavailablePage } from "@/features/auth/AreaUnavailablePage";
import { InvitePage } from "@/features/auth/InvitePage";
import { ResetPasswordPage } from "@/features/auth/ResetPasswordPage";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { RequireSession } from "@/features/auth/RequireSession";
import { ThemeToggle } from "@/theme/ThemeToggle";

function Home() {
  const { t } = useTranslation();
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
        <h1 className="text-2xl font-semibold">{t("app.welcomeTitle")}</h1>
        <p className="mt-2 text-muted-foreground">{t("app.welcomeText")}</p>
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
        <Route path="/" element={<Home />} />
        <Route path="/sem-acesso" element={<AreaUnavailablePage />} />
      </Route>
    </Routes>
  );
}
