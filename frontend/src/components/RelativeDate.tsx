import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import { DEFAULT_TZ, formatDate, formatDateTime, isCalendarDate, relativeDay } from "@/lib/format";

// "há N dias" vale de 2 a este limite; acima disso ou no futuro, data exata (D3).
const MAX_DAYS = 30;

type Props = { value: string | Date | null | undefined; timeZone?: string; now?: Date; className?: string };

// Data relativa com a data e a hora exatas ao passar o mouse, focar ou tocar (RNF-23).
export function RelativeDate({ value, timeZone = DEFAULT_TZ, now, className }: Props) {
  const { t } = useTranslation();
  const id = useId();
  const [hover, setHover] = useState(false);
  const [focus, setFocus] = useState(false);
  const [toggled, setToggled] = useState(false);
  const open = hover || focus || toggled;

  useEffect(() => {
    if (!open) return;
    const esc = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setHover(false);
        setFocus(false);
        setToggled(false);
      }
    };
    document.addEventListener("keydown", esc);
    return () => document.removeEventListener("keydown", esc);
  }, [open]);

  const days = value ? relativeDay(value, now ?? new Date(), timeZone) : null;
  if (!value || days === null) return <span className={className}>{t("format.empty")}</span>;

  const text =
    days < 0 || days > MAX_DAYS ? formatDate(value, timeZone) : days === 0 ? t("format.today") : days === 1 ? t("format.yesterday") : t("format.daysAgo", { days });
  const iso = isCalendarDate(value) ? value : (value instanceof Date ? value : new Date(value)).toISOString();

  return (
    <span className="relative inline-block">
      <button
        type="button"
        className={`min-h-11 min-w-11 rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${className ?? ""}`}
        aria-describedby={open ? id : undefined}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        onFocus={() => setFocus(true)}
        onBlur={() => {
          setFocus(false);
          setToggled(false);
        }}
        onClick={() => setToggled((v) => !v)}
      >
        <time dateTime={iso}>{text}</time>
      </button>
      {open && (
        <span id={id} role="tooltip" className="absolute left-0 top-full z-10 whitespace-nowrap rounded-md border bg-popover px-2 py-1 text-sm text-popover-foreground shadow">
          {formatDateTime(value, timeZone)}
        </span>
      )}
    </span>
  );
}
