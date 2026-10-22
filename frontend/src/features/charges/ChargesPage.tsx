import { useState } from "react";
import { useTranslation } from "react-i18next";
import { QueryPage, type Page } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { useQueryParams } from "@/components/query/useQueryParams";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatCurrency, formatDate } from "@/lib/format";
import type { ChargeRow } from "./types";
import { ChargeCard, OpenButton, StatusBadge, StudentCell } from "./ChargeCards";
import { ChargeFilters } from "./ChargeFilters";
import { ChargePanel } from "./ChargePanel";
import { fetchCharges } from "./useCharges";

const FILTER_KEYS = ["quick", "from", "to", "status", "type", "unitId", "modalityId", "classId"];

function Content() {
  const { t } = useTranslation();
  // O painel vive no endereço (?cobranca=); depois de cancelar ou renegociar a lista é recarregada.
  const { get, set } = useQueryParams();
  const [version, setVersion] = useState(0);
  const open = (id: string) => set({ cobranca: id });
  const id = get("cobranca");
  const columns: Column<ChargeRow>[] = [
    { key: "student", label: t("charges.columns.student"), sortable: true, render: (r) => <StudentCell row={r} /> },
    { key: "unit", label: t("charges.columns.unit"), render: (r) => r.unit.name },
    { key: "reference", label: t("charges.columns.reference"), render: (r) => <OpenButton row={r} onOpen={open} /> },
    { key: "amount", label: t("charges.columns.amount"), sortable: true, render: (r) => formatCurrency(r.amountCents) },
    { key: "dueOn", label: t("charges.columns.dueOn"), sortable: true, render: (r) => formatDate(r.dueOn) },
    { key: "status", label: t("charges.columns.status"), render: (r) => <StatusBadge status={r.status} /> },
    { key: "method", label: t("charges.columns.method"), render: (r) => (r.payment ? t(`payments.method.${r.payment.method}`) : t("format.empty")) },
  ];
  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-2xl font-semibold">{t("charges.title")}</h1>
      <p className="text-muted-foreground">{t("charges.intro")}</p>
      <QueryPage
        key={version}
        columns={columns}
        fetchPage={fetchCharges}
        rowKey={(r) => r.id}
        filterKeys={FILTER_KEYS}
        filters={(p) => <ChargeFilters {...p} />}
        cards={(r) => <ChargeCard row={r} onOpen={open} />}
        footer={(page: Page<ChargeRow>) => (
          <p className="font-medium">{t("charges.totals", { count: page.counts?.totalCount ?? page.total, amount: formatCurrency(page.counts?.totalCents ?? 0) })}</p>
        )}
      />
      {id && <ChargePanel id={id} onClose={() => set({ cobranca: "" })} onChanged={() => setVersion((v) => v + 1)} />}
    </div>
  );
}

// RF-COB-01: lista de cobranças da gestão; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function ChargesPage() {
  return (
    <ManagementLayout activeId="collections">
      <Content />
    </ManagementLayout>
  );
}
