import { useRef, type ReactNode, type RefObject } from "react";
import * as Dialog from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

// Janela: título como nome acessível, foco preso, Esc fecha e o Radix devolve o foco a quem abriu.
export function Modal({
  open,
  onOpenChange,
  title,
  children,
  className,
  dismissible = true,
  returnFocusRef,
  footer,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
  className?: string;
  /** Falso: sem Esc, sem clique fora e sem botão de fechar (a pessoa precisa decidir). */
  dismissible?: boolean;
  /** Quem recebe o foco ao fechar, quando a janela abre depois de uma resposta e o foco original se perdeu. */
  returnFocusRef?: RefObject<HTMLElement | null>;
  footer?: ReactNode;
}) {
  const { t } = useTranslation();
  const opener = useRef<HTMLElement | null>(null);
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-sidebar/70" />
        <Dialog.Content
          aria-describedby={undefined}
          onEscapeKeyDown={dismissible ? undefined : (e) => e.preventDefault()}
          onInteractOutside={dismissible ? undefined : (e) => e.preventDefault()}
          // Aberta por estado (sem Trigger), o Radix não sabe quem abriu: guardamos e devolvemos o foco.
          onOpenAutoFocus={() => {
            opener.current = document.activeElement as HTMLElement | null;
          }}
          onCloseAutoFocus={(e) => {
            e.preventDefault();
            (returnFocusRef?.current ?? opener.current)?.focus();
          }}
          className={cn(
            "fixed left-1/2 top-1/2 z-50 max-h-full w-[calc(100%-2rem)] max-w-lg -translate-x-1/2 -translate-y-1/2 flex flex-col overflow-hidden rounded-window border bg-card",
            className,
          )}
        >
          <div className="flex items-center justify-between gap-2 border-b border-divider px-4 py-3">
            <Dialog.Title className="font-heading text-xl font-semibold">{title}</Dialog.Title>
            {dismissible && (
              <Dialog.Close asChild>
                <Button variant="ghost" size="icon" aria-label={t("common.close")}>
                  <X aria-hidden="true" />
                </Button>
              </Dialog.Close>
            )}
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-4">{children}</div>
          {footer && <div className="flex justify-end gap-2 border-t border-divider bg-surface-soft px-4 py-3">{footer}</div>}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
