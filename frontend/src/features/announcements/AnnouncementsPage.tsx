import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useLocation } from "react-router";
import { Button } from "@/components/ui/button";
import { QueryPage } from "@/components/query/QueryPage";
import type { Column } from "@/components/query/DataTable";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatDateTime } from "@/lib/format";
import { AnnouncementFilters } from "./AnnouncementFilters";
import { CancelAnnouncementDialog } from "./CancelAnnouncementDialog";
import type { AnnouncementItem } from "./types";
import { fetchAnnouncements } from "./useAnnouncements";

function useCells() {
  const { t } = useTranslation();
  return {
    target: (a: AnnouncementItem) => t(`announcements.target.${a.target.kind}`, { name: a.target.name }),
    recipients: (a: AnnouncementItem) => (a.recipients === null ? t("format.empty") : String(a.recipients)),
    reads: (a: AnnouncementItem) => (a.status === "SCHEDULED" ? t("format.empty") : String(a.reads)),
  };
}

function CancelButton({ row, onCancel }: { row: AnnouncementItem; onCancel: (a: AnnouncementItem) => void }) {
  const { t } = useTranslation();
  if (row.status !== "SCHEDULED") return null;
  return (
    <Button type="button" variant="outline" className="min-h-11" aria-label={t("announcements.cancel.actionFor", { title: row.title })} onClick={() => onCancel(row)}>
      {t("announcements.cancel.action")}
    </Button>
  );
}

function Card({ row, onCancel }: { row: AnnouncementItem; onCancel: (a: AnnouncementItem) => void }) {
  const { t } = useTranslation();
  const cells = useCells();
  return (
    <div className="flex flex-col gap-2">
      <p className="break-words font-medium">{row.title}</p>
      <dl className="grid grid-cols-2 gap-x-3 gap-y-1 text-sm">
        <dt className="text-muted-foreground">{t("announcements.columns.date")}</dt>
        <dd>{formatDateTime(row.date)}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.target")}</dt>
        <dd className="break-words">{cells.target(row)}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.type")}</dt>
        <dd>{t(`announcements.types.${row.type}`)}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.author")}</dt>
        <dd className="break-words">{row.author}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.status")}</dt>
        <dd>{t(`announcements.status.${row.status}`)}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.recipients")}</dt>
        <dd>{cells.recipients(row)}</dd>
        <dt className="text-muted-foreground">{t("announcements.columns.reads")}</dt>
        <dd>{cells.reads(row)}</dd>
      </dl>
      <CancelButton row={row} onCancel={onCancel} />
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const cells = useCells();
  const [cancelling, setCancelling] = useState<AnnouncementItem | null>(null);
  const [version, setVersion] = useState(0);
  // Confirmação vinda de /comunicados/novo (RF-COM-02): o estado da navegação traz o resultado e o link do grupo, se o WhatsApp foi escolhido.
  const sent = (useLocation().state as { sent?: { status: "SENT" | "SCHEDULED"; groupLink?: string | null } } | null)?.sent;

  const columns: Column<AnnouncementItem>[] = [
    { key: "date", label: t("announcements.columns.date"), sortable: true, render: (a) => formatDateTime(a.date) },
    { key: "target", label: t("announcements.columns.target"), render: cells.target },
    { key: "title", label: t("announcements.columns.title"), sortable: true, render: (a) => a.title },
    { key: "type", label: t("announcements.columns.type"), sortable: true, render: (a) => t(`announcements.types.${a.type}`) },
    { key: "author", label: t("announcements.columns.author"), render: (a) => a.author },
    { key: "status", label: t("announcements.columns.status"), render: (a) => t(`announcements.status.${a.status}`) },
    { key: "recipients", label: t("announcements.columns.recipients"), render: cells.recipients },
    { key: "reads", label: t("announcements.columns.reads"), render: cells.reads },
    { key: "actions", label: t("announcements.columns.actions"), render: (a) => <CancelButton row={a} onCancel={setCancelling} /> },
  ];
  const newButton = (
    <Button asChild className="min-h-11">
      <Link to="/comunicados/novo">{t("announcements.newAction")}</Link>
    </Button>
  );

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t("announcements.title")}</h1>
        {newButton}
      </div>
      {sent && (
        <p role="status" className="flex flex-wrap items-center gap-3 rounded-md border p-3">
          {t(sent.status === "SENT" ? "announcements.sentNotice" : "announcements.scheduledNotice")}
          {sent.groupLink && (
            <a className="underline" href={sent.groupLink} target="_blank" rel="noopener noreferrer">
              {t("announcements.openGroup")}
            </a>
          )}
        </p>
      )}
      <p className="text-muted-foreground">{t("announcements.intro")}</p>
      <QueryPage
        key={version}
        columns={columns}
        fetchPage={fetchAnnouncements}
        rowKey={(a) => a.id}
        filterKeys={["from", "to", "classId", "type"]}
        filters={({ get, set }) => <AnnouncementFilters get={get} set={set} />}
        emptyAction={newButton}
        texts={{ emptyTitle: t("announcements.emptyTitle"), emptyText: t("announcements.emptyText"), noResultTitle: t("announcements.noResultTitle"), noResultText: t("announcements.noResultText") }}
        cards={(a) => <Card row={a} onCancel={setCancelling} />}
      />
      {cancelling && <CancelAnnouncementDialog announcement={cancelling} onClose={() => setCancelling(null)} onDone={() => setVersion((v) => v + 1)} />}
    </div>
  );
}

// RF-COM-01: lista de comunicados da gestão; a barreira de dados é o servidor (403 vira "sem permissão" na lista).
export function AnnouncementsPage() {
  return (
    <ManagementLayout activeId="announcements">
      <Content />
    </ManagementLayout>
  );
}
