import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";

export type Step = { id: string; title: string; valid: boolean; content: ReactNode };

// O que foi preenchido fica com a tela (valores e `valid` vêm de fora), por isso voltar não perde nada.
export function Stepper({ steps, onFinish }: { steps: Step[]; onFinish: () => void }) {
  const { t } = useTranslation();
  const [i, setI] = useState(0);
  const step = steps[i];
  const last = i === steps.length - 1;
  return (
    <section aria-label={step.title} className="flex flex-col gap-3">
      <p role="status" className="text-sm text-muted-foreground">
        {t("stepper.step", { current: i + 1, total: steps.length })}
      </p>
      <h2 className="text-lg font-semibold">{step.title}</h2>
      {step.content}
      <div className="flex justify-between gap-2">
        <Button variant="outline" className="min-h-11" disabled={i === 0} onClick={() => setI(i - 1)}>
          {t("common.back")}
        </Button>
        <Button className="min-h-11" disabled={!step.valid} onClick={() => (last ? onFinish() : setI(i + 1))}>
          {last ? t("common.finish") : t("common.next")}
        </Button>
      </div>
    </section>
  );
}
