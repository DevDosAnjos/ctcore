import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { AuthShell } from "@/components/layout/AuthShell";
import { buttonVariants } from "@/components/ui/button";
import { ChooseLinkTrigger } from "./ChooseLinkTrigger";

// RF-ACE-08: texto único para qualquer área negada (D3); a recusa de dados é do servidor (403).
export function AreaUnavailablePage() {
  const { t } = useTranslation();
  return (
    <AuthShell title={t("areaUnavailable.title")} subtitle={t("areaUnavailable.text")} focusTitle>
      <div className="flex flex-wrap items-center gap-2">
        <Link to="/" className={buttonVariants({ size: "md" })}>
          {t("areaUnavailable.home")}
        </Link>
        <ChooseLinkTrigger />
      </div>
    </AuthShell>
  );
}
