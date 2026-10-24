import { useTranslation } from "react-i18next";
import type { AnnouncementPreview } from "./types";

export type PreviewState = { status: "loading" } | { status: "error" } | { status: "ready"; data: AnnouncementPreview };

// RN-COM-09: "para N pessoas por portal, e-mail (M) e WhatsApp", mostrado antes do botão de enviar. Sem destino não aparece.
export function AnnouncementPreviewPanel({ state }: { state: PreviewState | null }) {
  const { t } = useTranslation();
  if (!state) return null;
  const p = state.status === "ready" ? state.data : null;
  const whatsapp = !p?.whatsapp ? "" : t(p.whatsapp.mode === "GROUP" ? "announcements.new.previewWhatsappGroup" : "announcements.new.previewWhatsappConversation");
  return (
    <section aria-label={t("announcements.new.previewLabel")} role="status" className="rounded-md border p-3">
      {state.status === "loading" && <p>{t("announcements.new.previewLoading")}</p>}
      {state.status === "error" && <p className="text-destructive">{t("announcements.new.previewError")}</p>}
      {p && (
        <>
          <p className="font-medium">{p.recipients === 1 ? t("announcements.new.previewOne") : t("announcements.new.preview", { count: p.recipients })}</p>
          <p className="text-sm">{t("announcements.new.previewChannels", { portal: p.portal, email: p.email, whatsapp })}</p>
        </>
      )}
    </section>
  );
}
