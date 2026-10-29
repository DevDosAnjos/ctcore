import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { PageHeader } from "@/components/ui/page-header";
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingState,
} from "@/components/states";
import { Banner } from "@/components/ui/banner";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { GraduationDialog } from "./GraduationDialog";
import { RequestModalityDialog } from "./RequestModalityDialog";
import { useModalities, type Modality } from "./useModalities";

function Block({
  title,
  empty,
  items,
  onToggle,
  onGraduation,
}: {
  title: string;
  empty: string;
  items: Modality[];
  onToggle: (m: Modality) => void;
  onGraduation?: (m: Modality) => void;
}) {
  const { t } = useTranslation();
  return (
    <Card heading="h2" title={title}>
      {items.length === 0 ? (
        <p className="text-muted-foreground">{empty}</p>
      ) : (
        <ul className="flex flex-col divide-y">
          {items.map((m) => (
            <li
              key={m.id}
              className="flex flex-wrap items-center justify-between gap-2 py-2"
            >
              <span className="min-w-0 break-words">{m.name}</span>
              <span className="flex flex-wrap gap-2">
                {m.active && onGraduation && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onGraduation(m)}
                  >
                    {m.canManage
                      ? t("modalities.graduation.button")
                      : t("modalities.graduation.view")}
                    <span className="sr-only"> {m.name}</span>
                  </Button>
                )}
                {m.canManage && (
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onToggle(m)}
                  >
                    {m.active
                      ? t("modalities.deactivate")
                      : t("modalities.activate")}
                    <span className="sr-only"> {m.name}</span>
                  </Button>
                )}
              </span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

function Content() {
  const { t } = useTranslation();
  const api = useModalities();
  const [asking, setAsking] = useState(false);
  const [grading, setGrading] = useState<Modality | null>(null);
  const [message, setMessage] = useState<{
    kind: "status" | "alert";
    text: string;
  }>();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.items) return <ErrorState onRetry={api.retry} />;
  const items = api.items;

  async function toggle(m: Modality) {
    setMessage(undefined);
    try {
      await api.setActive(m.id, !m.active);
    } catch (e) {
      const fields = e instanceof ApiError ? e.fields : [];
      const count = (name: string) =>
        fields.find((f) => f.field === name)?.message ?? "0";
      setMessage({
        kind: "alert",
        text:
          e instanceof ApiError && e.code === "MODALITY_IN_USE"
            ? t("modalities.inUse", {
                name: m.name,
                classes: count("activeClasses"),
                students: count("activeStudents"),
              })
            : t("modalities.changeError", { name: m.name }),
      });
    }
  }

  const canManage = items.some((m) => m.canManage);
  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("modalities.title")} />
      {message && <Banner tone={message.kind === "alert" ? "danger" : "ok"}>{message.text}</Banner>}
      {items.length === 0 ? (
        <EmptyState
          title={t("modalities.emptyTitle")}
          text={t("modalities.emptyText")}
        />
      ) : (
        <>
          <Block
            title={t("modalities.mine")}
            empty={t("modalities.mineEmpty")}
            items={items.filter((m) => m.active)}
            onToggle={toggle}
            onGraduation={(m) => {
              setMessage(undefined);
              setGrading(m);
            }}
          />
          <Block
            title={t("modalities.available")}
            empty={t("modalities.availableEmpty")}
            items={items.filter((m) => !m.active)}
            onToggle={toggle}
          />
        </>
      )}
      {canManage && (
        <Button
          variant="outline"
          className="self-start whitespace-normal"
          onClick={() => setAsking(true)}
        >
          {t("modalities.notFound")}
        </Button>
      )}
      <Card heading="h2" title={t("modalities.requests.title")}>
        {api.requests.length === 0 ? (
          <p className="text-muted-foreground">
            {t("modalities.requests.empty")}
          </p>
        ) : (
          <ul className="flex flex-col divide-y">
            {api.requests.map((r) => (
              <li
                key={r.id}
                className="flex flex-wrap justify-between gap-2 py-2"
              >
                <span className="min-w-0 break-words">{r.name}</span>
                <span className="text-sm text-muted-foreground">
                  {formatDate(r.createdAt)} ·{" "}
                  <StatusBadge status={r.status} label={t(`modalities.requests.status.${r.status}`)} />
                </span>
              </li>
            ))}
          </ul>
        )}
      </Card>
      <GraduationDialog
        modality={grading}
        onClose={() => setGrading(null)}
        onSaved={(id, saved) => {
          api.setGraduationEnabled(id, saved.enabled);
          setGrading(null);
          setMessage({
            kind: "status",
            text: t("modalities.graduation.saved"),
          });
        }}
      />
      <RequestModalityDialog
        open={asking}
        onClose={() => setAsking(false)}
        onSend={async (name, note) => {
          await api.sendRequest(name, note);
          setMessage({ kind: "status", text: t("modalities.dialog.sent") });
        }}
      />
    </div>
  );
}

// RF-MOD-01: Coordenador altera; Administrador só consulta (canManage). A barreira de dados é o servidor.
export function ModalitiesPage() {
  return (
    <ManagementLayout activeId="modalities">
      <Content />
    </ManagementLayout>
  );
}
