import { useRef, type ReactNode } from "react";
import { Bell, Lock } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { AccountLink } from "@/features/account/AccountFrame";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { useSession } from "@/features/auth/SessionProvider";
import { ThemeToggle } from "@/theme/ThemeToggle";
import { OrgMark, orgStyle } from "./OrgMark";
import { PortalFooter } from "./PortalFooter";
import { PortalTabs } from "./PortalTabs";
import { usePortalShell } from "./usePortalShell";

// RF-POR-01: moldura única do portal (barra escura com a marca, abas e sino; abas embaixo no celular). Agenda e Avisos somem no portal reduzido (RN-POR-08).
export function PortalLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const shell = usePortalShell();
  const main = useRef<HTMLElement>(null);
  const session = state.status === "ready" ? state.session : undefined;
  const role = session?.links.find((l) => l.id === session.activeLinkId)?.role;
  const data = shell.data;
  const reduced = data?.reduced ?? false;
  const count = data?.unreadAnnouncements ?? 0;
  const shown = count > 99 ? "99+" : String(count);
  const name = session?.account.name || session?.account.email;
  const orgName = data?.organization.name ?? t("app.name");
  return (
    <div className="flex min-h-screen flex-col" style={orgStyle(data?.organization.color)}>
      <a
        href="#conteudo"
        onClick={(e) => {
          e.preventDefault();
          main.current?.focus();
        }}
        className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-background focus:px-3 focus:py-2 focus:text-sm focus:ring-2 focus:ring-ring"
      >
        {t("portalShell.skip")}
      </a>
      <header className="flex flex-wrap items-center gap-x-4 gap-y-1 bg-sidebar px-4 py-2 text-sidebar-foreground">
        <Link to="/" className="flex min-h-11 items-center gap-2 rounded-md text-lg font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
          <OrgMark name={orgName} logoUrl={data?.organization.logoUrl} />
          <span>{orgName}</span>
        </Link>
        <PortalTabs reduced={reduced} count={count} variant="bar" />
        <div className="ml-auto flex flex-wrap items-center gap-2">
          {!reduced && data && (
            <Link
              to="/portal/avisos"
              aria-label={count > 0 ? t("portalShell.bell.withCount", { count: shown }) : t("portalShell.bell.label")}
              className="relative inline-flex min-h-11 min-w-11 items-center justify-center rounded-md hover:bg-sidebar-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <Bell aria-hidden="true" className="size-5" />
              {count > 0 && (
                <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-[var(--org,var(--brand))] px-1 text-center text-xs font-semibold text-brand-foreground">
                  {shown}
                </span>
              )}
            </Link>
          )}
          {name && role && <span className="hidden text-sm md:inline">{t("portalShell.person", { name, role: t(`chooseLink.roles.${role}`) })}</span>}
          <AccountLink />
          <ChooseLinkTrigger />
          <span className="text-sidebar-foreground [&_button:hover]:bg-sidebar-accent [&_button:hover]:text-sidebar-foreground">
            <ThemeToggle />
          </span>
        </div>
      </header>
      {reduced && (
        <p role="status" className="flex items-center gap-2 border-b bg-muted px-4 py-2 text-sm">
          <Lock aria-hidden="true" className="size-4 shrink-0" />
          {t("portalShell.reduced")}
        </p>
      )}
      <main id="conteudo" ref={main} tabIndex={-1} className="mx-auto w-full max-w-3xl flex-1 px-4 pb-6 pt-6 focus:outline-none">
        {shell.status === "loading" ? <LoadingState /> : shell.status === "forbidden" ? <ForbiddenState /> : shell.status === "error" ? <ErrorState onRetry={shell.retry} /> : children}
      </main>
      <PortalFooter organization={orgName} unit={data?.unit} />
      <PortalTabs reduced={reduced} count={count} variant="bottom" />
    </div>
  );
}
