import { createContext, useContext, type ReactNode } from "react";

// WhatsApp ligado ou desligado pela plataforma (RNF-42). Padrão: ligado (D6).
const Ctx = createContext(true);
export const WhatsAppProvider = ({ enabled, children }: { enabled: boolean; children: ReactNode }) => <Ctx.Provider value={enabled}>{children}</Ctx.Provider>;
export const useWhatsAppEnabled = () => useContext(Ctx);
