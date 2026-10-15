import type { ComponentProps } from "react";
import { Modal } from "./Modal";

// Painel lateral: ao lado em tela larga, tela cheia no celular. Mesmo comportamento da janela.
export function SidePanel(props: Omit<ComponentProps<typeof Modal>, "className">) {
  return (
    <Modal
      {...props}
      className="inset-y-0 left-0 right-0 top-0 h-full max-h-full w-full max-w-none translate-x-0 translate-y-0 rounded-none md:left-auto md:w-96 md:border-l"
    />
  );
}
