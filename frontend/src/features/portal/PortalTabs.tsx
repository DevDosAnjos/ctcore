import { CalendarDays, House, Megaphone, User, Wallet, type LucideIcon } from "lucide-react";
import { useTranslation } from "react-i18next";
import { NavLink } from "react-router";
import { cn } from "@/lib/utils";

const TABS: { id: string; to: string; full: boolean; Icon: LucideIcon }[] = [
  { id: "home", to: "/", full: false, Icon: House },
  { id: "schedule", to: "/portal/agenda", full: true, Icon: CalendarDays },
  { id: "finance", to: "/portal/financeiro", full: false, Icon: Wallet },
  { id: "notices", to: "/portal/avisos", full: true, Icon: Megaphone },
  { id: "info", to: "/portal/minhas-informacoes", full: false, Icon: User },
];

const ring = "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";

/** Contador de avisos: 99+ acima de 99, some em zero. */
function Count({ count, className }: { count: number; className?: string }) {
  const { t } = useTranslation();
  if (count <= 0) return null;
  const shown = count > 99 ? "99+" : String(count);
  return (
    <span className={cn("min-w-5 rounded-full bg-[var(--org,var(--brand))] px-1 text-center text-xs font-semibold text-brand-foreground", className)}>
      <span aria-hidden="true">{shown}</span>
      <span className="sr-only">{t("portalShell.unread", { count: shown })}</span>
    </span>
  );
}

// Abas do portal: na barra escura a partir de 768 px e embaixo, com ícone e nome curto, abaixo disso. Agenda e Avisos somem no reduzido (RN-POR-08).
export function PortalTabs({ reduced, count, variant }: { reduced: boolean; count: number; variant: "bar" | "bottom" }) {
  const { t } = useTranslation();
  const bar = variant === "bar";
  return (
    <nav
      aria-label={t(bar ? "portalShell.nav" : "portalShell.navBottom")}
      className={bar ? "hidden gap-1 md:flex md:flex-1" : "fixed inset-x-0 bottom-0 z-10 flex border-t bg-background md:hidden"}
    >
      {TABS.filter((tab) => !(reduced && tab.full)).map(({ id, to, Icon }) => {
        return (
          <NavLink
            key={id}
            to={to}
            end
            className={cn(
              ring,
              bar
                ? "inline-flex min-h-11 items-center gap-2 rounded-md px-3 text-sm text-sidebar-foreground hover:bg-sidebar-accent aria-[current=page]:bg-[var(--org,var(--brand))] aria-[current=page]:font-semibold aria-[current=page]:text-brand-foreground"
                : "relative flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-xs aria-[current=page]:font-semibold aria-[current=page]:shadow-[inset_0_2px_0_var(--org,var(--brand))]",
            )}
          >
            {!bar && <Icon aria-hidden="true" className="size-5" />}
            {t(bar ? `portalShell.tabs.${id}` : `portalShell.tabsShort.${id}`)}
            {id === "notices" && count > 0 && " "}
            {id === "notices" && <Count count={count} className={bar ? undefined : "absolute right-1/4 top-1"} />}
          </NavLink>
        );
      })}
    </nav>
  );
}
