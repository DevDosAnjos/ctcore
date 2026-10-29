import { useState } from "react";
import { useTranslation } from "react-i18next";
import { DataTable, type Column } from "@/components/query/DataTable";
import { RelativeDate } from "@/components/RelativeDate";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Person } from "@/components/ui/avatar";
import { Banner } from "@/components/ui/banner";
import { StatusBadge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { QuickFilters } from "@/components/ui/quick-filters";
import { ManagementLayout } from "@/features/permissions/ManagementLayout";
import { DeactivateUserDialog } from "./DeactivateUserDialog";
import { EditUnitsDialog } from "./EditUnitsDialog";
import { InviteUserDialog } from "./InviteUserDialog";
import { resendInvite, useUsers, type StaffUser } from "./useUsers";
import { PageHeader } from "@/components/ui/page-header";

function Content() {
  const { t } = useTranslation();
  const api = useUsers();
  const [inviting, setInviting] = useState(false);
  const [editing, setEditing] = useState<StaffUser | null>(null);
  const [deactivating, setDeactivating] = useState<StaffUser | null>(null);
  const [notice, setNotice] = useState<string>();
  const [filter, setFilter] = useState("all");
  if (api.status === "loading") return <LoadingState />;
  if (api.status === "forbidden") return <ForbiddenState />;
  if (!api.users) return <ErrorState onRetry={api.retry} />;

  const users = api.users;
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

  const sr = (u: StaffUser) => <span className="sr-only"> {u.name}</span>;
  const columns: Column<StaffUser>[] = [
    { key: "name", label: t("users.title"), render: (u) => <Person name={u.name} secondary={u.email} /> },
    { key: "role", label: t("users.role"), render: (u) => t(`users.roles.${u.role}`) },
    { key: "units", label: t("users.units"), render: (u) => u.units.map((x) => x.name).join(", ") },
    { key: "last", label: t("users.lastAccess"), render: (u) => (u.lastAccessAt ? <RelativeDate value={u.lastAccessAt} /> : t("users.never")) },
    { key: "status", label: t("users.status"), render: (u) => <StatusBadge status={u.status} label={t(`users.statuses.${u.status}`)} /> },
    {
      key: "actions",
      label: t("users.actions"),
      render: (u) =>
        u.canManage && (
          <div className="flex flex-wrap gap-2">
            {u.status === "PENDING" && (
              <Button variant="secondary" size="sm" onClick={() => void resend(u)}>
                {t("users.resend")}
                {sr(u)}
              </Button>
            )}
            <Button variant="secondary" size="sm" onClick={() => setEditing(u)}>
              {t("users.editUnits.button")}
              {sr(u)}
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setDeactivating(u)}>
              {t("users.deactivate.button")}
              {sr(u)}
            </Button>
          </div>
        ),
    },
  ];
  const count = (s: string) => users.filter((u) => u.status === s).length;
  const shown = filter === "all" ? users : users.filter((u) => u.status === filter);

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title={t("users.title")} />
      {notice && <Banner tone="ok">{notice}</Banner>}
      <Button className="self-start" onClick={() => setInviting(true)}>
        {t("users.invite.button")}
      </Button>
      {users.length === 0 ? (
        <EmptyState title={t("users.emptyTitle")} text={t("users.emptyText")} />
      ) : (
        <>
          <QuickFilters
            value={filter}
            onChange={setFilter}
            options={[
              { key: "all", label: t("users.filters.all"), count: users.length },
              { key: "ACTIVE", label: t("users.filters.ACTIVE"), count: count("ACTIVE") },
              { key: "PENDING", label: t("users.filters.PENDING"), count: count("PENDING") },
            ]}
          />
          <DataTable columns={columns} rows={shown} rowKey={(u) => u.linkId} order="" onOrder={() => undefined} />
        </>
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
