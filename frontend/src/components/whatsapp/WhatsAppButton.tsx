import { MessageCircle } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button, type ButtonProps } from "@/components/ui/button";
import { isValidPhone, normalizePhone } from "@/components/fields/masks";
import { useWhatsAppEnabled } from "./WhatsAppContext";

type Props = { phone?: string; groupUrl?: string; message?: string; variant?: ButtonProps["variant"]; className?: string };

function href({ phone, groupUrl, message }: Props): string | null {
  if (groupUrl) return groupUrl.startsWith("https://chat.whatsapp.com/") ? groupUrl : null;
  if (!phone) return null;
  const d = normalizePhone(phone);
  if (!isValidPhone(d)) return null;
  return `https://wa.me/55${d}${message ? `?text=${encodeURIComponent(message)}` : ""}`;
}

// Link wa.me ou convite de grupo, sem API. Some se o WhatsApp está desligado ou sem destino válido (RNF-42).
export function WhatsAppButton({ variant = "outline", className, ...dest }: Props) {
  const { t } = useTranslation();
  const enabled = useWhatsAppEnabled();
  const link = enabled ? href(dest) : null;
  if (!link) return null;
  return (
    <Button asChild variant={variant} className={`min-h-11 min-w-11 ${className ?? ""}`}>
      <a href={link} target="_blank" rel="noopener noreferrer" aria-label={t("whatsapp.open")}>
        <MessageCircle aria-hidden="true" />
        {t("whatsapp.label")}
      </a>
    </Button>
  );
}
