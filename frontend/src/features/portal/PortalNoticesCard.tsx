import { useTranslation } from "react-i18next";
import { Card } from "@/components/Card";
import { EmptyState } from "@/components/states";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { PortalHomeData } from "./usePortalHome";

// RF-POR-02, RN-COM-08: avisos não lidos e fixados; ler e marcar como lido é da aba Avisos (#93).
export function PortalNoticesCard({ data }: { data: PortalHomeData["announcements"] }) {
  const { t } = useTranslation();
  return (
    <Card
      heading="h2"
      title={t("portalHome.notices.title")}
      actions={data.unreadCount > 0 ? <span className="text-sm">{t("portalHome.notices.unreadCount", { count: data.unreadCount })}</span> : undefined}
    >
      {data.items.length === 0 ? (
        <EmptyState title={t("portalHome.notices.emptyTitle")} text={t("portalHome.notices.emptyText")} />
      ) : (
        <ul className="flex flex-col divide-y">
          {data.items.map((n) => (
            <li key={n.id} className="flex flex-col gap-1 py-3">
              <div className="flex flex-wrap items-center gap-2">
                {n.pinned && <PortalTag tone="strong">{t("portalHome.notices.pinned")}</PortalTag>}
                {n.unread && <PortalTag>{t("portalHome.notices.unread")}</PortalTag>}
              </div>
              <p className="break-words font-medium">{n.title}</p>
              <p className="text-muted-foreground">{t("portalHome.notices.sentAt", { date: formatDate(n.sentAt) })}</p>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
