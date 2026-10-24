import { useRef, useState } from "react";
import { Bell } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { NotificationPanel } from "./NotificationPanel";
import { useUnreadCount } from "./useNotifications";

// RF-NOT-01: sino da barra superior com o contador (99+ acima de 99; sem número em zero). Perfil sem sino (403): não aparece.
export function BellButton() {
  const { t } = useTranslation();
  const { count, forbidden, refresh, setCount } = useUnreadCount();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLButtonElement>(null);
  if (forbidden) return null;
  const shown = count > 99 ? "99+" : String(count);
  return (
    <>
      <Button
        ref={ref}
        variant="ghost"
        size="icon"
        className="relative min-h-11 min-w-11"
        aria-label={count > 0 ? t("notifications.bell.withCount", { count: shown }) : t("notifications.bell.label")}
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <Bell aria-hidden="true" />
        {count > 0 && (
          <span aria-hidden="true" className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-primary px-1 text-center text-xs font-semibold text-primary-foreground">
            {shown}
          </span>
        )}
      </Button>
      <NotificationPanel
        open={open}
        onOpenChange={(v) => {
          setOpen(v);
          if (!v) refresh();
        }}
        returnFocusRef={ref}
        onUnread={setCount}
      />
    </>
  );
}
