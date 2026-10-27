import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { EmptyState } from "@/components/states";
import { PortalLayout } from "./PortalLayout";

/** D4: página provisória das abas ainda sem conteúdo (#92 a #94); cada item troca só o miolo. */
export function PortalSectionPage({ tab }: { tab: "notices" | "info" }) {
  const { t } = useTranslation();
  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t(`portalShell.tabs.${tab}`)}</h1>
      {tab === "info" && (
        <Link to="/portal/carteirinha" className="mb-4 inline-flex min-h-11 items-center underline">
          {t("portalCard.link")}
        </Link>
      )}
      <EmptyState title={t("portalShell.soon.title")} text={t("portalShell.soon.text")} />
    </PortalLayout>
  );
}
