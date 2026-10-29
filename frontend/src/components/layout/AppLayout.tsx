import { useRef, type ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { MobileTabs, type NavTab } from "./MobileTabs";
import { Sidebar } from "./Sidebar";
import { Topbar } from "./Topbar";
import { useSidebarCollapsed } from "./useSidebarCollapsed";

export type NavItem = { id: string; label: string; href: string; icon?: string };
export type NavGroup = { id: string; label: string; items: NavItem[] };

// D1: sem roteador; a tela passa os grupos e o id ativo, e os itens são links comuns.
// Moldura da gestão: menu lateral escuro (desktop); no celular, barra de cima, abas embaixo e menu em tela cheia.
export function AppLayout({
  groups,
  activeId,
  headerExtra,
  org,
  roleLabel,
  badges,
  tabs = [],
  children,
}: {
  groups: NavGroup[];
  activeId: string;
  headerExtra?: ReactNode;
  org?: string;
  roleLabel?: string;
  badges?: Record<string, number>;
  tabs?: NavTab[];
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const [collapsed, toggle] = useSidebarCollapsed();
  const main = useRef<HTMLElement>(null);
  const side = { groups, activeId, org, roleLabel, badges };
  return (
    <div className="min-h-screen md:flex">
      <a
        href="#conteudo"
        onClick={(e) => {
          e.preventDefault();
          main.current?.focus();
        }}
        className="sr-only focus:not-sr-only focus:fixed focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-background focus:px-3 focus:py-2 focus:text-sm focus:ring-2 focus:ring-ring"
      >
        {t("layout.skip")}
      </a>
      <aside className="sticky top-0 hidden h-screen shrink-0 md:block">
        <Sidebar {...side} collapsed={collapsed} onToggle={toggle} />
      </aside>
      <div className="min-w-0 flex-1">
        <Topbar
          groups={groups}
          activeId={activeId}
          leading={
            <div className="md:hidden">
              {activeId === "home" ? (
                <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-md bg-primary font-semibold text-primary-foreground">
                  {t("app.name").charAt(0).toUpperCase()}
                </span>
              ) : (
                <Button asChild variant="ghost" size="icon">
                  <a href="/" aria-label={t("layout.back")}>
                    <ArrowLeft aria-hidden="true" />
                  </a>
                </Button>
              )}
            </div>
          }
        >
          {headerExtra}
        </Topbar>
        <main id="conteudo" ref={main} tabIndex={-1} className="px-4 py-6 pb-20 focus:outline-none md:pb-6">
          {children}
        </main>
        <MobileTabs tabs={tabs} {...side} />
      </div>
    </div>
  );
}
