import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { addDays, br, mondayOf, todayIso, weekday, weekDays } from "./agendaDates";
import { AgendaDayList } from "./AgendaDayList";
import { AgendaFilters, type FilterValues } from "./AgendaFilters";
import { AgendaWeekGrid } from "./AgendaWeekGrid";
import { NewLessonDialog } from "./NewLessonDialog";
import { useAgenda } from "./useAgenda";
import { PageHeader } from "@/components/ui/page-header";

const narrow = () => window.innerWidth < 768;

// Até 767 px a mesma página mostra a lista do dia (R14).
function useNarrow() {
  const [value, setValue] = useState(narrow);
  useEffect(() => {
    const on = () => setValue(narrow());
    window.addEventListener("resize", on);
    return () => window.removeEventListener("resize", on);
  }, []);
  return value;
}

function Content() {
  const { t } = useTranslation();
  const isNarrow = useNarrow();
  const [anchor, setAnchor] = useState(todayIso);
  const [creating, setCreating] = useState(false);
  const { permissions } = usePermissions();
  const [filters, setFilters] = useState<FilterValues>({ unitId: "", teacherId: "", modalityId: "" });
  const days = weekDays(anchor);
  const { status, data, last, retry } = useAgenda({ from: days[0], to: days[6], ...filters });
  const canCreate = !!permissions && permissions.AGENDA_EDIT !== "NONE" && !!(data ?? last)?.unit;
  const step = isNarrow ? 1 : 7;
  const today = todayIso();
  const label = isNarrow
    ? t("agenda.dayLabel", { day: t(`agenda.weekdaysLong.${weekday(anchor)}`), date: br(anchor) })
    : t("agenda.weekLabel", { from: br(days[0]), to: br(days[6]) });

  if (status === "forbidden") {
    return (
      <div className="flex flex-col gap-4">
        <PageHeader title={t("agenda.title")} />
        <ForbiddenState />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={t("agenda.title")}
        actions={canCreate && (
          <Button className="min-h-11" onClick={() => setCreating(true)}>
            {t("agenda.new.open")}
          </Button>
        )}
      />
      <p className="text-muted-foreground">{t("agenda.intro")}</p>
      <div className="flex flex-wrap items-center gap-2">
        <Button variant="outline" className="min-h-11" aria-label={t(isNarrow ? "agenda.prevDay" : "agenda.prevWeek")} onClick={() => setAnchor(addDays(anchor, -step))}>
          ‹
        </Button>
        <Button variant="outline" className="min-h-11" aria-label={t(isNarrow ? "agenda.nextDay" : "agenda.nextWeek")} onClick={() => setAnchor(addDays(anchor, step))}>
          ›
        </Button>
        <Button variant="outline" className="min-h-11" onClick={() => setAnchor(today)}>
          {t("agenda.today")}
        </Button>
        <Button variant="outline" className="min-h-11" onClick={() => setAnchor(mondayOf(anchor) === mondayOf(today) ? anchor : today)}>
          {t("agenda.thisWeek")}
        </Button>
        <span aria-live="polite" className="font-medium">
          {label}
        </span>
      </div>
      <AgendaFilters options={data ?? last} values={filters} onChange={(v) => setFilters((f) => ({ ...f, ...v }))} />
      {creating && canCreate && (
        <NewLessonDialog
          unit={(data ?? last)!.unit!}
          modalities={(data ?? last)!.modalities}
          teachers={(data ?? last)!.teachers}
          onClose={() => setCreating(false)}
          onSaved={retry}
        />
      )}
      {status === "loading" && <LoadingState />}
      {status === "error" && <ErrorState onRetry={retry} />}
      {status === "ready" && data && isNarrow && <AgendaDayList data={data} day={anchor} />}
      {status === "ready" && data && !isNarrow && data.lessons.length === 0 && data.noClassDays.length === 0 && (
        <EmptyState title={t("agenda.emptyTitle")} text={t("agenda.emptyText")} />
      )}
      {status === "ready" && data && !isNarrow && (data.lessons.length > 0 || data.noClassDays.length > 0) && <AgendaWeekGrid data={data} anchor={anchor} />}
    </div>
  );
}

// RF-AGE-01: a barreira de dados é o servidor (403 vira "sem permissão" na própria tela).
export function AgendaPage() {
  return (
    <ManagementLayout activeId="agenda">
      <Content />
    </ManagementLayout>
  );
}
