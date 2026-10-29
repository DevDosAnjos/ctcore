import type { ReactNode } from "react";
import { Inbox, Lock, TriangleAlert } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

// Os quatro estados de tela (RNF-25). Textos padrão vêm de pt-BR.json; cada tela pode
// passar os seus, já traduzidos (D7).

export function LoadingState({ text }: { text?: string }) {
  const { t } = useTranslation();
  return (
    <div role="status" className="flex flex-col gap-3 py-8">
      <span className="sr-only">{text ?? t("states.loading")}</span>
      <div aria-hidden className="h-6 w-1/3 animate-pulse rounded-md bg-neutral" />
      <div aria-hidden className="h-4 w-full animate-pulse rounded-md bg-neutral" />
      <div aria-hidden className="h-4 w-5/6 animate-pulse rounded-md bg-neutral" />
    </div>
  );
}

export function EmptyState({ title, text, action }: { title?: string; text?: string; action?: ReactNode }) {
  const { t } = useTranslation();
  return (
    <section className="flex flex-col items-center gap-3 py-8 text-center">
      <span aria-hidden className="flex size-12 items-center justify-center rounded-full bg-neutral text-neutral-foreground">
        <Inbox className="size-6" />
      </span>
      <h2 className="font-heading text-xl font-semibold">{title ?? t("states.emptyTitle")}</h2>
      <p className="text-muted-foreground">{text ?? t("states.emptyText")}</p>
      {action}
    </section>
  );
}

export function ErrorState({ onRetry, retryLabel }: { onRetry: () => void; retryLabel?: string }) {
  const { t } = useTranslation();
  return (
    <section role="alert" className="flex flex-col items-center gap-2 py-8 text-center">
      <span aria-hidden className="flex size-12 items-center justify-center rounded-full bg-danger text-danger-foreground">
        <TriangleAlert className="size-6" />
      </span>
      <h2 className="font-heading text-xl font-semibold">{t("errors.generic.title")}</h2>
      <p>{t("errors.generic.aconteceu")}</p>
      <p>{t("errors.generic.alterado")}</p>
      <p>{t("errors.generic.fazer")}</p>
      <Button onClick={onRetry}>
        {retryLabel ?? t("states.retry")}
      </Button>
    </section>
  );
}

// Só exibe: quem recusa é o servidor. Não recebe o conteúdo protegido.
export function ForbiddenState() {
  const { t } = useTranslation();
  return (
    <section className="flex flex-col items-center gap-2 py-8 text-center">
      <span aria-hidden className="flex size-12 items-center justify-center rounded-full bg-warn text-warn-foreground">
        <Lock className="size-6" />
      </span>
      <h2 className="font-heading text-xl font-semibold">{t("states.forbiddenTitle")}</h2>
      <p className="text-muted-foreground">{t("states.forbiddenText")}</p>
    </section>
  );
}
