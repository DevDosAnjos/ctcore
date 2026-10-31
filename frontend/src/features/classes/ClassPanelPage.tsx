import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { ClassFormDialog } from "./ClassFormDialog";
import { DeactivateClassDialog } from "./DeactivateClassDialog";
import { useClass, type ClassDetail } from "./useClasses";
import { PageHeader } from "@/components/ui/page-header";
import { Banner } from "@/components/ui/banner";
import { StatusBadge } from "@/components/ui/badge";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

// D7: só http e https viram botão; qualquer outro esquema (javascript:, data:) é ignorado.
function safeLink(link: string | null) {
  if (!link) return null;
  try {
    const u = new URL(link);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : null;
  } catch {
    return null;
  }
}

function Panel({ klass, onRefresh }: { klass: ClassDetail; onRefresh: () => Promise<void> }) {
  const { t } = useTranslation();
  const [deactivating, setDeactivating] = useState(false);
  const [editing, setEditing] = useState(false);
  const [notice, setNotice] = useState<string>();
  const { permissions } = usePermissions();
  const canManage = !!permissions && permissions.CLASSES_MANAGE !== "NONE";
  const group = safeLink(klass.groupLink);
  const canNotify = !!permissions && permissions.ANNOUNCEMENTS !== "NONE" && klass.status === "ACTIVE";

  return (
    <div className="flex flex-col gap-4">
      <Link to="/turmas" className="inline-flex min-h-11 items-center self-start text-sm underline">
        {t("classes.panel.back")}
      </Link>
      <PageHeader title={klass.name} actions={<StatusBadge status={klass.status} label={t(`classes.statuses.${klass.status}`)} />} />
      {notice && (
        <Banner tone="ok">
          {notice}
        </Banner>
      )}
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" className="min-h-11" disabled={!canManage || klass.status !== "ACTIVE"} onClick={() => setEditing(true)}>
          {t("classes.panel.edit")}
        </Button>
        <Button variant="outline" className="min-h-11" asChild>
          <Link to={`/alunos?classId=${klass.id}`}>{t("classes.panel.viewStudents")}</Link>
        </Button>
        {canNotify ? (
          <Button variant="outline" className="min-h-11" asChild>
            <Link to={`/comunicados/novo?classId=${klass.id}`}>{t("classes.panel.notify")}</Link>
          </Button>
        ) : (
          <Button variant="outline" className="min-h-11" disabled>
            {t("classes.panel.notify")}
          </Button>
        )}
        {group ? (
          <Button variant="outline" className="min-h-11" asChild>
            <a href={group} target="_blank" rel="noopener noreferrer">
              {t("classes.panel.openGroup")}
            </a>
          </Button>
        ) : (
          <Button variant="outline" className="min-h-11" disabled>
            {t("classes.panel.openGroup")}
          </Button>
        )}
        <Button variant="outline" className="min-h-11" disabled={!canManage || klass.status !== "ACTIVE"} onClick={() => setDeactivating(true)}>
          {t("classes.panel.deactivate")}
        </Button>
      </div>

      <Card heading="h2" title={t("classes.panel.about")}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label={t("classes.panel.unit")}>{klass.unit.name}</Field>
          <Field label={t("classes.columns.modality")}>{klass.modality.name}</Field>
          <Field label={t("classes.columns.ageRange")}>{klass.ageRange?.name ?? t("classes.none")}</Field>
        </dl>
      </Card>
      <Card heading="h2" title={t("classes.columns.schedule")}>
        {klass.schedule.length === 0 ? (
          <p>{t("classes.panel.noSchedule")}</p>
        ) : (
          <ul>
            {klass.schedule.map((s) => (
              <li key={s.weekday}>{t("classes.panel.slot", { day: t(`classes.weekdaysLong.${s.weekday}`), from: s.startsAt, to: s.endsAt })}</li>
            ))}
          </ul>
        )}
      </Card>
      <Card heading="h2" title={t("classes.panel.team")}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label={t("classes.panel.head")}>{klass.head.name}</Field>
          <Field label={t("classes.panel.assistants")}>{klass.assistants.map((a) => a.name).join(", ") || t("classes.none")}</Field>
        </dl>
      </Card>
      <Card heading="h2" title={t("classes.columns.students")}>
        <p>
          {klass.capacity === null
            ? t("classes.panel.studentsNoCap", { count: klass.activeStudents })
            : t("classes.panel.studentsCap", { count: klass.activeStudents, capacity: klass.capacity })}
        </p>
        {klass.overCapacity && klass.capacity !== null && (
          <p className="font-medium text-danger-foreground">{t("classes.over", { count: klass.activeStudents, capacity: klass.capacity })}</p>
        )}
      </Card>
      <Card heading="h2" title={t("classes.panel.nextClass")}>
        <p>
          {klass.nextClass
            ? t("classes.panel.nextLine", { date: klass.nextClass.date, from: klass.nextClass.startsAt, to: klass.nextClass.endsAt })
            : t("classes.panel.noNext")}
        </p>
      </Card>
      <Card heading="h2" title={t("classes.panel.activeStudents")}>
        {klass.students.length === 0 ? (
          <p>{t("classes.panel.noStudents")}</p>
        ) : (
          <ul>
            {klass.students.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-2">
                {s.name}
                {!s.primary && <span className="rounded-full border px-2 text-xs">{t("classes.panel.otherSlot")}</span>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card heading="h2" title={t("classes.panel.group")}>
        <p>{group ? t("classes.panel.hasGroup") : t("classes.panel.noGroup")}</p>
      </Card>
      <ClassFormDialog open={editing} klass={klass} onClose={() => setEditing(false)} onSaved={() => void onRefresh()} />
      <DeactivateClassDialog
        klass={deactivating ? klass : null}
        onClose={() => setDeactivating(false)}
        onDone={() => {
          setNotice(t("classes.panel.deactivateDialog.done"));
          void onRefresh();
        }}
      />
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const { id = "" } = useParams();
  const api = useClass(id);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (api.status === "notFound") {
    return (
      <EmptyState
        title={t("classes.panel.notFoundTitle")}
        text={t("classes.panel.notFoundText")}
        action={
          <Link to="/turmas" className="inline-flex min-h-11 items-center underline">
            {t("classes.panel.back")}
          </Link>
        }
      />
    );
  }
  if (!api.klass) return <ErrorState onRetry={api.retry} />;
  return <Panel klass={api.klass} onRefresh={api.refresh} />;
}

// RF-TUR-02: painel da turma; a barreira de dados é o servidor.
export function ClassPanelPage() {
  return (
    <ManagementLayout activeId="classes">
      <Content />
    </ManagementLayout>
  );
}
