import { Navigate, Outlet, useLocation } from "react-router";
import { ErrorState, LoadingState } from "@/components/states";
import { useSession } from "./SessionProvider";

export function RequireSession() {
  const { state, refresh } = useSession();
  const { pathname, search } = useLocation();
  if (state.status === "loading") return <LoadingState />;
  if (state.status === "error") return <ErrorState onRetry={() => void refresh()} />;
  if (state.status === "anon") return <Navigate to={`/entrar?${new URLSearchParams({ next: pathname + search })}`} replace />;
  return <Outlet />;
}
