import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

// Os quatro estados de tela (RNF-25). Textos padrão vêm de pt-BR.json; cada tela pode
// passar os seus, já traduzidos (D7).

export function LoadingState({ text }: { text?: string }) {
  const { t } = useTranslation();
  return (
    <p role="status" className="py-8 text-center text-muted-foreground">
      {text ?? t("states.loading")}
    </p>
  );
}

export function EmptyState({ title, text, action }: { title?: string; text?: string; action?: ReactNode }) {
  const { t } = useTranslation();
  return (
    <section className="flex flex-col items-center gap-3 py-8 text-center">
      <h2 className="text-lg font-semibold">{title ?? t("states.emptyTitle")}</h2>
      <p className="text-muted-foreground">{text ?? t("states.emptyText")}</p>
      {action}
    </section>
  );
}

export function ErrorState({ onRetry, retryLabel }: { onRetry: () => void; retryLabel?: string }) {
  const { t } = useTranslation();
  return (
    <section role="alert" className="flex flex-col items-center gap-2 py-8 text-center">
      <h2 className="text-lg font-semibold">{t("errors.generic.title")}</h2>
      <p>{t("errors.generic.aconteceu")}</p>
      <p>{t("errors.generic.alterado")}</p>
      <p>{t("errors.generic.fazer")}</p>
      <Button className="min-h-11" onClick={onRetry}>
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
      <h2 className="text-lg font-semibold">{t("states.forbiddenTitle")}</h2>
      <p className="text-muted-foreground">{t("states.forbiddenText")}</p>
    </section>
  );
}
