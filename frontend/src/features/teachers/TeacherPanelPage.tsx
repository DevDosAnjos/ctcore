import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { Card } from "@/components/Card";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { WhatsAppButton } from "@/components/whatsapp";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { formatDateTime } from "@/lib/format";
import { GrantAccessDialog } from "./GrantAccessDialog";
import { resendTeacherInvite, useTeacher, type TeacherDetail } from "./useTeachers";

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

function Panel({ teacher, onRefresh }: { teacher: TeacherDetail; onRefresh: () => Promise<void> }) {
  const { t } = useTranslation();
  const [granting, setGranting] = useState(false);
  const [notice, setNotice] = useState<string>();

  async function resend() {
    try {
      await resendTeacherInvite(teacher.id);
      setNotice(t("teachers.panel.resendDone"));
    } catch {
      setNotice(t("teachers.panel.resendError"));
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <Link to="/professores" className="inline-flex min-h-11 items-center self-start text-sm underline">
        {t("teachers.panel.back")}
      </Link>
      <header className="flex flex-wrap items-center gap-3">
        {teacher.photoUrl ? (
          <img src={teacher.photoUrl} alt="" className="size-16 shrink-0 rounded-full object-cover" />
        ) : (
          <span aria-hidden="true" className="flex size-16 shrink-0 items-center justify-center rounded-full bg-muted text-xl font-semibold">
            {initials(teacher.name)}
          </span>
        )}
        <div className="min-w-0">
          <h1 className="break-words text-2xl font-semibold">{teacher.name}</h1>
          <p className="text-muted-foreground">{t(`teachers.statuses.${teacher.status}`)}</p>
        </div>
      </header>
      {notice && (
        <p role="status" className="text-sm">
          {notice}
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" className="min-h-11" disabled>
          {t("teachers.panel.edit")}
        </Button>
        <Button variant="outline" className="min-h-11" disabled>
          {t("teachers.panel.deactivate")}
        </Button>
        {(teacher.access === "NONE" || teacher.access === "ENDED") && (
          <Button className="min-h-11" onClick={() => setGranting(true)}>
            {t("teachers.panel.grant.button")}
          </Button>
        )}
        {teacher.access === "PENDING" && (
          <Button className="min-h-11" onClick={() => void resend()}>
            {t("teachers.panel.resend")}
          </Button>
        )}
        <WhatsAppButton phone={teacher.phone ?? undefined} />
      </div>

      <Card heading="h2" title={t("teachers.panel.about")}>
        <p className="break-words">{teacher.bio ?? t("teachers.panel.noBio")}</p>
      </Card>
      <Card heading="h2" title={t("teachers.panel.contact")}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Field label={t("teachers.panel.email")}>{teacher.email ?? t("teachers.panel.none")}</Field>
          <Field label={t("teachers.panel.phone")}>{teacher.phone ?? t("teachers.panel.none")}</Field>
        </dl>
      </Card>
      <Card heading="h2" title={t("teachers.panel.graduations")}>
        {teacher.modalities.length === 0 ? (
          <p>{t("teachers.panel.none")}</p>
        ) : (
          <ul>
            {teacher.modalities.map((m) => (
              <li key={m.id}>{m.level === null ? m.name : t("teachers.modality", { name: m.name, level: m.level, degree: m.degree })}</li>
            ))}
          </ul>
        )}
      </Card>
      <Card heading="h2" title={t("teachers.panel.units")}>
        <p>{teacher.units.map((u) => u.name).join(", ") || t("teachers.panel.none")}</p>
      </Card>
      <Card heading="h2" title={t("teachers.panel.classes")}>
        {teacher.classes.length === 0 ? (
          <p>{t("teachers.panel.noClasses")}</p>
        ) : (
          <ul>
            {teacher.classes.map((c) => (
              <li key={c.id}>{t("teachers.panel.classLine", { name: c.name, role: t(`teachers.panel.classRoles.${c.role}`) })}</li>
            ))}
          </ul>
        )}
      </Card>
      <Card heading="h2" title={t("teachers.panel.upcoming")}>
        {teacher.upcomingClasses.length === 0 ? (
          <p>{t("teachers.panel.noUpcoming")}</p>
        ) : (
          <ul>
            {teacher.upcomingClasses.map((c) => (
              <li key={`${c.classId}-${c.startsAt}`}>
                {c.name} · {formatDateTime(c.startsAt)}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card heading="h2" title={t("teachers.panel.access")}>
        <p>{t(`teachers.panel.accessStates.${teacher.access}`)}</p>
      </Card>
      <GrantAccessDialog
        teacher={granting ? teacher : null}
        onClose={() => setGranting(false)}
        onDone={() => {
          setNotice(t("teachers.panel.grant.done"));
          void onRefresh();
        }}
      />
    </div>
  );
}

function Content() {
  const { t } = useTranslation();
  const { id = "" } = useParams();
  const api = useTeacher(id);
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (api.status === "notFound") {
    return (
      <EmptyState
        title={t("teachers.panel.notFoundTitle")}
        text={t("teachers.panel.notFoundText")}
        action={
          <Link to="/professores" className="inline-flex min-h-11 items-center underline">
            {t("teachers.panel.back")}
          </Link>
        }
      />
    );
  }
  if (!api.teacher) return <ErrorState onRetry={api.retry} />;
  return <Panel teacher={api.teacher} onRefresh={api.refresh} />;
}

// RF-PRO-02: painel com dar acesso e reenviar convite; a barreira de dados é o servidor.
export function TeacherPanelPage() {
  return (
    <ManagementLayout activeId="teachers">
      <Content />
    </ManagementLayout>
  );
}
