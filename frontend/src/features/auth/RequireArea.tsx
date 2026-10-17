import { Outlet } from "react-router";
import { AreaUnavailablePage } from "./AreaUnavailablePage";
import { useSession } from "./SessionProvider";

// Dentro de RequireSession. Nega sem redirecionar (D1); sem vínculo ativo também nega (CA4).
export function RequireArea({ roles }: { roles: string[] }) {
  const { state } = useSession();
  if (state.status !== "ready") return null;
  const { links, activeLinkId } = state.session;
  const role = links.find((l) => l.id === activeLinkId)?.role;
  return role && roles.includes(role) ? <Outlet /> : <AreaUnavailablePage />;
}
