import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { Card } from "@/components/Card";
import { PageHeader } from "@/components/ui/page-header";
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingState,
} from "@/components/states";
import { Button } from "@/components/ui/button";
import { useSession } from "@/features/auth/SessionProvider";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { usePermissions } from "@/features/permissions/usePermissions";
import { StudentSimpleList } from "@/features/students/StudentSimpleList";
import { br } from "./agendaDates";
import { CancelLessonDialog } from "./CancelLessonDialog";
import { EditLessonDialog } from "./EditLessonDialog";
import { cancelReasonText, lessonName, modalityHue } from "./LessonCard";
import { useLesson, type LessonDetail } from "./useLesson";
import { Banner } from "@/components/ui/banner";
import { Badge, StatusBadge } from "@/components/ui/badge";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

// Só http e https viram botão (como o painel da turma).
function safeLink(link: string | null) {
  if (!link) return null;
  try {
    const u = new URL(link);
    return u.protocol === "http:" || u.protocol === "https:" ? u.href : null;
  } catch {
    return null;
  }
}

function BackLink() {
  const { t } = useTranslation();
  return (
    <Link
      to="/agenda"
      className="inline-flex min-h-11 items-center self-start text-sm underline"
    >
      {t("agenda.panel.back")}
    </Link>
  );
}

