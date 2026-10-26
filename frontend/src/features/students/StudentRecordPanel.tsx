import { useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { SidePanel } from "@/components/SidePanel";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { WhatsAppButton } from "@/components/whatsapp";
import { Button } from "@/components/ui/button";
import { usePermissions } from "@/features/permissions/usePermissions";
import { StudentFinancialTab, isOverdue } from "./StudentFinancialTab";
import { StudentEditDialog } from "./StudentEditDialog";
import { RecordEnrollment } from "./RecordEnrollment";
import { RecordGuardians } from "./RecordGuardians";
import { RecordHistory } from "./RecordHistory";
import { RecordSummary } from "./RecordSummary";
import { useStudentCharges } from "@/features/charges/useChargeActions";
import { useEnrollment, useStudentRecord } from "./useStudentRecord";
import type { EnrollmentStatus, StudentRecord } from "./types";

const TABS = ["summary", "enrollment", "financial", "graduations", "guardians", "history"] as const;
type Tab = (typeof TABS)[number];
const LATER: Tab[] = ["graduations"];
// O Professor vê só o Resumo e os Responsáveis (RF-ALU-03, R26): matrícula, histórico e o que vier depois são da gestão.
const TEACHER_TABS: readonly Tab[] = ["summary", "guardians"];

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

function Header({ record, status, overdue, onEdit }: { record: StudentRecord; status?: EnrollmentStatus; overdue?: boolean; onEdit?: () => void }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-wrap items-center gap-3">
      {record.photoUrl ? (
        <img src={record.photoUrl} alt="" className="size-14 shrink-0 rounded-full object-cover" />
      ) : (
        <span aria-hidden="true" className="flex size-14 shrink-0 items-center justify-center rounded-full bg-muted font-semibold">
          {initials(record.name)}
        </span>
      )}
      <div className="min-w-0 flex-1">
        <p className="break-words text-lg font-semibold">{record.name}</p>
        <p className="text-sm text-muted-foreground">{t("students.age", { count: record.age })}</p>
        <div className="mt-1 flex flex-wrap gap-1">
          {status && <span className="rounded-md border px-2 py-0.5 text-sm font-medium">{t(`students.statuses.${status}`)}</span>}
          {overdue !== undefined && <span className="rounded-md border px-2 py-0.5 text-sm font-medium">{t(overdue ? "students.record.financial.overdue" : "students.record.financial.current")}</span>}
          {(record.seals ?? []).map((seal) => (
            <span key={seal} className="rounded-md border px-2 py-0.5 text-sm">
              {t(`students.seals.${seal}`)}
            </span>
          ))}
        </div>
      </div>
      <WhatsAppButton phone={record.contact?.phone ?? undefined} />
      {onEdit && (
        <Button type="button" variant="outline" className="min-h-11" onClick={onEdit}>
          {t("students.edit.button")}
        </Button>
      )}
    </div>
  );
}

// Abas com setas, Home e End (padrão WAI-ARIA): só a aba ativa entra na ordem de Tab.
function Tabs({ tab, tabs, onChange }: { tab: Tab; tabs: readonly Tab[]; onChange: (t: Tab) => void }) {
  const { t } = useTranslation();
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  function onKey(e: KeyboardEvent) {
    const i = tabs.indexOf(tab);
    const next = e.key === "ArrowRight" ? (i + 1) % tabs.length : e.key === "ArrowLeft" ? (i + tabs.length - 1) % tabs.length : e.key === "Home" ? 0 : e.key === "End" ? tabs.length - 1 : -1;
    if (next < 0) return;
    e.preventDefault();
    onChange(tabs[next]);
    refs.current[tabs[next]]?.focus();
  }
  return (
    <div role="tablist" aria-label={t("students.record.tabsLabel")} onKeyDown={onKey} className="flex flex-wrap gap-1 border-b">
      {tabs.map((id) => (
        <button
          key={id}
          ref={(el) => {
            refs.current[id] = el;
          }}
          role="tab"
          id={`record-tab-${id}`}
          type="button"
          aria-selected={tab === id}
          aria-controls={`record-panel-${id}`}
          tabIndex={tab === id ? 0 : -1}
          onClick={() => onChange(id)}
          className={`min-h-11 rounded-t-md px-3 text-sm ${tab === id ? "border-b-2 border-primary font-semibold" : "text-muted-foreground"}`}
        >
          {t(`students.record.tabs.${id}`)}
        </button>
      ))}
    </div>
  );
}

