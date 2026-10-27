import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { PortalAnnouncementDialog } from "./PortalAnnouncementDialog";
import { PortalAnnouncementItem } from "./PortalAnnouncementItem";
import { PortalLayout } from "./PortalLayout";
import { markAllPortalAnnouncementsRead, usePortalAnnouncements, type AnnouncementFilter, type PortalAnnouncementsData } from "./usePortalAnnouncements";

const SLUG = { ALL: "todos", UNREAD: "nao-lidos", MODALITY: "modalidade" } as const;
const FILTERS: AnnouncementFilter[] = ["ALL", "UNREAD", "MODALITY"];
const button = (on: boolean) => `min-h-11 rounded-md border px-3 ${on ? "bg-primary text-primary-foreground" : "bg-background"}`;

// RF-POR-06: avisos do aluno em `?aluno=`, `?filtro=`, `?modalidade=`, `?pagina=` e `?aviso=` (aberto). Abrir grava a leitura; "Marcar todos" só limpa a lista e o sino.
export function PortalAnnouncementsPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const student = params.get("aluno");
  const slug = params.get("filtro");
  const filter = FILTERS.find((f) => SLUG[f] === slug) ?? "ALL";
  const modality = params.get("modalidade");
  const page = Math.max(Number(params.get("pagina")) || 1, 1);
  const opened = params.get("aviso");
  const { status, data, reload } = usePortalAnnouncements(student, filter, modality, page);
  // Os controles ficam na tela enquanto a lista nova carrega.
  const [last, setLast] = useState<PortalAnnouncementsData>();
  if (data && data !== last) setLast(data);
  const shown = data ?? last;
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ ok: boolean; text: string }>();

  const change = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === null) next.delete(k);
      else next.set(k, v);
    }
    setParams(next, { replace: true });
  };

  async function markAll() {
    if (busy) return;
    setBusy(true);
    setMessage(undefined);
    try {
      const count = await markAllPortalAnnouncementsRead();
      setMessage({ ok: true, text: t("portalAnnouncements.cleared", { count }) });
      reload();
    } catch {
      setMessage({ ok: false, text: t("portalAnnouncements.clearFailed") });
    } finally {
      setBusy(false);
    }
  }

  const pages = shown ? Math.max(Math.ceil(shown.total / shown.size), 1) : 1;
  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t("portalAnnouncements.title")}</h1>
      {shown && !shown.reduced && (
        <div className="mb-4 flex flex-col gap-3">
          {shown.students.length > 1 && (
            <label className="flex flex-col gap-1">
              <span className="text-sm font-medium">{t("portalAnnouncements.student")}</span>
              <select
                className="min-h-11 rounded-md border bg-background px-2"
                value={student ?? shown.studentId}
                onChange={(e) => change({ aluno: e.target.value, pagina: null, modalidade: null, filtro: filter === "MODALITY" ? null : slug })}
              >
                {shown.students.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.self ? t("portalAnnouncements.you") : s.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div role="group" aria-label={t("portalAnnouncements.filterLabel")} className="flex flex-wrap gap-2">
            {FILTERS.map((f) => (
              <button
                key={f}
                type="button"
                aria-pressed={filter === f}
                className={button(filter === f)}
                onClick={() => change({ filtro: f === "ALL" ? null : SLUG[f], modalidade: f === "MODALITY" ? (modality ?? shown.modalities[0]?.id ?? null) : null, pagina: null })}
              >
                {t(`portalAnnouncements.filter.${f}`)}
              </button>
            ))}
          </div>
          {filter === "MODALITY" && (
            <label className="flex flex-col gap-1">
              <span className="text-sm font-medium">{t("portalAnnouncements.modality")}</span>
              <select className="min-h-11 rounded-md border bg-background px-2" value={modality ?? shown.modalities[0]?.id ?? ""} onChange={(e) => change({ modalidade: e.target.value, pagina: null })}>
                {shown.modalities.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" className="min-h-11 rounded-md border px-3 disabled:opacity-50" disabled={busy || shown.unreadCount === 0} onClick={() => void markAll()}>
              {t("portalAnnouncements.markAll")}
            </button>
            {message && (
              <p role={message.ok ? "status" : "alert"} className={`text-sm ${message.ok ? "" : "text-destructive"}`}>
                {message.text}
              </p>
            )}
          </div>
        </div>
      )}
      {status === "loading" || status === "idle" ? (
        <LoadingState />
      ) : status === "forbidden" ? (
        <ForbiddenState />
      ) : status === "ready" && data ? (
        data.items.length === 0 ? (
          filter === "UNREAD" ? (
            <EmptyState title={t("portalAnnouncements.emptyUnreadTitle")} text={t("portalAnnouncements.emptyUnreadText")} />
          ) : (
            <EmptyState title={t("portalAnnouncements.emptyTitle")} text={t("portalAnnouncements.emptyText")} />
          )
        ) : (
          <>
            <ul aria-label={t("portalAnnouncements.listLabel")} className="flex flex-col gap-2">
              {data.items.map((n) => (
                <PortalAnnouncementItem key={n.id} item={n} onOpen={(id) => change({ aviso: id })} />
              ))}
            </ul>
            {pages > 1 && (
              <nav aria-label={t("portalAnnouncements.pageNav")} className="mt-4 flex flex-wrap items-center gap-2">
                <button type="button" className="min-h-11 rounded-md border px-3 disabled:opacity-50" disabled={page <= 1} onClick={() => change({ pagina: page - 1 > 1 ? String(page - 1) : null })}>
                  {t("portalAnnouncements.prev")}
                </button>
                <span aria-live="polite">{t("portalAnnouncements.pageOf", { page, pages })}</span>
                <button type="button" className="min-h-11 rounded-md border px-3 disabled:opacity-50" disabled={page >= pages} onClick={() => change({ pagina: String(page + 1) })}>
                  {t("portalAnnouncements.next")}
                </button>
              </nav>
            )}
          </>
        )
      ) : (
        <ErrorState onRetry={reload} />
      )}
      {opened && <PortalAnnouncementDialog id={opened} studentId={student} onClose={() => change({ aviso: null })} onRead={reload} />}
    </PortalLayout>
  );
}
