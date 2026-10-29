import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Avatar } from "@/components/ui/avatar";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { PortalLayout } from "@/features/portal/PortalLayout";
import { ThemeToggle } from "@/theme/ThemeToggle";

const MANAGEMENT = ["COORDINATOR", "ADMINISTRATOR", "TEACHER"];

/** Atalho "Minha conta" para os cabeçalhos de gestão e de portal (D1: uma só tela para os dois). */
export function AccountLink({ roleLabel }: { roleLabel?: string }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const account = state.status === "ready" ? state.session.account : undefined;
  // Avatar e nome só na barra da gestão (que passa o perfil); o portal mantém o link de texto.
  const name = roleLabel ? account?.name || account?.email : undefined;
  return (
    <Link
      to="/conta"
      className="inline-flex min-h-11 items-center gap-2 rounded-md px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      <span className={name ? "sr-only" : "underline"}>{t("account.link")}</span>
      {name && (
        <>
          <Avatar name={name} decorative />
          <span className="hidden min-w-0 text-left leading-tight lg:block">
            <span className="block max-w-40 truncate font-medium">{name}</span>
            {roleLabel && <span className="block text-xs text-muted-foreground">{roleLabel}</span>}
          </span>
        </>
      )}
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
