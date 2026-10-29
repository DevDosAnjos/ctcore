import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { ErrorState, LoadingState } from "@/components/states";
import { AppLayout } from "@/components/layout/AppLayout";
import { AccountLink } from "@/features/account/AccountFrame";
import { AreaUnavailablePage } from "@/features/auth/AreaUnavailablePage";
import { ChooseLinkTrigger } from "@/features/auth/ChooseLinkTrigger";
import { useSession } from "@/features/auth/SessionProvider";
import { BellButton } from "@/features/notifications/BellButton";
import { UnitScopeProvider } from "@/features/units/UnitScopeProvider";
import { UnitSwitcher } from "@/features/units/UnitSwitcher";
import { buildMenu } from "./buildMenu";
import { buildTabs } from "./buildTabs";
import { usePendingRequests } from "./usePendingRequests";
import { usePermissions } from "./usePermissions";

// Layout da gestão com o menu montado pelas permissões do servidor (R7). O menu só esconde; a barreira é o servidor (D5).
export function ManagementLayout({ activeId = "home", children }: { activeId?: string; children: ReactNode }) {
  const { t } = useTranslation();
  const { state } = useSession();
  const { status, permissions, retry } = usePermissions();
  const groups = permissions ? buildMenu(permissions, t) : [];
  const pending = usePendingRequests(groups.some((g) => g.items.some((i) => i.id === "requests")));
  if (!permissions) {
    return (
      <main className="mx-auto max-w-md px-4 py-6">
        {status === "loading" ? <LoadingState /> : <ErrorState onRetry={retry} />}
      </main>
    );
  }
  if (groups.length === 0) return <AreaUnavailablePage />;
  const link = state.status === "ready" ? state.session.links.find((l) => l.id === state.session.activeLinkId) : undefined;
  const roleLabel = link && ["ADMINISTRATOR", "COORDINATOR", "TEACHER"].includes(link.role) ? t(`layout.roles.${link.role}`) : undefined;
  return (
    <UnitScopeProvider>
      <AppLayout
        groups={groups}
        activeId={activeId}
        org={link?.organizationName}
        roleLabel={roleLabel}
        badges={{ requests: pending }}
        tabs={buildTabs(permissions, link?.role, t)}
        headerExtra={
          <>
            <UnitSwitcher />
            <BellButton />
            <AccountLink roleLabel={roleLabel} />
            <ChooseLinkTrigger />
          </>
        }
      >
        {children}
      </AppLayout>
    </UnitScopeProvider>
  );
}
