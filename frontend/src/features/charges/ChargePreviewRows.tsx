import { useState } from "react";
import { useTranslation } from "react-i18next";
import { GrantBenefitDialog } from "@/features/benefits/GrantBenefitDialog";
import { Button } from "@/components/ui/button";
import { formatCurrency, formatDate } from "@/lib/format";
import { CancelNextDialog } from "./CancelNextDialog";
import { ChangeDueDialog } from "./ChangeDueDialog";
import type { GenerationPreview, PreviewLine } from "./types";

type Action = { kind: "due" | "cancel" | "benefit"; line: PreviewLine; dueOn: string };

// Linha da prévia: aluno, modalidade, plano, desconto e valor; em 360 px empilha como cartão. Ações só quando a pessoa pode ajustar e a linha é ajustável.
function Line({ line, canAdjust, onAct }: { line: PreviewLine; canAdjust: boolean; onAct: (kind: Action["kind"]) => void }) {
  const { t } = useTranslation();
  const who = { student: line.student };
  return (
    <li className="flex flex-col gap-2 rounded-md border p-3 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="break-words font-medium">{line.student}</p>
        <p className="break-words text-sm text-muted-foreground">
          {line.modality} · {line.plan}
          {line.discountCents > 0 && ` · ${t("charges.preview.discount", { amount: formatCurrency(line.discountCents) })}`}
        </p>
      </div>
      <p className="font-semibold">{formatCurrency(line.amountCents)}</p>
      {canAdjust && line.adjustable && (
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" className="min-h-11" aria-label={t("charges.preview.changeDue.open", who)} onClick={() => onAct("due")}>
            {t("charges.preview.changeDue.title")}
          </Button>
          <Button type="button" variant="outline" className="min-h-11" aria-label={t("charges.preview.cancel.open", who)} onClick={() => onAct("cancel")}>
            {t("charges.preview.cancel.title")}
          </Button>
          <Button type="button" variant="outline" className="min-h-11" aria-label={t("charges.preview.benefit.open", who)} onClick={() => onAct("benefit")}>
            {t("charges.preview.benefit.title")}
          </Button>
        </div>
      )}
    </li>
  );
}

// Grupos por dia de vencimento e a seção "Não serão cobrados"; depois de cada ação `onChanged` recarrega a prévia.
export function ChargePreviewRows({ data, canAdjust, onChanged }: { data: GenerationPreview; canAdjust: boolean; onChanged: () => void }) {
  const { t, i18n } = useTranslation();
  // Motivo sem texto cai em "Não será cobrado" sem pedir a chave que não existe (o modo estrito do i18n lança).
  const reasonKey = (r: string) => (i18n.exists(`charges.preview.reasons.${r}`) ? `charges.preview.reasons.${r}` : "charges.preview.reasons.OTHER");
  const [action, setAction] = useState<Action | null>(null);
  const close = () => setAction(null);
  return (
    <>
      {data.days.map((day) => (
        <section key={day.dueOn} aria-labelledby={`day-${day.dueOn}`} className="flex flex-col gap-2">
          <h2 id={`day-${day.dueOn}`} className="text-lg font-semibold">
            {t("charges.preview.dayTitle", { date: formatDate(day.dueOn), count: day.count, total: formatCurrency(day.totalCents) })}
          </h2>
          <ul className="flex flex-col gap-2">
            {day.lines.map((l) => (
              <Line key={l.modalityRowId + l.referenceMonth} line={l} canAdjust={canAdjust} onAct={(kind) => setAction({ kind, line: l, dueOn: day.dueOn })} />
            ))}
          </ul>
        </section>
      ))}
      {data.skipped.length > 0 && (
        <section aria-labelledby="skipped" className="flex flex-col gap-2">
          <h2 id="skipped" className="text-lg font-semibold">
            {t("charges.preview.skipped")}
          </h2>
          <ul className="flex flex-col gap-2">
            {data.skipped.map((s) => (
              <li key={s.studentId + s.modality + s.referenceMonth} className="rounded-md border border-dashed p-3">
                <span className="font-medium">{s.student}</span> · {s.modality} · {t(reasonKey(s.reason))}
              </li>
            ))}
          </ul>
        </section>
      )}
      {action?.kind === "due" && <ChangeDueDialog line={action.line} dueOn={action.dueOn} onClose={close} onDone={onChanged} />}
      {action?.kind === "cancel" && <CancelNextDialog line={action.line} onClose={close} onDone={onChanged} />}
      {action?.kind === "benefit" && (
        <GrantBenefitDialog
          fixed={{ studentId: action.line.studentId, studentName: action.line.student, modalityRowId: action.line.modalityRowId, modalityName: action.line.modality }}
          onClose={close}
          onDone={onChanged}
        />
      )}
    </>
  );
}
