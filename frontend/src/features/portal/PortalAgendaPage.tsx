import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useSearchParams } from "react-router";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { addDays, weekDays } from "@/features/agenda/agendaDates";
import { formatDate } from "@/lib/format";
import { PortalAgendaDayList } from "./PortalAgendaDayList";
import { PortalAgendaGrid } from "./PortalAgendaGrid";
import { PortalLayout } from "./PortalLayout";
import { PortalTag } from "./PortalTag";
import { usePortalSchedule, type PortalScheduleData, type ScheduleScope } from "./usePortalSchedule";

const WIDE = "(min-width: 768px)";

// D7: grade a partir de md e lista do dia abaixo; uma só visão no DOM, para o axe e o leitor de tela não lerem o conteúdo duas vezes.
function useWide() {
  const [wide, setWide] = useState(() => window.matchMedia?.(WIDE).matches ?? false);
  useEffect(() => {
    const m = window.matchMedia?.(WIDE);
    if (!m) return;
    const on = () => setWide(m.matches);
    on();
    m.addEventListener("change", on);
    return () => m.removeEventListener("change", on);
  }, []);
  return wide;
}

// RF-POR-04: agenda da semana do aluno em `?aluno=`, `?semana=` e `?escopo=` (padrão: primeiro cartão, semana de hoje, todos os horários). Só consulta.
export function PortalAgendaPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const student = params.get("aluno");
  const week = params.get("semana");
  const scope: ScheduleScope = params.get("escopo") === "MAIN" ? "MAIN" : "ALL";
  const wide = useWide();
  const [picked, setPicked] = useState<string>();
  const { status, data, reload } = usePortalSchedule(student, week, scope);
  // Os controles ficam na tela enquanto a semana nova carrega.
  const [last, setLast] = useState<PortalScheduleData>();
  if (data && data !== last) setLast(data);
  const shown = data ?? last;

  const change = (patch: Record<string, string | null>) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === null) next.delete(k);
      else next.set(k, v);
    }
    setParams(next, { replace: true });
  };

  return (
    <PortalLayout>
      <h1 className="mb-4 text-2xl font-semibold">{t("portalAgenda.title")}</h1>
      {shown && (
        <div className="mb-4 flex flex-col gap-3">
          {shown.students.length > 1 && (
            <label className="flex flex-col gap-1">
              <span className="text-sm font-medium">{t("portalAgenda.student")}</span>
              <select className="min-h-11 rounded-md border bg-background px-2" value={student ?? shown.studentId} onChange={(e) => change({ aluno: e.target.value })}>
                {shown.students.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.self ? t("portalAgenda.you") : s.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <div role="group" aria-label={t("portalAgenda.scopeLabel")} className="flex flex-wrap gap-2">
            {(["MAIN", "ALL"] as const).map((s) => (
              <button
                key={s}
                type="button"
                aria-pressed={scope === s}
                className={`min-h-11 rounded-md border px-3 ${scope === s ? "bg-primary text-primary-foreground" : "bg-background"}`}
                onClick={() => change({ escopo: s === "MAIN" ? "MAIN" : null })}
              >
                {t(s === "MAIN" ? "portalAgenda.scopeMain" : "portalAgenda.scopeAll")}
              </button>
            ))}
          </div>
          <nav aria-label={t("portalAgenda.weekNav")} className="flex flex-wrap items-center gap-2">
            <button type="button" className="min-h-11 rounded-md border px-3" onClick={() => change({ semana: addDays(shown.weekStart, -7) })}>
              {t("portalAgenda.prevWeek")}
            </button>
            <button type="button" className="min-h-11 rounded-md border px-3" onClick={() => change({ semana: null })}>
              {t("portalAgenda.today")}
            </button>
            <button type="button" className="min-h-11 rounded-md border px-3" onClick={() => change({ semana: addDays(shown.weekStart, 7) })}>
              {t("portalAgenda.nextWeek")}
            </button>
            <p className="font-medium" aria-live="polite">
              {t("portalAgenda.weekRange", { start: formatDate(shown.weekStart), end: formatDate(shown.weekEnd) })}
            </p>
          </nav>
          <ul aria-label={t("portalAgenda.legendTitle")} className="flex flex-wrap gap-2">
            {(["MAIN", "OTHER", "ADHOC"] as const).map((k) => (
              <li key={k}>
                <PortalTag>{t(`portalAgenda.kind.${k}`)}</PortalTag>
              </li>
            ))}
            <li>
              <PortalTag tone="strong">{t("portalAgenda.changed")}</PortalTag>
            </li>
            <li>
              <PortalTag tone="alert">{t("portalAgenda.cancelled")}</PortalTag>
            </li>
            <li>
              <PortalTag>{t("portalAgenda.noClass")}</PortalTag>
            </li>
          </ul>
        </div>
      )}
      {status === "loading" || status === "idle" ? (
        <LoadingState />
      ) : status === "forbidden" ? (
        <ForbiddenState />
      ) : status === "ready" && data ? (
        data.lessons.length === 0 && data.noClassDays.length === 0 ? (
          <EmptyState title={t("portalAgenda.emptyTitle")} text={t("portalAgenda.emptyText")} />
        ) : wide ? (
          <PortalAgendaGrid data={data} />
        ) : (
          <PortalAgendaDayList data={data} day={dayOf(data, picked)} onDay={setPicked} />
        )
      ) : (
        <ErrorState onRetry={reload} />
      )}
    </PortalLayout>
  );
}

// Começa em hoje, ou na segunda se a semana não tem hoje.
function dayOf(data: PortalScheduleData, picked?: string) {
  const days = weekDays(data.weekStart);
  return picked && days.includes(picked) ? picked : days.includes(data.today) ? data.today : data.weekStart;
}
