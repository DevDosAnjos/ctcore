import { useState } from "react";

const KEY = "ctcore.sidebar.collapsed";

// D3: a escolha fica no navegador; sem localStorage (bloqueado, modo privado) o menu segue aberto.
export function useSidebarCollapsed() {
  const [collapsed, set] = useState(() => {
    try {
      return localStorage.getItem(KEY) === "1";
    } catch {
      return false;
    }
  });
  const toggle = () =>
    set((c) => {
      try {
        localStorage.setItem(KEY, c ? "0" : "1");
      } catch {
        /* sem armazenamento: vale só nesta visita */
      }
      return !c;
    });
  return [collapsed, toggle] as const;
}
