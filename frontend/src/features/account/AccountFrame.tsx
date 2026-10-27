import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { PortalLayout } from "@/features/portal/PortalLayout";
import { ThemeToggle } from "@/theme/ThemeToggle";

const MANAGEMENT = ["COORDINATOR", "ADMINISTRATOR", "TEACHER"];

/** Atalho "Minha conta" para os cabeçalhos de gestão e de portal (D1: uma só tela para os dois). */
export function AccountLink() {
  const { t } = useTranslation();
  return (
    <Link to="/conta" className="inline-flex min-h-11 items-center rounded-md px-3 text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
      {t("account.link")}
    </Link>
  );
}

/**
 * Moldura da gestão (com menu) ou do portal (barra e abas, #88), conforme o vínculo ativo. `plain`: no portal, só o cabeçalho simples, para a tela
 * do aceite do termo, que abre antes de a moldura poder ler a rota do portal (TERM_PENDING).
 */
export function AccountFrame({ children, plain = false }: { children: ReactNode; plain?: boolean }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role ?? "";
  if (MANAGEMENT.includes(role)) return <ManagementLayout activeId="account">{children}</ManagementLayout>;
  if (!plain) return <PortalLayout>{children}</PortalLayout>;
  return (
    <div className="min-h-screen">
      <header className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2">
        <Link to="/" className="text-lg font-semibold">
          {t("app.name")}
        </Link>
        <div className="flex items-center gap-2">
          <AccountLink />
          <ChooseLinkTrigger />
          <ThemeToggle />
        </div>
      </header>
      <main className="px-4 py-6">{children}</main>
    </div>
  );
}