function Body({ id, onChanged }: { id: string; onChanged?: () => void }) {
  const { t } = useTranslation();
  const { status, data, reload } = useStudentRecord(id);
  const { permissions, status: permStatus } = usePermissions();
  // R18: a ficha que chega com `?pedido=` abre na matrícula, onde a troca pedida abre preenchida.
  const asked = useSearchParams()[0].has("pedido");
  const [tab, setTab] = useState<Tab>(asked ? "enrollment" : "summary");
  const [editing, setEditing] = useState(false);
  // Só a gestão vê o botão; a barreira é o servidor.
  const canEdit = !!permissions && permissions.STUDENT_MANAGE !== "NONE";
  const enrollment = useEnrollment(id, canEdit);
  // D6: só quem tem Cobranças lê as cobranças do aluno; o Professor nem faz o pedido.
  const canCollect = !!permissions && permissions.COLLECTIONS !== "NONE";
  const charges = useStudentCharges(id, canEdit && canCollect);
  if (status === "loading" || permStatus === "loading") return <LoadingState />;
  if (status === "forbidden") return <ForbiddenState />;
  if (status === "notFound") return <EmptyState title={t("students.record.notFound")} text={t("students.record.notFoundText")} />;
  if (status === "error" || !data) return <ErrorState onRetry={reload} />;
  return (
    <div className="flex flex-col gap-4">
      <Header record={data} status={enrollment.data?.status} overdue={charges.status === "ready" ? isOverdue(charges.data) : undefined} onEdit={canEdit ? () => setEditing(true) : undefined} />
      <StudentEditDialog
        studentId={id}
        open={editing}
        onClose={() => setEditing(false)}
        onSaved={() => {
          reload();
          onChanged?.();
        }}
      />
      <Tabs tab={tab} tabs={canEdit ? (canCollect ? TABS : TABS.filter((x) => x !== "financial")) : TEACHER_TABS} onChange={setTab} />
      <div role="tabpanel" id={`record-panel-${tab}`} aria-labelledby={`record-tab-${tab}`}>
        {tab === "summary" && <RecordSummary record={data} modalities={enrollment.data?.modalities} />}
        {tab === "enrollment" && <RecordEnrollment studentId={id} studentName={data.name} canGrant={!!permissions && permissions.BENEFITS !== "NONE"} enrollment={enrollment} onChanged={() => { reload(); onChanged?.(); }} />}
        {tab === "financial" && <StudentFinancialTab charges={charges} onChanged={() => onChanged?.()} />}
        {LATER.includes(tab) && <EmptyState title={t("students.record.empty.title")} text={t(`students.record.empty.${tab}`)} />}
        {tab === "guardians" && <RecordGuardians record={data} onChanged={reload} canManage={canEdit} />}
        {tab === "history" && <RecordHistory id={id} />}
      </div>
    </div>
  );
}

// RF-ALU-03: ficha em painel lateral (tela cheia no celular). O servidor decide o que cada perfil vê; aqui só se mostra o que veio.
export function StudentRecordPanel({ id, onClose, onChanged }: { id: string; onClose: () => void; onChanged?: () => void }) {
  const { t } = useTranslation();
  return (
    <SidePanel open onOpenChange={(open) => !open && onClose()} title={t("students.record.title")}>
      <Body key={id} id={id} onChanged={onChanged} />
    </SidePanel>
  );
}
