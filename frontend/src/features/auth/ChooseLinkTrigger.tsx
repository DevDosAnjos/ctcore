import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { ChooseLinkModal } from "./ChooseLinkModal";
import { useSession } from "./SessionProvider";

// Reabre o modal sem novo login; só aparece com mais de um vínculo (D5).
export function ChooseLinkTrigger() {
  const { t } = useTranslation();
  const { state } = useSession();
  const [open, setOpen] = useState(false);
  if (state.status !== "ready" || state.session.links.length < 2) return null;
  return (
    <>
      <Button variant="outline" size="sm" onClick={() => setOpen(true)}>
        {t("chooseLink.trigger")}
      </Button>
      <ChooseLinkModal open={open} onClose={() => setOpen(false)} />
    </>
  );
}
