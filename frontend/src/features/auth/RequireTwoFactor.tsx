import { Navigate, Outlet, useLocation } from "react-router";
import { useSession } from "./SessionProvider";

export const TWO_FACTOR_REQUIRED_PATH = "/configurar-duas-etapas";

// Dentro de RequireSession (R2 na tela): quem é obrigado a ter as duas etapas e ainda não tem
// só alcança a página de configuração; qualquer outro endereço volta a ela. O servidor também recusa (403).
export function RequireTwoFactor() {
  const { state } = useSession();
  const { pathname } = useLocation();
  if (state.status !== "ready") return null;
  const { enabled, required } = state.session.twoFactor;
  const blocked = required && !enabled;
  if (blocked && pathname !== TWO_FACTOR_REQUIRED_PATH) return <Navigate to={TWO_FACTOR_REQUIRED_PATH} replace />;
  if (!blocked && pathname === TWO_FACTOR_REQUIRED_PATH) return <Navigate to="/" replace />;
  return <Outlet />;
}
