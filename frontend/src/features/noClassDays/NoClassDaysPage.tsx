import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { br } from "@/features/agenda/agendaDates";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { NoClassDayDialog } from "./NoClassDayDialog";
import { UndoNoClassDayDialog } from "./UndoNoClassDayDialog";
import { useNoClassDays, type NoClassDayItem, type Suggestion } from "./useNoClassDays";

type Dialog = { kind: "new"; suggestion?: Suggestion } | { kind: "edit"; item: NoClassDayItem } | { kind: "undo"; item: NoClassDayItem };

function Content() {
  const { t } = useTranslation();
  const { state } = useSession();
  const { permissions } = usePermissions();
  const [unitId, setUnitId] = useState("");
  const [dialog, setDialog] = useState<Dialog>();
  const { status, data, last, retry } = useNoClassDays(unitId);
  const options = data ?? last;
  const role = state.status === "ready" ? state.session.links.find((l) => l.id === state.session.activeLinkId)?.role : undefined;
  const canNotify = !!permissions && permissions.ANNOUNCEMENTS !== "NONE";
  const canManage = !!permissions && permissions.NO_CLASS_DAYS !== "NONE" && !!options?.unit;
  const period = (d: NoClassDayItem) => (d.startsOn === d.endsOn ? t("noClassDays.list.single", { date: br(d.startsOn) }) : t("noClassDays.list.period", { from: br(d.startsOn), to: br(d.endsOn) }));

  if (status === "forbidden") {
    return (
      <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-semibold">{t("noClassDays.title")}</h1>
        <ForbiddenState />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold">{t("noClassDays.title")}</h1>
        {canManage && (
          <Button className="min-h-11" onClick={() => setDialog({ kind: "new" })}>
            {t("noClassDays.new")}
          </Button>
        )}
      </div>
      <p className="text-muted-foreground">{t("noClassDays.intro")}</p>
      {options && options.units.length > 1 && (
        <label className="flex max-w-xs flex-col gap-1 text-sm">
          {t("noClassDays.unit")}
          <select className="min-h-11 rounded-md border bg-background px-2" value={unitId || options.unit?.id || ""} onChange={(e) => setUnitId(e.target.value)}>
            {options.units.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {status === "loading" && <LoadingState />}
      {status === "error" && <ErrorState onRetry={retry} />}
      {status === "ready" && data && (
        <>
          {canManage && data.suggestions.length > 0 && (
            <section aria-labelledby="suggestions-title" className="flex flex-col gap-2 rounded-md border p-3">
              <h2 id="suggestions-title" className="text-lg font-semibold">
                {t("noClassDays.suggestions.title")}
              </h2>
              <p className="text-sm text-muted-foreground">{t("noClassDays.suggestions.help")}</p>
              <ul className="flex max-h-64 flex-col gap-1 overflow-y-auto">
                {data.suggestions.map((s) => (
                  <li key={s.date} className="flex flex-wrap items-center justify-between gap-2">
                    <span>
                      {br(s.date)} · {t(`noClassDays.holidays.${s.code}`)}
                    </span>
                    <Button
                      variant="outline"
                      className="min-h-11"
                      aria-label={t("noClassDays.suggestions.confirmLabel", { name: t(`noClassDays.holidays.${s.code}`), date: br(s.date) })}
                      onClick={() => setDialog({ kind: "new", suggestion: s })}
                    >
                      {t("noClassDays.suggestions.confirm")}
                    </Button>
                  </li>
                ))}
              </ul>
            </section>
          )}
          {data.days.length === 0 ? (
            <EmptyState title={t("noClassDays.emptyTitle")} text={t("noClassDays.emptyText")} />
          ) : (
            <section aria-labelledby="days-title" className="flex flex-col gap-2">
              <h2 id="days-title" className="text-lg font-semibold">
                {t("noClassDays.list.title")}
              </h2>
              <ul className="flex flex-col gap-2">
                {data.days.map((d) => (
                  <li key={d.id} className="flex flex-col gap-1 rounded-md border p-3">
                    <p className="break-words font-medium">
                      {period(d)} · {d.reason}
                    </p>
                    <p className="text-sm">{t("noClassDays.list.origin", { origin: t(`noClassDays.origins.${d.origin}`) })}</p>
                    <p className="break-words text-sm">{d.allClasses ? t("noClassDays.list.allClasses") : t("noClassDays.list.classes", { names: d.classes.map((c) => c.name).join(", ") })}</p>
                    <p className="text-sm">{t("noClassDays.list.notice", { status: t(`noClassDays.noticeStatus.${d.notice.status}`) })}</p>
                    <p className="text-sm">{t("noClassDays.list.cancelled", { count: d.cancelledLessons })}</p>
                    {canNotify && d.notice.status === "NOT_NOTIFIED" && (
                      <Button variant="outline" className="min-h-11 self-start" asChild>
                        <Link to={`/comunicados/novo?${new URLSearchParams({ type: "NO_CLASS_DAY", unitId: d.unit.id, startsOn: d.startsOn, endsOn: d.endsOn, reason: d.reason, noClassDayId: d.id })}`}>{t("noClassDays.list.notify")}</Link>
                      </Button>
                    )}
                    {canManage && d.canEdit && (
                      <div className="flex flex-wrap gap-2">
                        <Button variant="outline" className="min-h-11" onClick={() => setDialog({ kind: "edit", item: d })}>
                          {t("noClassDays.list.edit")}
                        </Button>
                        <Button variant="outline" className="min-h-11" onClick={() => setDialog({ kind: "undo", item: d })}>
                          {t("noClassDays.list.undo")}
                        </Button>
                      </div>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}
      {dialog && options?.unit && dialog.kind !== "undo" && (
        <NoClassDayDialog
          key={dialog.kind === "edit" ? dialog.item.id : (dialog.suggestion?.date ?? "new")}
          units={options.units}
          unit={options.unit}
          multi={role === "COORDINATOR"}
          item={dialog.kind === "edit" ? dialog.item : undefined}
          suggestion={dialog.kind === "new" ? dialog.suggestion : undefined}
          onClose={() => setDialog(undefined)}
          onSaved={retry}
        />
      )}
      {dialog?.kind === "undo" && <UndoNoClassDayDialog item={dialog.item} period={period(dialog.item)} onClose={() => setDialog(undefined)} onDone={retry} />}
    </div>
  );
}

// RF-AGE-05: a barreira de dados é o servidor (403 vira "sem permissão" na própria tela).
export function NoClassDaysPage() {
  return (
    <ManagementLayout activeId="noClassDays">
      <Content />
    </ManagementLayout>
  );
}
