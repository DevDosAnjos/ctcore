import { useTranslation } from "react-i18next";
import type { PortalShellData } from "./usePortalShell";

/** Rodapé do portal: organização, unidade, endereço e WhatsApp (o que não existe não aparece) e "feito com ctcore". */
export function PortalFooter({ organization, unit }: { organization: string; unit?: PortalShellData["unit"] }) {
  const { t } = useTranslation();
  const parts = [organization, unit?.name, unit?.address, unit?.whatsapp ? t("portalShell.footer.whatsapp", { number: unit.whatsapp }) : null].filter(Boolean);
  return (
    <footer aria-label={t("portalShell.footer.label")} className="border-t px-4 pb-20 pt-4 text-sm text-muted-foreground md:pb-4">
      <p>{parts.join(" · ")}</p>
      <p className="text-xs">{t("portalShell.footer.madeWith")}</p>
    </footer>
  );
}
