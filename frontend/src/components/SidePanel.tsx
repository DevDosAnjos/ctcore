import type { ComponentProps, ReactNode } from "react";
import { Modal } from "./Modal";
import { Tabs, type TabItem } from "@/components/ui/tabs";

// Painel lateral (ficha): 680 px ao lado em tela larga, tela cheia no celular. Mesmo comportamento da janela.
export function SidePanel({
  tabs,
  children,
  ...props
}: Omit<ComponentProps<typeof Modal>, "className" | "children"> & { children?: ReactNode; /** Abas no topo do conteúdo. */ tabs?: TabItem[] }) {
  return (
    <Modal
      {...props}
      className="inset-y-0 left-0 right-0 top-0 h-full max-h-full w-full max-w-none translate-x-0 translate-y-0 rounded-none md:left-auto md:w-[min(680px,100%)] md:border-l"
    >
      {tabs ? <Tabs tabs={tabs} label={props.title} /> : children}
    </Modal>
  );
}
