import { useState, type ReactNode } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { Menu, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/theme/ThemeToggle";

export type NavItem = { id: string; label: string; href: string };
export type NavGroup = { id: string; label: string; items: NavItem[] };

const linkClass =
  "flex min-h-11 items-center rounded-md px-3 text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[current=page]:bg-accent aria-[current=page]:font-semibold";

// D1: sem roteador; a tela passa os grupos e o id ativo, e os itens são links comuns.
function NavList({ groups, activeId }: { groups: NavGroup[]; activeId: string }) {
  const { t } = useTranslation();
  return (
    <nav aria-label={t("layout.main")} className="flex flex-col gap-1 p-2">
      {groups.map((g) => (
        <details key={g.id} open={g.items.some((i) => i.id === activeId)}>
          <summary className="flex min-h-11 cursor-pointer items-center rounded-md px-3 text-sm font-medium hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            {g.label}
          </summary>
          <ul>
            {g.items.map((i) => (
              <li key={i.id}>
                <a href={i.href} aria-current={i.id === activeId ? "page" : undefined} className={cn(linkClass, "pl-6")}>
                  {i.label}
                </a>
              </li>
            ))}
          </ul>
        </details>
      ))}
    </nav>
  );
}

export function AppLayout({
  groups,
  activeId,
  headerExtra,
  children,
}: {
  groups: NavGroup[];
  activeId: string;
  headerExtra?: ReactNode;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  return (
    <div className="min-h-screen md:grid md:grid-cols-[16rem_1fr]">
      <aside className="hidden border-r md:block">
        <div className="px-4 py-3 text-lg font-semibold">{t("app.name")}</div>
        <NavList groups={groups} activeId={activeId} />
      </aside>
      <div className="min-w-0">
        <header className="flex items-center justify-between gap-2 border-b px-4 py-2">
          <Dialog.Root open={open} onOpenChange={setOpen}>
            <Dialog.Trigger asChild>
              <Button variant="ghost" size="icon" className="md:hidden" aria-label={t("layout.openMenu")}>
                <Menu aria-hidden="true" />
              </Button>
            </Dialog.Trigger>
            <Dialog.Portal>
              <Dialog.Overlay className="fixed inset-0 z-40 bg-black/50" />
              <Dialog.Content
                aria-describedby={undefined}
                className="fixed inset-y-0 left-0 z-50 w-72 max-w-full overflow-y-auto border-r bg-background"
              >
                <div className="flex items-center justify-between px-4 py-2">
                  <Dialog.Title className="text-lg font-semibold">{t("layout.menu")}</Dialog.Title>
                  <Dialog.Close asChild>
                    <Button variant="ghost" size="icon" aria-label={t("layout.closeMenu")}>
                      <X aria-hidden="true" />
                    </Button>
                  </Dialog.Close>
                </div>
                <NavList groups={groups} activeId={activeId} />
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
          <span className="text-lg font-semibold md:hidden">{t("app.name")}</span>
          <div className="ml-auto flex items-center gap-2">
            {headerExtra}
            <ThemeToggle />
          </div>
        </header>
        <main className="px-4 py-6">{children}</main>
      </div>
    </div>
  );
}
