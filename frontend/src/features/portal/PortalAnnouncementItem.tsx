import { useTranslation } from "react-i18next";
import { formatDate } from "@/lib/format";
import { PortalTag } from "./PortalTag";
import type { AnnouncementItem } from "./usePortalAnnouncements";

/** Texto do destino ("Para: ..."), compartilhado com a janela do aviso. */
export function destinationText(t: (key: string, o?: Record<string, unknown>) => string, d: AnnouncementItem["destination"]) {
  return t(`portalAnnouncements.destination.${d.kind}`, { names: d.names.join(", ") });
}

// RF-POR-06: um aviso da lista. Fixado e Não lido levam etiqueta com texto (não só cor); o botão abre o aviso e grava a leitura.
export function PortalAnnouncementItem({ item, onOpen }: { item: AnnouncementItem; onOpen: (id: string) => void }) {
  const { t } = useTranslation();
  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(item.id)}
        className={`flex min-h-11 w-full flex-col gap-1 rounded-md border p-3 text-left hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${item.unread ? "border-l-4 border-l-primary bg-accent/50" : ""}`}
      >
        {(item.pinned || item.unread) && (
          <span className="flex flex-wrap items-center gap-2">
            {item.pinned && <PortalTag tone="strong">{t("portalAnnouncements.pinned")}</PortalTag>}
            {item.unread && <PortalTag>{t("portalAnnouncements.unread")}</PortalTag>}
          </span>
        )}
        <span className={`break-words ${item.unread ? "font-semibold" : "font-medium"}`}>{item.title}</span>
        <span className="text-sm text-muted-foreground">{t("portalAnnouncements.sentAt", { date: formatDate(item.sentAt) })}</span>
        <span className="break-words text-sm">{destinationText(t, item.destination)}</span>
        <span className="break-words text-sm text-muted-foreground">{t("portalAnnouncements.author", { name: item.author })}</span>
      </button>
    </li>
  );
}
