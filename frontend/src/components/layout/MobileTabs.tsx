import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { iconFor } from "./icons";
import { MobileMenu } from "./MobileMenu";
import type { SidebarProps } from "./Sidebar";

export type NavTab = { id: string; label: string; href: string; icon?: string; disabled?: boolean };

// Abas de baixo (até 768 px): as do perfil e, no fim, "Menu" em tela cheia.
export function MobileTabs({ tabs, ...side }: { tabs: NavTab[] } & SidebarProps) {
  const { t } = useTranslation();
  const cell = "flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";
  return (
    <nav aria-label={t("layout.tabs.label")} className="fixed inset-x-0 bottom-0 z-30 flex border-t bg-background md:hidden">
      {tabs.map((tab) => {
        const Icon = iconFor(tab.icon);
        const body = (
          <>
            <Icon aria-hidden="true" className="size-5" />
            {tab.label}
          </>
        );
        return tab.disabled ? (
          <button key={tab.id} type="button" disabled title={t("layout.soon")} className={cn(cell, "opacity-60")}>
            {body}
          </button>
        ) : (
          <a key={tab.id} href={tab.href} aria-current={tab.id === side.activeId ? "page" : undefined} className={cn(cell, "aria-[current=page]:font-semibold aria-[current=page]:text-primary")}>
            {body}
          </a>
        );
      })}
      <MobileMenu {...side} />
    </nav>
  );
}
