import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";

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
      <ol aria-label={t("ui.stepsLabel")} className="flex gap-2">
        {steps.map((s, n) => (
          <li
            key={s.id}
            aria-current={n === i ? "step" : undefined}
            className={cn(
              "flex size-8 items-center justify-center rounded-full border text-sm font-semibold tabular-nums",
              n === i ? "border-primary bg-primary text-primary-foreground" : n < i ? "border-ok-foreground bg-ok text-ok-foreground" : "border-input bg-card text-muted-foreground",
            )}
          >
            <span className="sr-only">{s.title}</span>
            {n < i ? <Check aria-hidden className="size-4" /> : <span aria-hidden>{n + 1}</span>}
          </li>
        ))}
      </ol>
      <h2 className="text-lg font-semibold">{step.title}</h2>
      {step.content}
      <div className="flex justify-between gap-2">
        <Button variant="secondary" disabled={i === 0} onClick={() => setI(i - 1)}>
          {t("common.back")}
        </Button>
        <Button disabled={!step.valid} onClick={() => (last ? onFinish() : setI(i + 1))}>
          {last ? t("common.finish") : t("common.next")}
        </Button>
      </div>
    </section>
  );
}
