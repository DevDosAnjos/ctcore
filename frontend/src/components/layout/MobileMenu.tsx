import * as Dialog from "@radix-ui/react-dialog";
import { Menu, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { Sidebar, type SidebarProps } from "./Sidebar";

// Menu em tela cheia do celular (Radix Dialog: foco preso, Esc fecha e o foco volta à aba "Menu").
export function MobileMenu(props: SidebarProps) {
  const { t } = useTranslation();
  return (
    <Dialog.Root>
      <Dialog.Trigger className="flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 text-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
        <Menu aria-hidden="true" className="size-5" />
        {t("layout.menu")}
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Content aria-describedby={undefined} className="fixed inset-0 z-50 flex flex-col overflow-y-auto bg-sidebar text-sidebar-foreground">
          <div className="flex items-center justify-between px-4 py-2">
            <Dialog.Title className="text-lg font-semibold">{t("layout.menu")}</Dialog.Title>
            <Dialog.Close asChild>
              <Button variant="ghost" size="icon" aria-label={t("layout.closeMenu")}>
                <X aria-hidden="true" />
              </Button>
            </Dialog.Close>
          </div>
          <Sidebar {...props} />
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
