import { useState, type ReactNode } from "react";
import { Bell } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link, NavLink } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { AccountLink } from "@/features/account/AccountFrame";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { useSession } from "@/features/auth/SessionProvider";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { usePortalShell } from "./usePortalShell";

const TABS = [
  { id: "home", to: "/", full: false },
  { id: "schedule", to: "/portal/agenda", full: true },
  { id: "finance", to: "/portal/financeiro", full: false },
  { id: "notices", to: "/portal/avisos", full: true },
  { id: "info", to: "/portal/minhas-informacoes", full: false },
] as const;

const tabClass =
  "flex min-h-11 flex-1 items-center justify-center rounded-md px-3 text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[current=page]:bg-accent aria-[current=page]:font-semibold md:flex-none";

// RF-POR-01: moldura única do portal (barra, abas e sino). Abas embaixo abaixo de 768 px e na barra a partir daí; Agenda e Avisos somem no portal reduzido (RN-POR-08).
export function PortalLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const shell = usePortalShell();
  const [logoBroken, setLogoBroken] = useState(false);
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role;
  const data = shell.data;
  const reduced = data?.reduced ?? false;
  const count = data?.unreadAnnouncements ?? 0;
  const shown = count > 99 ? "99+" : String(count);
  const name = session?.account.name || session?.account.email;
  return (
    <div className="min-h-screen">
      <header className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-t-4 px-4 py-2" style={data?.organization.color ? { borderTopColor: data.organization.color } : undefined}>
        <Link to="/" className="flex min-h-11 items-center gap-2 rounded-md text-lg font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          {data?.organization.logoUrl && !logoBroken && (
            <img src={data.organization.logoUrl} alt="" className="h-8 w-8 object-contain" onError={() => setLogoBroken(true)} />
          )}
          <span>{data?.organization.name ?? t("app.name")}</span>
        </Link>
        <nav
          aria-label={t("portalShell.nav")}
          className="fixed inset-x-0 bottom-0 z-10 flex gap-1 border-t bg-background p-1 md:static md:z-auto md:flex-1 md:border-0 md:bg-transparent md:p-0"
        >
          {TABS.filter((tab) => !(reduced && tab.full)).map((tab) => (
            <NavLink key={tab.id} to={tab.to} end className={tabClass}>
              {t(`portalShell.tabs.${tab.id}`)}
            </NavLink>
          ))}
        </nav>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {!reduced && data && (
            <Link
              to="/portal/avisos"
              aria-label={count > 0 ? t("portalShell.bell.withCount", { count: shown }) : t("portalShell.bell.label")}
              className="relative inline-flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Bell aria-hidden="true" className="size-5" />
              {count > 0 && (
                <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-primary px-1 text-center text-xs font-semibold text-primary-foreground">
                  {shown}
                </span>
              )}
            </Link>
          )}
          {name && role && <span className="text-sm">{t("portalShell.person", { name, role: t(`chooseLink.roles.${role}`) })}</span>}
          <AccountLink />
          <ChooseLinkTrigger />
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 pb-24 pt-6 md:pb-6">
        {reduced && (
          <p role="status" className="mb-4 rounded-md border bg-muted px-3 py-2 text-sm">
            {t("portalShell.reduced")}
          </p>
        )}
        {shell.status === "loading" ? <LoadingState /> : shell.status === "forbidden" ? <ForbiddenState /> : shell.status === "error" ? <ErrorState onRetry={shell.retry} /> : children}
      </main>
    </div>
  );
}
