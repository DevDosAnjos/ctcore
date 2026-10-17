import { useTranslation } from "react-i18next";
import { Route, Routes } from "react-router";
import { LoginPage } from "@/features/auth/LoginPage";
import { InvitePage } from "@/features/auth/InvitePage";
import { RequireSession } from "@/features/auth/RequireSession";
import { ThemeToggle } from "@/theme/ThemeToggle";

function Home() {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen">
      <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
        <span className="text-lg font-semibold">{t("app.name")}</span>
        <ThemeToggle />
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <h1 className="text-2xl font-semibold">{t("app.welcomeTitle")}</h1>
        <p className="mt-2 text-muted-foreground">{t("app.welcomeText")}</p>
      </main>
    </div>
  );
}

// Provisório: a tela real é a #22.
function ForgotPassword() {
  const { t } = useTranslation();
  return (
    <main className="mx-auto max-w-md px-4 py-6">
      <h1 className="text-2xl font-semibold">{t("auth.forgot")}</h1>
    </main>
  );
}

// Precisa estar dentro de um roteador e do SessionProvider (main.tsx).
export function App() {
  return (
    <Routes>
      <Route path="/entrar" element={<LoginPage />} />
      <Route path="/esqueci-senha" element={<ForgotPassword />} />
      <Route path="/convite/:code" element={<InvitePage />} />
      <Route element={<RequireSession />}>
        <Route path="/" element={<Home />} />
      </Route>
    </Routes>
  );
}
