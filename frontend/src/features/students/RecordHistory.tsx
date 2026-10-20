import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { formatDateTime } from "@/lib/format";
import { useStudentHistory } from "./useStudentRecord";

// RF-ALU-03: auditoria do aluno em texto, da mais recente para a mais antiga; sem valores antes e depois (D6).
export function RecordHistory({ id }: { id: string }) {
  const { t, i18n } = useTranslation();
  const [page, setPage] = useState(1);
  const { status, data, reload } = useStudentHistory(id, page);
  if (status === "loading") return <LoadingState />;
  if (status === "forbidden") return <ForbiddenState />;
  if (status === "error" || status === "notFound" || !data) return <ErrorState onRetry={reload} />;
  if (data.total === 0) return <EmptyState text={t("students.record.history.empty")} />;
  // O código da ação tem ponto; a chave usa sublinhado. Ação desconhecida mostra o código.
  const label = (action: string) => {
    const key = `students.record.history.actions.${action.replaceAll(".", "_")}`;
    return i18n.exists(key) ? t(key) : action;
  };
  const pages = Math.ceil(data.total / data.size);
  return (
    <div className="flex flex-col gap-3">
      <ol className="flex flex-col gap-3">
        {data.items.map((e, i) => (
          <li key={`${e.at}-${i}`} className="rounded-md border p-2">
            <p className="font-medium">{label(e.action)}</p>
            <p className="text-sm text-muted-foreground">
              {formatDateTime(e.at)}
              {e.actor && ` · ${t("students.record.history.by", { actor: e.actor })}`}
            </p>
            {e.reason && <p className="break-words text-sm">{t("students.record.history.reason", { reason: e.reason })}</p>}
          </li>
        ))}
      </ol>
      {pages > 1 && (
        <div className="flex items-center justify-between gap-2">
          <Button variant="outline" className="min-h-11" disabled={page <= 1} onClick={() => setPage(page - 1)}>
            {t("students.record.history.prev")}
          </Button>
          <span className="text-sm">{t("query.page", { page, pages })}</span>
          <Button variant="outline" className="min-h-11" disabled={page >= pages} onClick={() => setPage(page + 1)}>
            {t("students.record.history.next")}
          </Button>
        </div>
      )}
    </div>
  );
}
