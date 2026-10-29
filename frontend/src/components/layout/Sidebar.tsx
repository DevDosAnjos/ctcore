import { useId, useState } from "react";
import { ChevronDown, ChevronsLeft, ChevronsRight, CircleHelp, Sparkles } from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import type { NavGroup } from "./AppLayout";
import { iconFor } from "./icons";

const row =
  "flex min-h-11 w-full items-center gap-3 rounded-md px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring";
const live = "hover:bg-sidebar-accent aria-[current=page]:bg-sidebar-accent aria-[current=page]:font-semibold";

export const badgeText = (n: number) => (n > 99 ? "99+" : String(n));

export interface SidebarProps {
  groups: NavGroup[];
  activeId: string;
  org?: string;
  roleLabel?: string;
  badges?: Record<string, number>;
  collapsed?: boolean;
  onToggle?: () => void;
}

// Menu escuro (tokens sidebar*): grupos que abrem e fecham, item ativo, contadores, rodapé e recolher a 72 px (D3).
export function Sidebar({ groups, activeId, org, roleLabel, badges = {}, collapsed = false, onToggle }: SidebarProps) {
  const { t } = useTranslation();
  const uid = useId();
  const [open, setOpen] = useState<Set<string>>(() => new Set(groups.filter((g) => g.items.some((i) => i.id === activeId)).map((g) => g.id)));
  const flip = (id: string) =>
    setOpen((o) => {
      const n = new Set(o);
      if (!n.delete(id)) n.add(id);
      return n;
    });

  const link = (i: NavGroup["items"][number], nested: boolean) => {
    const Icon = iconFor(i.icon);
    const n = badges[i.id] ?? 0;
    const badgeId = `${uid}-${i.id}`;
    return (
      <a
        key={i.id}
        href={i.href}
        aria-current={i.id === activeId ? "page" : undefined}
        aria-label={collapsed ? i.label : undefined}
        aria-describedby={n > 0 && !collapsed ? badgeId : undefined}
        title={collapsed ? i.label : undefined}
        className={cn(row, live, nested && !collapsed && "pl-6", collapsed && "justify-center px-0")}
      >
        <Icon aria-hidden="true" className="size-5 shrink-0" />
        {!collapsed && <span className="min-w-0 flex-1 truncate">{i.label}</span>}
        {n > 0 && !collapsed && (
          <span id={badgeId} hidden={false} aria-hidden="true" className="rounded-full bg-primary px-1.5 text-center text-xs font-semibold text-primary-foreground">{badgeText(n)}</span>
        )}
      </a>
    );
  };

  const soon = t("layout.soon");
  const footer = (label: string, Icon: typeof CircleHelp) => (
    <button
      type="button"
      disabled
      title={collapsed ? `${label} · ${soon}` : soon}
      aria-label={collapsed ? `${label}, ${soon}` : undefined}
      className={cn(row, "opacity-60", collapsed && "justify-center px-0")}
    >
      <Icon aria-hidden="true" className="size-5 shrink-0" />
      {!collapsed && (
        <span className="min-w-0 flex-1 truncate text-left">
          {label} <span className="text-xs">({soon})</span>
        </span>
      )}
    </button>
  );

  return (
    <div className={cn("flex h-full flex-col bg-sidebar text-sidebar-foreground", collapsed ? "w-18" : "w-64")}>
      <div className={cn("flex items-center gap-3 px-4 py-4", collapsed && "justify-center px-0")}>
        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary font-semibold text-primary-foreground">
          {t("app.name").charAt(0).toUpperCase()}
        </span>
        {!collapsed && (
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{org ?? t("app.name")}</p>
            {roleLabel && <p className="truncate text-xs opacity-70">{t("layout.area", { role: roleLabel })}</p>}
          </div>
        )}
      </div>
      <nav aria-label={t("layout.main")} className="flex flex-1 flex-col gap-1 overflow-y-auto p-2">
        {groups.map((g) => {
          if (g.items.length === 1 && g.items[0].id === g.id) return link(g.items[0], false);
          const expanded = open.has(g.id);
          return (
            <div key={g.id}>
              {!collapsed && (
                <button type="button" aria-expanded={expanded} onClick={() => flip(g.id)} className={cn(row, "font-medium hover:bg-sidebar-accent")}>
                  <span className="flex-1 text-left">{g.label}</span>
                  <ChevronDown aria-hidden="true" className={cn("size-4 transition-transform", expanded && "rotate-180")} />
                </button>
              )}
              {(collapsed || expanded) && <div className="flex flex-col gap-1">{g.items.map((i) => link(i, true))}</div>}
            </div>
          );
        })}
      </nav>
      <div className="flex flex-col gap-1 border-t border-sidebar-accent p-2">
        {footer(t("layout.help"), CircleHelp)}
        {footer(t("layout.news"), Sparkles)}
        {onToggle && (
          <button
            type="button"
            onClick={onToggle}
            aria-label={collapsed ? t("layout.expand") : t("layout.collapse")}
            title={collapsed ? t("layout.expand") : undefined}
            className={cn(row, live, collapsed && "justify-center px-0")}
          >
            {collapsed ? <ChevronsRight aria-hidden="true" className="size-5" /> : <ChevronsLeft aria-hidden="true" className="size-5" />}
            {!collapsed && <span>{t("layout.collapse")}</span>}
          </button>
        )}
        {!collapsed && <p className="px-3 pt-1 text-xs opacity-70">{t("layout.madeWith")}</p>}
      </div>
    </div>
  );
}
