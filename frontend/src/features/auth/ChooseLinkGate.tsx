import { ChooseLinkModal } from "./ChooseLinkModal";
import { useSession } from "./SessionProvider";

// RN-ACE-03: vários vínculos e nenhum ativo → o modal abre sozinho e só fecha com a escolha (D4).
export function ChooseLinkGate() {
  const { state } = useSession();
  const open =
    state.status === "ready" &&
    state.session.links.length > 1 &&
    !state.session.activeLinkId;
  return <ChooseLinkModal open={open} />;
}
