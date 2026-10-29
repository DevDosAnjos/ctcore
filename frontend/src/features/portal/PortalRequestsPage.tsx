import { PageHeader } from "@/components/ui/page-header";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { AccountFrame } from "@/features/account/AccountFrame";
import { requestSummary } from "@/features/portal-requests/RequestSummary";
import type { PortalRequestKind } from "@/features/portal-requests/types";
import { formatDateTime } from "@/lib/format";
import { PortalRequestDialog } from "./PortalRequestDialog";
import { useMyRequests, usePortalStudents, useRequestOptions, type ModalityOption } from "./usePortalRequests";

type Open = { kind: PortalRequestKind; modality?: ModalityOption };

function Student({ studentId }: { studentId: string }) {
  const { t } = useTranslation();
  const options = useRequestOptions(studentId);
  const mine = useMyRequests(studentId);
  const [open, setOpen] = useState<Open>();
  const parts = [options, mine];
  if (parts.some((p) => p.status === "loading")) return <LoadingState />;
  if (parts.some((p) => p.status === "forbidden")) return <ForbiddenState />;
  if (!options.data || !mine.data) return <ErrorState onRetry={() => parts.forEach((p) => p.reload())} />;
  const act = (kind: PortalRequestKind, modality?: ModalityOption) => (
    <Button key={kind} variant="outline" className="min-h-11" onClick={() => setOpen({ kind, modality })}>
      {t(`portalOwn.actions.${kind}`)}
    </Button>
  );
  return (
    <div className="flex flex-col gap-4">
      <Card heading="h2" title={t("portalOwn.actionsTitle")}>
        <ul className="flex flex-col divide-y">
          {options.data.modalities.map((m) => (
            <li key={m.modalityRowId} className="flex flex-col gap-2 py-3">
              <p className="break-words font-medium">{t("portalOwn.modalityLine", { modality: m.modality, unit: m.unit, klass: m.currentClass.name, plan: m.currentPlan.name })}</p>
              {m.closureRequestedAt ? (
                <p className="text-sm text-muted-foreground">{t("portalOwn.closureOpen", { date: formatDateTime(m.closureRequestedAt) })}</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {act("CLASS_CHANGE", m)}
                  {act("PLAN_CHANGE", m)}
                  {act("CLOSURE", m)}
                </div>
              )}
            </li>
          ))}
        </ul>
        {options.data.offers.length > 0 && <div className="mt-3">{act("NEW_MODALITY")}</div>}
      </Card>
      <Card heading="h2" title={t("portalOwn.listTitle")}>
        {mine.data.length === 0 ? (
          <EmptyState title={t("portalOwn.emptyTitle")} text={t("portalOwn.emptyText")} />
        ) : (
          <ul className="flex flex-col divide-y">
            {mine.data.map((r) => (
              <li key={r.id} className="flex flex-col gap-1 py-3 text-sm">
                <p className="font-medium">{t(`portalRequests.kinds.${r.kind}`)}</p>
                <p className="break-words">{requestSummary(t, r)}</p>
                <p className="text-muted-foreground">{t("portalOwn.statusLine", { status: t(`portalRequests.statuses.${r.status}`), date: formatDateTime(r.createdAt) })}</p>
                {r.response && <p className="break-words">{t("portalOwn.response", { response: r.response })}</p>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      {open && <PortalRequestDialog studentId={studentId} kind={open.kind} modality={open.modality} offers={options.data.offers} onClose={() => setOpen(undefined)} onDone={() => parts.forEach((p) => p.reload())} />}
    </div>
  );
}

// RF-MAT-04 (#86): o aluno adulto, ou o responsável pelo menor, faz e acompanha os pedidos; nada muda na matrícula até a gestão agir.
export function PortalRequestsPage() {
  const { t } = useTranslation();
  const students = usePortalStudents();
  const [picked, setPicked] = useState<string>();
  let body;
  if (students.status === "loading") body = <LoadingState />;
  else if (students.status === "forbidden") body = <ForbiddenState />;
  else if (!students.data) body = <ErrorState onRetry={students.reload} />;
  else if (students.data.length === 0) body = <EmptyState title={t("portalOwn.noStudentTitle")} text={t("portalOwn.noStudentText")} />;
  else {
    const id = picked ?? students.data[0].id;
    body = (
      <div className="flex flex-col gap-4">
        {students.data.length > 1 && (
          <label className="flex flex-col gap-1 text-sm">
            {t("portalOwn.student")}
            <select className="min-h-11 rounded-md border bg-background px-2" value={id} onChange={(e) => setPicked(e.target.value)}>
              {students.data.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </select>
          </label>
        )}
        <Student key={id} studentId={id} />
      </div>
    );
  }
  return (
    <AccountFrame>
      <div className="mx-auto max-w-3xl">
        <PageHeader title={t("portalOwn.title")} />
        <p className="mb-4 mt-2 text-muted-foreground">{t("portalOwn.intro")}</p>
        {body}
      </div>
    </AccountFrame>
  );
}
