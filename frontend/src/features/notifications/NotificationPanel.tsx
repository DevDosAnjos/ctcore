import { useEffect, useState, type RefObject } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { SidePanel } from "@/components/SidePanel";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { formatCurrency, formatDateTime } from "@/lib/format";
import { notificationTarget, relativeTime } from "./notificationActions";
import { useNotificationPanel, type AppNotification } from "./useNotifications";

type Filter = "all" | "unread";

function Row({ n, onNavigate }: { n: AppNotification; onNavigate: () => void }) {
  const { t } = useTranslation();
  const unread = n.readAt === null;
  const rel = relativeTime(n.createdAt);
  const p = n.params;
  const text = t(`notifications.types.${n.type}.text`, {
    studentName: String(p.studentName ?? ""),
    personName: String(p.personName ?? ""),
    amount: typeof p.amountCents === "number" ? formatCurrency(p.amountCents) : "",
  });
  return (
    <li className={`flex flex-col gap-2 rounded-md border p-3 ${unread ? "border-primary bg-accent" : ""}`}>
      <p className="break-words text-sm">
        {unread && <span className="mr-1 font-semibold">{t("notifications.unreadMark")}</span>}
        {text}
      </p>
      <time className="text-xs text-muted-foreground" dateTime={n.createdAt} title={formatDateTime(n.createdAt)}>
        {t(`notifications.time.${rel.key}`, { count: rel.count })}
      </time>
      <Button asChild variant="outline" size="sm" className="self-start">
        <Link to={notificationTarget(n)} onClick={onNavigate}>
          {t(`notifications.types.${n.type}.action`)}
        </Link>
      </Button>
    </li>
  );
}

// RF-NOT-01: painel do sino. A barreira de permissão é do servidor (403 vira o estado sem permissão).
export function NotificationPanel({
  open,
  onOpenChange,
  returnFocusRef,
  onUnread,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  returnFocusRef?: RefObject<HTMLElement | null>;
  onUnread: (count: number) => void;
}) {
  const { t } = useTranslation();
  const panel = useNotificationPanel(open);
  const [filter, setFilter] = useState<Filter>("all");
  const [failed, setFailed] = useState(false);
  const unreadCount = panel.data?.unreadCount;
  useEffect(() => {
    if (unreadCount !== undefined) onUnread(unreadCount);
  }, [unreadCount, onUnread]);

  const items = panel.data?.items ?? [];
  const unreadItems = items.filter((n) => n.readAt === null);
  const shown = filter === "unread" ? unreadItems : items;
  const close = () => onOpenChange(false);

  const markAll = () => panel.readAll().then(() => setFailed(false), () => setFailed(true));
  const toggle = (value: boolean) => panel.setReceiveAll(value).then(() => setFailed(false), () => setFailed(true));

  let body;
  if (panel.status === "loading") body = <LoadingState />;
  else if (panel.status === "forbidden") body = <ForbiddenState />;
  else if (panel.status === "error") body = <ErrorState onRetry={panel.retry} />;
  else if (items.length === 0) body = <EmptyState title={t("notifications.empty.title")} text={t("notifications.empty.text")} />;
  else if (shown.length === 0) body = <EmptyState title={t("notifications.allRead.title")} text={t("notifications.allRead.text")} />;
  else {
    body = (
      <div className="flex flex-col gap-4">
        {unreadItems.length === 0 && (
          <p role="status" className="text-sm font-medium">
            {t("notifications.allRead.title")}
          </p>
        )}
        {(["TODAY", "WEEK"] as const).map((group) => {
          const rows = shown.filter((n) => n.group === group);
          if (rows.length === 0) return null;
          return (
            <section key={group} aria-labelledby={`notif-${group}`} className="flex flex-col gap-2">
              <h3 id={`notif-${group}`} className="text-sm font-semibold">
                {t(`notifications.groups.${group}`)}
              </h3>
              <ul className="flex flex-col gap-2">
                {rows.map((n) => (
                  <Row key={n.id} n={n} onNavigate={close} />
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    );
  }

  return (
    <SidePanel open={open} onOpenChange={onOpenChange} title={t("notifications.title")} returnFocusRef={returnFocusRef}>
      <div className="flex flex-col gap-4">
        {panel.prefs?.canReceiveAll && (
          <label className="flex min-h-11 items-center justify-between gap-3 text-sm">
            <span className="flex flex-col">
              <span className="font-medium">{t("notifications.receiveAll.label")}</span>
              <span className="text-muted-foreground">{t("notifications.receiveAll.hint")}</span>
            </span>
            <Switch checked={panel.prefs.receiveAll} onCheckedChange={toggle} label={t("notifications.receiveAll.label")} />
          </label>
        )}
        {panel.status === "ready" && items.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div role="group" aria-label={t("notifications.filters.label")} className="flex gap-2">
              <Button variant={filter === "all" ? "default" : "outline"} size="sm" aria-pressed={filter === "all"} onClick={() => setFilter("all")}>
                {t("notifications.filters.all", { count: items.length })}
              </Button>
              <Button variant={filter === "unread" ? "default" : "outline"} size="sm" aria-pressed={filter === "unread"} onClick={() => setFilter("unread")}>
                {t("notifications.filters.unread", { count: unreadItems.length })}
              </Button>
            </div>
            <Button variant="ghost" size="sm" disabled={unreadItems.length === 0} onClick={markAll}>
              {t("notifications.markAll")}
            </Button>
          </div>
        )}
        {failed && (
          <p role="alert" className="text-sm">
            {t("notifications.failed")}
          </p>
        )}
        {body}
        {panel.status === "ready" && <p className="text-xs text-muted-foreground">{t("notifications.retention")}</p>}
      </div>
    </SidePanel>
  );
}
