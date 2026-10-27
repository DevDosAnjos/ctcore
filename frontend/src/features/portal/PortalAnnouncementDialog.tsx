import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Modal } from "@/components/Modal";
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from "@/components/states";
import { ApiError } from "@/lib/api";
import { formatDate } from "@/lib/format";
import { destinationText } from "./PortalAnnouncementItem";
import { openPortalAnnouncement, type AnnouncementDetail } from "./usePortalAnnouncements";

type Loaded = { id: string; detail?: AnnouncementDetail; failure?: "forbidden" | "notFound" | "error" };

// RF-POR-06, RN-COM-08: abrir o aviso mostra o texto completo e grava a leitura do aluno. Título e texto entram como texto, nunca como HTML.
export function PortalAnnouncementDialog({ id, studentId, onClose, onRead }: { id: string; studentId: string | null; onClose: () => void; onRead: () => void }) {
  const { t } = useTranslation();
  const [done, setDone] = useState<Loaded>();
  const [tick, setTick] = useState(0);
  const read = useRef(onRead);
  useEffect(() => {
    read.current = onRead;
  });

  useEffect(() => {
    let alive = true;
    openPortalAnnouncement(id, studentId).then(
      (detail) => {
        if (!alive) return;
        setDone({ id, detail });
        read.current();
      },
      (e) => alive && setDone({ id, failure: e instanceof ApiError && e.status === 403 ? "forbidden" : e instanceof ApiError && e.status === 404 ? "notFound" : "error" }),
    );
    return () => {
      alive = false;
    };
  }, [id, studentId, tick]);

  const current = done?.id === id ? done : undefined;
  const detail = current?.detail;
  const retry = () => {
    setDone(undefined);
    setTick((n) => n + 1);
  };
  return (
    <Modal open onOpenChange={(o) => !o && onClose()} title={detail?.title ?? t("portalAnnouncements.dialogTitle")}>
      {!current && <LoadingState />}
      {current?.failure === "forbidden" && <ForbiddenState />}
      {current?.failure === "notFound" && <EmptyState title={t("portalAnnouncements.notFoundTitle")} text={t("portalAnnouncements.notFoundText")} />}
      {current?.failure === "error" && <ErrorState onRetry={retry} />}
      {detail && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">{t("portalAnnouncements.sentAt", { date: formatDate(detail.sentAt) })}</p>
          <p className="break-words text-sm">{destinationText(t, detail.destination)}</p>
          <p className="break-words text-sm text-muted-foreground">{t("portalAnnouncements.author", { name: detail.author })}</p>
          {detail.imageUrl && <img src={detail.imageUrl} alt={t("portalAnnouncements.imageAlt")} className="max-h-64 w-full rounded-md object-contain" />}
          <p className="whitespace-pre-wrap break-words">{detail.body}</p>
        </div>
      )}
    </Modal>
  );
}
