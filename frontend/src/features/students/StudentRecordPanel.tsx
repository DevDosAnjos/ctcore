import { useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { SidePanel } from "@/components/SidePanel";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { WhatsAppButton } from "@/components/whatsapp";
import { RecordGuardians } from "./RecordGuardians";
import { RecordHistory } from "./RecordHistory";
import { RecordSummary } from "./RecordSummary";
import { useStudentRecord } from "./useStudentRecord";
import type { StudentRecord } from "./types";

const TABS = ["summary", "enrollment", "financial", "graduations", "guardians", "history"] as const;
type Tab = (typeof TABS)[number];
const LATER: Tab[] = ["enrollment", "financial", "graduations"];

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

function Header({ record }: { record: StudentRecord }) {
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
          {(record.seals ?? []).map((seal) => (
            <span key={seal} className="rounded-md border px-2 py-0.5 text-sm">
              {t(`students.seals.${seal}`)}
            </span>
          ))}
        </div>
      </div>
      <WhatsAppButton phone={record.contact?.phone ?? undefined} />
    </div>
  );
}

// Abas com setas, Home e End (padrão WAI-ARIA): só a aba ativa entra na ordem de Tab.
function Tabs({ tab, onChange }: { tab: Tab; onChange: (t: Tab) => void }) {
  const { t } = useTranslation();
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  function onKey(e: KeyboardEvent) {
    const i = TABS.indexOf(tab);
    const next = e.key === "ArrowRight" ? (i + 1) % TABS.length : e.key === "ArrowLeft" ? (i + TABS.length - 1) % TABS.length : e.key === "Home" ? 0 : e.key === "End" ? TABS.length - 1 : -1;
    if (next < 0) return;
    e.preventDefault();
    onChange(TABS[next]);
    refs.current[TABS[next]]?.focus();
  }
  return (
    <div role="tablist" aria-label={t("students.record.tabsLabel")} onKeyDown={onKey} className="flex flex-wrap gap-1 border-b">
      {TABS.map((id) => (
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

function Body({ id }: { id: string }) {
  const { t } = useTranslation();
  const { status, data, reload } = useStudentRecord(id);
  const [tab, setTab] = useState<Tab>("summary");
  if (status === "loading") return <LoadingState />;
  if (status === "forbidden") return <ForbiddenState />;
  if (status === "notFound") return <EmptyState title={t("students.record.notFound")} text={t("students.record.notFoundText")} />;
  if (status === "error" || !data) return <ErrorState onRetry={reload} />;
  return (
    <div className="flex flex-col gap-4">
      <Header record={data} />
      <Tabs tab={tab} onChange={setTab} />
      <div role="tabpanel" id={`record-panel-${tab}`} aria-labelledby={`record-tab-${tab}`}>
        {tab === "summary" && <RecordSummary record={data} />}
        {LATER.includes(tab) && <EmptyState title={t("students.record.empty.title")} text={t(`students.record.empty.${tab}`)} />}
        {tab === "guardians" && <RecordGuardians record={data} onChanged={reload} />}
        {tab === "history" && <RecordHistory id={id} />}
      </div>
    </div>
  );
}

// RF-ALU-03: ficha em painel lateral (tela cheia no celular). O servidor decide o que cada perfil vê; aqui só se mostra o que veio.
export function StudentRecordPanel({ id, onClose }: { id: string; onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <SidePanel open onOpenChange={(open) => !open && onClose()} title={t("students.record.title")}>
      <Body key={id} id={id} />
    </SidePanel>
  );
}
