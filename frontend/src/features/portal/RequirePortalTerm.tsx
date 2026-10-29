import { Navigate, Outlet } from "react-router";
import { ErrorState, LoadingState } from "@/components/states";
import { useSession } from "@/features/auth/SessionProvider";
import { usePortalTerm } from "./usePortalTerm";

export const PORTAL_TERM_PATH = "/portal/termo";

function PortalGate() {
  const term = usePortalTerm();
  if (term.status === "loading") return <LoadingState />;
  if (term.status === "error") return <ErrorState onRetry={term.retry} />;
  // 403 não chega aqui para Aluno e Responsável; qualquer outra coisa segue e o servidor decide em cada rota.
  return term.data?.length ? <Navigate to={PORTAL_TERM_PATH} replace /> : <Outlet />;
}

// RN-POR-03: com termo pendente, o portal leva ao aceite antes de qualquer outra tela. A gestão passa direto; o servidor bloqueia as rotas (TERM_PENDING).
export function RequirePortalTerm() {
  const { state } = useSession();
  if (state.status !== "ready") return null;
  const { links, activeLinkId } = state.session;
  const role = links.find((l) => l.id === activeLinkId)?.role;
  return role === "STUDENT" || role === "GUARDIAN" ? <PortalGate /> : <Outlet />;
}
