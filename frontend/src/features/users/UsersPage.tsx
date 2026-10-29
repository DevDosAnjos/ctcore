import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { RelativeDate } from "@/components/RelativeDate";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { DeactivateUserDialog } from "./DeactivateUserDialog";
import { EditUnitsDialog } from "./EditUnitsDialog";
import { InviteUserDialog } from "./InviteUserDialog";
import { resendInvite, useUsers, type StaffUser } from "./useUsers";
import { PageHeader } from "@/components/ui/page-header";

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="break-words">{children}</dd>
    </div>
  );
}

function UserRow({ user, onResend, onEdit, onDeactivate }: { user: StaffUser; onResend: (u: StaffUser) => void; onEdit: (u: StaffUser) => void; onDeactivate: (u: StaffUser) => void }) {
  const { t } = useTranslation();
  const sr = <span className="sr-only"> {user.name}</span>;
  return (
    <li>
      <Card heading="h2" title={user.name}>
        <dl className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5">
          <Field label={t("users.email")}>{user.email}</Field>
          <Field label={t("users.role")}>{t(`users.roles.${user.role}`)}</Field>
          <Field label={t("users.units")}>{user.units.map((u) => u.name).join(", ")}</Field>
          <Field label={t("users.lastAccess")}>{user.lastAccessAt ? <RelativeDate value={user.lastAccessAt} /> : t("users.never")}</Field>
          <Field label={t("users.status")}>{t(`users.statuses.${user.status}`)}</Field>
        </dl>
        {user.canManage && (
          <div className="mt-3 flex flex-wrap gap-2">
            {user.status === "PENDING" && (
              <Button variant="outline" size="sm" onClick={() => onResend(user)}>
                {t("users.resend")}
                {sr}
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={() => onEdit(user)}>
              {t("users.editUnits.button")}
              {sr}
            </Button>
            <Button variant="outline" size="sm" onClick={() => onDeactivate(user)}>
              {t("users.deactivate.button")}
              {sr}
            </Button>
          </div>
        )}
      </Card>
    </li>
  );
}

function Content() {
  const { t } = useTranslation();
  const api = useUsers();
  const [inviting, setInviting] = useState(false);
  const [editing, setEditing] = useState<StaffUser | null>(null);
  const [deactivating, setDeactivating] = useState<StaffUser | null>(null);
  const [notice, setNotice] = useState<string>();
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.users) return <ErrorState onRetry={api.retry} />;

  const done = (key: string) => () => {
    setNotice(t(key));
    void api.refresh();
  };
  async function resend(u: StaffUser) {
    try {
      await resendInvite(u.linkId);
      setNotice(t("users.resendDone"));
    } catch {
      setNotice(t("users.resendError"));
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("users.title")} />
      {notice && (
        <p role="status" className="text-sm">
          {notice}
        </p>
      )}
      <Button className="self-start" onClick={() => setInviting(true)}>
        {t("users.invite.button")}
      </Button>
      {api.users.length === 0 ? (
        <EmptyState title={t("users.emptyTitle")} text={t("users.emptyText")} />
      ) : (
        <ul className="flex flex-col gap-4">
          {api.users.map((u) => (
            <UserRow key={u.linkId} user={u} onResend={(x) => void resend(x)} onEdit={setEditing} onDeactivate={setDeactivating} />
          ))}
        </ul>
      )}
      <InviteUserDialog open={inviting} onClose={() => setInviting(false)} onDone={done("users.invite.done")} />
      <EditUnitsDialog user={editing} onClose={() => setEditing(null)} onDone={done("users.editUnits.done")} />
      <DeactivateUserDialog user={deactivating} onClose={() => setDeactivating(null)} onDone={done("users.deactivate.done")} />
    </div>
  );
}

// RF-ACE-06: a rota já nega Professor; a barreira de dados é o servidor.
export function UsersPage() {
  return (
    <ManagementLayout activeId="staffUsers">
      <Content />
    </ManagementLayout>
  );
}
