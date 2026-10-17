import { useTranslation } from "react-i18next";
import { WhatsAppButton } from "@/components/whatsapp";

export interface Contact {
  email?: string;
  phone?: string;
  whatsapp?: string;
}

/** Contato da plataforma (D2): vem das variáveis do front. */
export const platformContact = (): Contact => ({
  email: (import.meta.env.VITE_SUPPORT_EMAIL as string | undefined) || "suporte@ctcore.com.br",
  whatsapp: (import.meta.env.VITE_SUPPORT_WHATSAPP as string | undefined) || undefined,
});

/** Bloco de contato da plataforma ou da unidade (D3). */
export function SupportContact({ contact, title }: { contact: Contact; title?: string }) {
  const { t } = useTranslation();
  return (
    <section aria-label={title ?? t("auth.support.title")} className="space-y-2 text-sm">
      <h2 className="font-medium">{title ?? t("auth.support.title")}</h2>
      {contact.phone && (
        <p>
          {t("auth.support.phone")}: <a className="underline" href={`tel:${contact.phone.replace(/\D/g, "")}`}>{contact.phone}</a>
        </p>
      )}
      {contact.email && (
        <p>
          {t("auth.support.email")}: <a className="underline" href={`mailto:${contact.email}`}>{contact.email}</a>
        </p>
      )}
      <WhatsAppButton phone={contact.whatsapp} />
    </section>
  );
}
