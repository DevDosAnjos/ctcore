import { Navigate, Outlet } from "react-router";
import { LoadingState } from "@/components/states";
import { usePortalShell } from "./usePortalShell";

// R4/CA9: Agenda e Avisos não existem no portal reduzido; abrir pelo endereço leva ao Início. A barreira dos dados é do servidor (PortalShell.reduced).
export function RequireFullPortal() {
  const shell = usePortalShell();
  if (shell.status === "loading") return <LoadingState />;
  return shell.data?.reduced ? <Navigate to="/" replace /> : <Outlet />;
}
