import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { PortalTag } from "./PortalTag";

// RF-POR-05, D3: cartão informativo; ativar, trocar e desativar chegam com o pagamento online (Sprint 11). Sem botão nem chamada de rede.
export function PortalAutoDebitCard() {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={t("portalFinance.autoDebit.title")} actions={<PortalTag>{t("portalFinance.autoDebit.inactive")}</PortalTag>}>
      <p className="text-muted-foreground">{t("portalFinance.autoDebit.text")}</p>
    </Card>
  );
}
