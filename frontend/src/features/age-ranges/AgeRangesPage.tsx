import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { AgeRangeDialog } from "./AgeRangeDialog";
import { useAgeRanges, type AgeRange } from "./useAgeRanges";
import { PageHeader } from "@/components/ui/page-header";

// Aberta com `target`: undefined = fechada, null = nova faixa, faixa = edição. `n` renova a janela a cada abertura.
type Editing = { n: number; target: AgeRange | null };

function Content() {
  const { t } = useTranslation();
  const api = useAgeRanges();
  const [editing, setEditing] = useState<Editing>();
  const [removing, setRemoving] = useState<AgeRange>();
  const [message, setMessage] = useState<{ kind: "status" | "alert"; text: string }>();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.items) return <ErrorState onRetry={api.retry} />;

  const open = (target: AgeRange | null) => {
    setMessage(undefined);
    setEditing({ n: Date.now(), target });
  };
  const ages = (r: AgeRange) =>
    r.maxAge === null ? t("ageRanges.openEnded", { count: r.minAge }) : t("ageRanges.range", { min: r.minAge, max: r.maxAge });

  const create = api.canManage && (
    <Button size="sm" onClick={() => open(null)}>
      {t("ageRanges.create")}
    </Button>
  );

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("ageRanges.title")} />
      <p className="text-muted-foreground">{t("ageRanges.intro")}</p>
      {message && <Banner tone={message.kind === "alert" ? "danger" : "ok"}>{message.text}</Banner>}
      {api.items.length === 0 ? (
        <EmptyState title={t("ageRanges.emptyTitle")} text={t("ageRanges.emptyText")} action={create || undefined} />
      ) : (
        <Card heading="h2" title={t("ageRanges.listTitle")} actions={create || undefined}>
          <ul className="flex flex-col divide-y">
            {api.items.map((r) => (
              <li key={r.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                <span className="min-w-0 break-words">
                  <span className="font-medium">{r.name}</span>
                  <span className="text-muted-foreground"> · {ages(r)}</span>
                </span>
                {api.canManage && (
                  <span className="flex flex-wrap gap-2">
                    <Button variant="outline" size="sm" onClick={() => open(r)}>
                      {t("ageRanges.edit")}
                      <span className="sr-only"> {r.name}</span>
                    </Button>
                    <Button variant="outline" size="sm" onClick={() => setRemoving(r)}>
                      {t("ageRanges.delete")}
                      <span className="sr-only"> {r.name}</span>
                    </Button>
                  </span>
                )}
              </li>
            ))}
          </ul>
        </Card>
      )}
      {editing && (
        <AgeRangeDialog
          key={editing.n}
          range={editing.target}
          open
          onClose={() => setEditing(undefined)}
          onSave={async (input) => {
            await (editing.target ? api.update(editing.target.id, input) : api.create(input));
            setMessage({ kind: "status", text: t("ageRanges.saved") });
          }}
        />
      )}
      <ConfirmDialog
        open={removing !== undefined}
        onOpenChange={(o) => !o && setRemoving(undefined)}
        title={t("ageRanges.confirm.title")}
        summary={t("ageRanges.confirm.summary", { name: removing?.name })}
        confirmLabel={t("ageRanges.delete")}
        onConfirm={async () => {
          if (!removing) return;
          try {
            await api.remove(removing.id);
            setMessage({ kind: "status", text: t("ageRanges.deleted", { name: removing.name }) });
          } catch {
            setMessage({ kind: "alert", text: t("ageRanges.deleteError", { name: removing.name }) });
          }
        }}
      />
    </div>
  );
}

// RF-MOD-04: Coordenador altera; Administrador só consulta (canManage). A barreira de dados é o servidor.
export function AgeRangesPage() {
  return (
    <ManagementLayout activeId="ageRanges">
      <Content />
    </ManagementLayout>
  );
}
