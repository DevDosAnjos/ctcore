import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import type { TFunction } from "i18next";
import type { Column } from "@/components/query/DataTable";
import { QueryPage, type Query } from "@/components/query/QueryPage";
import { Button } from "@/components/ui/button";
import { Select } from "@/components/ui/field";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatDateTime } from "@/lib/format";
import { ApproveRequestDialog, RefuseRequestDialog } from "./RefuseRequestDialog";
import { requestSummary } from "./RequestSummary";
import { REQUEST_KINDS, REQUEST_STATUSES, type PortalRequest } from "./types";
import { fetchPortalRequests, useReachableUnits } from "./usePortalRequests";
import { PageHeader } from "@/components/ui/page-header";

// D4: Aprovar marca o pedido e leva ao fluxo da gestão já preenchido; a matrícula só muda quando a gestão conclui o fluxo.
export function flowOf(r: PortalRequest): string {
  if (r.kind === "NEW_MODALITY") return `/alunos/nova-matricula?aluno=${r.studentId}&unidade=${r.targetUnitId}&modalidade=${r.targetModalityId}`;
  return `/alunos/${r.studentId}?pedido=${r.id}`;
}

type Decision = { request: PortalRequest; mode: "approve" | "refuse" };

function Filter({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: { id: string; name: string }[] }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      {label}
      <Select value={value} onChange={(e) => onChange(e.target.value)}>
        {options.map((o) => (
          <option key={o.id} value={o.id}>
            {o.name}
          </option>
        ))}
      </Select>
    </label>
  );
}

function Filters({ get, set }: { get: (k: string) => string; set: (k: string, v: string) => void }) {
  const { t } = useTranslation();
  const units = useReachableUnits();
  return (
    <>
      <Filter
        label={t("portalRequests.filters.status")}
        value={get("status")}
        onChange={(v) => set("status", v)}
        options={[{ id: "", name: t("portalRequests.filters.open") }, ...REQUEST_STATUSES.filter((s) => s !== "OPEN").map((s) => ({ id: s, name: t(`portalRequests.statuses.${s}`) })), { id: "ALL", name: t("portalRequests.filters.allStatuses") }]}
      />
      <Filter label={t("portalRequests.filters.kind")} value={get("kind")} onChange={(v) => set("kind", v)} options={[{ id: "", name: t("portalRequests.filters.allKinds") }, ...REQUEST_KINDS.map((k) => ({ id: k, name: t(`portalRequests.kinds.${k}`) }))]} />
      <Filter label={t("portalRequests.filters.unit")} value={get("unitId")} onChange={(v) => set("unitId", v)} options={[{ id: "", name: t("portalRequests.filters.allUnits") }, ...units]} />
    </>
  );
}

function Actions({ r, onDecide }: { r: PortalRequest; onDecide: (d: Decision) => void }) {
  const { t } = useTranslation();
  if (r.status !== "OPEN") return null;
  return (
    <span className="flex flex-wrap gap-2">
      {r.kind === "CLOSURE" ? (
        <Button asChild variant="outline" className="min-h-11">
          <Link to={`/alunos/${r.studentId}`} aria-label={t("portalRequests.actions.openRecordFor", { student: r.studentName })}>
            {t("portalRequests.actions.openRecord")}
          </Link>
        </Button>
      ) : (
        <Button className="min-h-11" aria-label={t("portalRequests.actions.approveFor", { student: r.studentName })} onClick={() => onDecide({ request: r, mode: "approve" })}>
          {t("portalRequests.actions.approve")}
        </Button>
      )}
      <Button variant="outline" className="min-h-11" aria-label={t("portalRequests.actions.refuseFor", { student: r.studentName })} onClick={() => onDecide({ request: r, mode: "refuse" })}>
        {t("portalRequests.actions.refuse")}
      </Button>
    </span>
  );
}

const Message = ({ r }: { r: PortalRequest }) => (r.note ? <span className="break-words">{r.note}</span> : null);

const columns = (t: TFunction, onDecide: (d: Decision) => void): Column<PortalRequest>[] => [
  { key: "kind", label: t("portalRequests.columns.kind"), render: (r) => t(`portalRequests.kinds.${r.kind}`) },
  {
    key: "student",
    label: t("portalRequests.columns.student"),
    render: (r) => (
      <Link to={`/alunos/${r.studentId}`} className="inline-flex min-h-11 items-center break-words font-medium underline">
        {r.studentName}
      </Link>
    ),
  },
  { key: "unit", label: t("portalRequests.columns.unit"), render: (r) => r.unit },
  { key: "request", label: t("portalRequests.columns.request"), render: (r) => requestSummary(t, r) },
  { key: "message", label: t("portalRequests.columns.message"), render: (r) => <Message r={r} /> },
  { key: "date", label: t("portalRequests.columns.date"), render: (r) => formatDateTime(r.createdAt) },
  { key: "status", label: t("portalRequests.columns.status"), render: (r) => t(`portalRequests.statuses.${r.status}`) },
  { key: "actions", label: t("portalRequests.columns.actions"), render: (r) => <Actions r={r} onDecide={onDecide} /> },
];

function Content() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [decision, setDecision] = useState<Decision>();
  const [version, setVersion] = useState(0);
  // Nova identidade refaz a consulta depois de uma decisão, sem piscar a lista (o resultado anterior segue à vista).
  const fetchPage = useCallback(
    (q: Query) => {
      void version;
      return fetchPortalRequests(q);
    },
    [version],
  );
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("portalRequests.title")} />
      <p className="text-muted-foreground">{t("portalRequests.intro")}</p>
      <QueryPage
        search={false}
        columns={columns(t, setDecision)}
        fetchPage={fetchPage}
        rowKey={(r) => r.id}
        filterKeys={["status", "kind", "unitId"]}
        filters={(p) => <Filters {...p} />}
        texts={{ emptyTitle: t("portalRequests.emptyTitle"), emptyText: t("portalRequests.emptyText") }}
        cards={(r) => (
          <div className="flex flex-col gap-1 text-sm">
            <p className="font-medium">{t(`portalRequests.kinds.${r.kind}`)}</p>
            <Link to={`/alunos/${r.studentId}`} className="inline-flex min-h-11 items-center break-words underline">
              {r.studentName}
            </Link>
            <p>{t("portalRequests.cardUnit", { unit: r.unit, date: formatDateTime(r.createdAt) })}</p>
            <p>{requestSummary(t, r)}</p>
            <Message r={r} />
            <p className="text-muted-foreground">{t(`portalRequests.statuses.${r.status}`)}</p>
            <Actions r={r} onDecide={setDecision} />
          </div>
        )}
      />
      {decision?.mode === "refuse" && (
        <RefuseRequestDialog request={decision.request} onClose={() => setDecision(undefined)} onDone={() => setVersion((n) => n + 1)} />
      )}
      {decision?.mode === "approve" && (
        <ApproveRequestDialog request={decision.request} onClose={() => setDecision(undefined)} onDone={(decided) => void navigate(flowOf(decided))} />
      )}
    </div>
  );
}

// RF-MAT-04: lista de pedidos do portal com Aprovar e Recusar; a barreira de dados é o servidor (403 vira "sem permissão" na própria lista).
export function PortalRequestsPage() {
  return (
    <ManagementLayout activeId="requests">
      <Content />
    </ManagementLayout>
  );
}
