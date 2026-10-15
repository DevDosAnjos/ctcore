import { useTranslation } from "react-i18next";
import { ThemeToggle } from "@/theme/ThemeToggle";

export function App() {
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