function Panel({
  lesson,
  onChanged,
}: {
  lesson: LessonDetail;
  onChanged: () => void;
}) {
  const { t } = useTranslation();
  const [dialog, setDialog] = useState<"edit" | "cancel">();
  const group = safeLink(lesson.groupLink);
  const cancelled = lesson.status === "CANCELLED";
  const { permissions } = usePermissions();
  const { state } = useSession();
  const role = state.status === "ready" ? state.session.links.find((l) => l.id === state.session.activeLinkId)?.role : undefined;
  const teacherRecord = role === "TEACHER" && !!permissions ? permissions.STUDENT_RECORD !== "NONE" : undefined;
  const canNotify = !!permissions && permissions.ANNOUNCEMENTS !== "NONE" && !!lesson.class;
  // RF-COM-02: abre o comunicado já preenchido (aula cancelada ou aviso geral), com a turma, a data e o horário.
  const noticeLink = `/comunicados/novo?${new URLSearchParams({ classId: lesson.class?.id ?? "", type: cancelled ? "CANCELED_CLASS" : "GENERAL", date: lesson.date, time: lesson.startsAt })}`;
  const color = `hsl(${modalityHue(lesson.modality.id)} 60% 45%)`;
  return (
    <div className="flex flex-col gap-4">
      <BackLink />
      <PageHeader
        title={lessonName(lesson)}
        actions={
          <>
        {!lesson.class && (
          <Badge tone="neutral">{t("agenda.adhoc")}</Badge>
        )}
        <StatusBadge status={lesson.status} label={t(`agenda.panel.statuses.${lesson.status}`)} />
        {lesson.substitute && (
          <Badge tone="info">{t("agenda.substitute")}</Badge>
        )}
          </>
        }
      />
      {cancelled && cancelReasonText(lesson, t) && (
        <Banner tone="ok">
          <span className="font-medium">{t("agenda.panel.reason")}</span>{" "}
          {cancelReasonText(lesson, t)}
        </Banner>
      )}
      <div className="flex flex-wrap gap-2">
        {canNotify ? (
          <Button variant="outline" className="min-h-11" asChild>
            <Link to={noticeLink}>{t("agenda.panel.notify")}</Link>
          </Button>
        ) : (
          <Button variant="outline" className="min-h-11" disabled>
            {t("agenda.panel.notify")}
          </Button>
        )}
        {lesson.canEdit && (
          <Button
            variant="outline"
            className="min-h-11"
            onClick={() => setDialog("edit")}
          >
            {t("agenda.panel.edit")}
          </Button>
        )}
        {lesson.canCancel && (
          <Button
            variant="outline"
            className="min-h-11"
            onClick={() => setDialog("cancel")}
          >
            {t("agenda.panel.cancel")}
          </Button>
        )}
        {lesson.canViewClass && lesson.class && (
          <Button variant="outline" className="min-h-11" asChild>
            <Link to={`/turmas/${lesson.class!.id}`}>
              {t("agenda.panel.viewClass")}
            </Link>
          </Button>
        )}
      </div>
      {cancelled && (
        <Card heading="h2" title={t("agenda.panel.noticeTitle")}>
          <p className="break-words">
            {t("agenda.panel.noticeText", {
              name: lessonName(lesson),
              date: br(lesson.date),
              from: lesson.startsAt,
              to: lesson.endsAt,
              reason: cancelReasonText(lesson, t) ?? "",
            })}
          </p>
        </Card>
      )}
      <Card heading="h2" title={t("agenda.panel.about")}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label={t("agenda.panel.when")}>
            {t("agenda.panel.whenLine", {
              date: br(lesson.date),
              from: lesson.startsAt,
              to: lesson.endsAt,
            })}
          </Field>
          <Field label={t("agenda.panel.unit")}>{lesson.unit.name}</Field>
          <Field label={t("agenda.filters.modality")}>
            <span className="flex items-center gap-1">
              <span
                aria-hidden="true"
                className="inline-block size-2 shrink-0 rounded-full"
                style={{ backgroundColor: color }}
              />
              {lesson.modality.name}
            </span>
          </Field>
          <Field label={t("agenda.filters.teacher")}>
            {lesson.teacher.name}
          </Field>
          {lesson.substitute && lesson.head && (
            <Field label={t("agenda.panel.head")}>{lesson.head.name}</Field>
          )}
          {!lesson.class && (
            <Field label={t("agenda.panel.visibility")}>
              {lesson.visibilityClass?.name ?? t("agenda.panel.visibilityAll")}
            </Field>
          )}
        </dl>
      </Card>
      {lesson.class && (
        <>
          <Card heading="h2" title={t("agenda.panel.numbers")}>
            <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Field label={t("agenda.panel.students")}>
                {lesson.activeStudents}
              </Field>
              <Field label={t("agenda.panel.trials")}>
                {lesson.trialClasses}
              </Field>
            </dl>
          </Card>
          <Card heading="h2" title={t("students.simple.title")}>
            <StudentSimpleList
              lessonId={lesson.id}
              recordHref={teacherRecord ? (sid) => `/agenda/aulas/${lesson.id}/alunos/${sid}` : undefined}
              noRecordNotice={teacherRecord === false}
            />
          </Card>
          <Card heading="h2" title={t("agenda.panel.group")}>
            {group ? (
              <Button variant="outline" className="min-h-11" asChild>
                <a href={group} target="_blank" rel="noopener noreferrer">
                  {t("agenda.panel.openGroup")}
                </a>
              </Button>
            ) : (
              <p>{t("agenda.panel.noGroup")}</p>
            )}
          </Card>
        </>
      )}
      <EditLessonDialog
        lesson={dialog === "edit" ? lesson : null}
        onClose={() => setDialog(undefined)}
        onSaved={onChanged}
      />
      <CancelLessonDialog
        lesson={dialog === "cancel" ? lesson : null}
        onClose={() => setDialog(undefined)}
        onSaved={onChanged}
      />
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const { id = "" } = useParams();
  const api = useLesson(id);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (api.status === "notFound") {
    return (
      <EmptyState
        title={t("agenda.panel.notFoundTitle")}
        text={t("agenda.panel.notFoundText")}
        action={<BackLink />}
      />
    );
  }
  if (!api.lesson) return <ErrorState onRetry={api.retry} />;
  return <Panel lesson={api.lesson} onChanged={api.retry} />;
}

// RF-AGE-02: painel da aula; a barreira de dados é o servidor.
export function LessonPanelPage() {
  return (
    <ManagementLayout activeId="agenda">
      <Content />
    </ManagementLayout>
  );
}
