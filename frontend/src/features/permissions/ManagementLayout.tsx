import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { ErrorState, LoadingState } from "@/components/states";
import { AppLayout } from "@/components/layout/AppLayout";
import { AreaUnavailablePage } from "@/features/auth/AreaUnavailablePage";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { UnitScopeProvider } from "@/features/units/UnitScopeProvider";
import { UnitSwitcher } from "@/features/units/UnitSwitcher";
import { buildMenu } from "./buildMenu";
import { usePermissions } from "./usePermissions";

// Layout da gestão com o menu montado pelas permissões do servidor (R7). O menu só esconde; a barreira é o servidor (D5).
export function ManagementLayout({ activeId = "home", children }: { activeId?: string; children: ReactNode }) {
  const { t } = useTranslation();
  const { status, permissions, retry } = usePermissions();
  if (!permissions) {
    return (
      <main className="mx-auto max-w-md px-4 py-6">
        {status === "loading" ? <LoadingState /> : <ErrorState onRetry={retry} />}
      </main>
    );
  }
  const groups = buildMenu(permissions, t);
  if (groups.length === 0) return <AreaUnavailablePage />;
  return (
    <UnitScopeProvider>
      <AppLayout
        groups={groups}
        activeId={activeId}
        headerExtra={
          <>
            <UnitSwitcher />
            <ChooseLinkTrigger />
          </>
        }
      >
        {children}
      </AppLayout>
    </UnitScopeProvider>
  );
}
