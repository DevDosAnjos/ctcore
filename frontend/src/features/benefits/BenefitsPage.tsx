import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { usePermissions } from "@/features/permissions/usePermissions";
import type { TFunction } from "i18next";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { formatMoney } from "@/components/fields/masks";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { BenefitFilters } from "./BenefitFilters";
import { EndBenefitDialog } from "./EndBenefitDialog";
import { GrantBenefitDialog } from "./GrantBenefitDialog";
import { fetchBenefits, type BenefitRow } from "./useBenefits";

const badge = (text: string) => <span className="rounded-md border px-2 py-0.5 text-sm">{text}</span>;

const columns = (t: TFunction, onEnd?: (r: BenefitRow) => void): Column<BenefitRow>[] => [
  {
    key: "student",
    label: t("benefits.columns.student"),
    render: (r) => (
      <span className="flex min-w-0 flex-col break-words">
        <span className="font-medium">{r.student.name}</span>
        <span className="text-sm text-muted-foreground">{t("benefits.unit", { name: r.unit.name })}</span>
      </span>
    ),
  },
  { key: "modality", label: t("benefits.columns.modality"), render: (r) => r.modality.name },
  { key: "type", label: t("benefits.columns.type"), render: (r) => t(`benefits.types.${r.type}`) },
  {
    key: "value",
    label: t("benefits.columns.value"),
    render: (r) => (
      <span className="flex flex-col">
        <span>{r.type === "PERCENT" ? t("benefits.percent", { value: r.value }) : formatMoney(r.value)}</span>
        {r.high && <span className="text-sm text-muted-foreground">{t("benefits.highBadge")}</span>}
      </span>
    ),
  },
  { key: "reason", label: t("benefits.columns.reason"), render: (r) => <span className="break-words">{r.reason}</span> },
  { key: "decidedBy", label: t("benefits.columns.decidedBy"), render: (r) => r.decidedBy },
  {
    key: "validUntil",
    label: t("benefits.columns.validUntil"),
    render: (r) => (r.validUntil ? new Date(`${r.validUntil}T12:00:00`).toLocaleDateString("pt-BR") : t("benefits.noDeadline")),
  },
  {
    key: "status",
    label: t("benefits.columns.status"),
    render: (r) => (r.status === "ACTIVE" ? t("benefits.statuses.ACTIVE") : badge(t(`benefits.statuses.${r.status}`))),
  },
  ...(onEnd
    ? [
        {
          key: "actions",
          label: t("benefits.columns.actions"),
          render: (r: BenefitRow) =>
            r.status === "ACTIVE" ? (
              <Button type="button" variant="outline" className="min-h-11" onClick={() => onEnd(r)}>
                {t("benefits.end.button")}
              </Button>
            ) : null,
        },
      ]
    : []),
];

// RF-BEN-01 e RF-BEN-02: tabela de benefícios com "Conceder benefício" e "Encerrar" (RF-BEN-03). A barreira de dados é o servidor.
function Content() {
  const { t } = useTranslation();
  const { permissions } = usePermissions();
  const canGrant = !!permissions && permissions.BENEFITS !== "NONE";
  const [granting, setGranting] = useState(false);
  const [ending, setEnding] = useState<BenefitRow>();
  const [version, setVersion] = useState(0);
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{t("benefits.title")}</h1>
        {canGrant && (
          <Button className="min-h-11" onClick={() => setGranting(true)}>
            {t("benefits.grant.button")}
          </Button>
        )}
      </div>
      <p className="text-muted-foreground">{t("benefits.intro")}</p>
      {granting && <GrantBenefitDialog onClose={() => setGranting(false)} onDone={() => setVersion((n) => n + 1)} />}
      {ending && <EndBenefitDialog benefit={ending} onClose={() => setEnding(undefined)} onDone={() => setVersion((n) => n + 1)} />}
      <QueryPage key={version} columns={columns(t, canGrant ? setEnding : undefined)} fetchPage={fetchBenefits} rowKey={(r) => r.id} filterKeys={["type", "modalityId", "unitId", "status", "high"]} filters={(p) => <BenefitFilters {...p} />} />
    </div>
  );
}

export function BenefitsPage() {
  return (
    <ManagementLayout activeId="benefits">
      <Content />
    </ManagementLayout>
  );
}
