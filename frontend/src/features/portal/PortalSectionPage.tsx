import { useTranslation } from "react-i18next";
import { EmptyState } from "@/components/states";
import { PortalLayout } from "./PortalLayout";

/** D4: página provisória das abas ainda sem conteúdo (#91 a #94); cada item troca só o miolo. */
export function PortalSectionPage({ tab }: { tab: "schedule" | "finance" | "notices" | "info" }) {
  const { t } = useTranslation();
  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t(`portalShell.tabs.${tab}`)}</h1>
      <EmptyState title={t("portalShell.soon.title")} text={t("portalShell.soon.text")} />
    </PortalLayout>
  );
}
