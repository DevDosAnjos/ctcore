import type { ReactNode } from "react";
import { CircleHelp, Search } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/theme/ThemeToggle";
import type { NavGroup } from "./AppLayout";
import { Breadcrumb } from "./Breadcrumb";

// Barra de cima: trilha à esquerda; unidade, busca, sino, tema, ajuda e conta à direita. Busca e ajuda ainda sem tela (D2): desabilitadas com motivo.
export function Topbar({ groups, activeId, leading, children }: { groups: NavGroup[]; activeId: string; leading?: ReactNode; children?: ReactNode }) {
  const { t } = useTranslation();
  const soon = t("layout.soon");
  return (
    <header className="flex items-center gap-2 border-b px-4 py-2">
      {leading}
      <div className="min-w-0 flex-1">
        <Breadcrumb groups={groups} activeId={activeId} />
      </div>
      <div className="flex min-w-0 flex-wrap items-center justify-end gap-2">
        {children}
        <Button variant="outline" disabled title={soon} aria-label={`${t("layout.search")}, ${soon}`} className="gap-2 text-muted-foreground">
          <Search aria-hidden="true" className="size-4" />
          <span className="hidden sm:inline">{t("layout.search")}</span>
          <kbd className="hidden rounded border px-1 text-xs sm:inline">{t("layout.searchKey")}</kbd>
        </Button>
        <ThemeToggle />
        <Button variant="ghost" size="icon" disabled title={soon} aria-label={`${t("layout.help")}, ${soon}`} className="hidden md:inline-flex">
          <CircleHelp aria-hidden="true" />
        </Button>
      </div>
    </header>
  );
}
