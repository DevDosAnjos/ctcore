import { useEffect, useRef, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { SupportContact, platformContact } from "@/features/auth/SupportContact";
import { ThemeToggle } from "@/theme/ThemeToggle";

// Moldura das telas de acesso (canvas): painel escuro da marca a partir de 768 px e formulário de 420 px.
// O contato de suporte fica uma vez só, abaixo do formulário, para valer nos dois tamanhos.
export function AuthShell({
  title,
  subtitle,
  focusTitle,
  children,
}: {
  title: string;
  subtitle?: string;
  /** Leva o foco ao título ao abrir (telas de estado). */
  focusTitle?: boolean;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const h1 = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (focusTitle) h1.current?.focus();
  }, [focusTitle]);
  return (
    <div className="min-h-screen md:grid md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
      <aside className="hidden flex-col justify-between bg-sidebar p-12 text-sidebar-foreground md:flex" data-testid="auth-brand">
        <span className="text-3xl font-semibold uppercase tracking-wide">{t("app.name")}</span>
        <div className="space-y-3">
          <p className="max-w-sm text-4xl font-semibold uppercase leading-tight">{t("authShell.tagline")}</p>
          <p className="max-w-sm text-sm">{t("authShell.text")}</p>
        </div>
        <span className="h-1 w-16 rounded-full bg-brand" aria-hidden="true" />
      </aside>
      <div className="flex min-h-screen flex-col">
        <header className="flex items-center justify-between gap-2 px-4 py-3 md:justify-end">
          <span className="text-lg font-semibold uppercase md:hidden">{t("app.name")}</span>
          <ThemeToggle />
        </header>
        <main className="mx-auto flex w-full max-w-105 flex-1 flex-col justify-center gap-6 px-4 pb-10">
          <div className="space-y-1">
            <h1 ref={h1} tabIndex={focusTitle ? -1 : undefined} className="text-4xl font-semibold uppercase leading-tight outline-none">
              {title}
            </h1>
            {subtitle && <p className="text-sm text-muted-foreground">{subtitle}</p>}
          </div>
          {children}
          <SupportContact contact={platformContact()} />
        </main>
      </div>
    </div>
  );
}
