import { useTranslation } from "react-i18next";
import { formatCurrency } from "@/lib/format";
import type { HomeStudent } from "./usePortalHome";

type Props = { students: HomeStudent[]; selected: string | null; familyOpenCents: number; onSelect: (id: string) => void };

// RN-POR-05: um cartão por aluno ligado ("Você" no próprio) com o em aberto de cada um e o total da família; o resto da tela é do cartão escolhido.
export function PortalFamilyStrip({ students, selected, familyOpenCents, onSelect }: Props) {
  const { t } = useTranslation();
  return (
    <section aria-label={t("portalHome.family.label")} className="mb-4">
      <ul className="flex gap-2 overflow-x-auto pb-2">
        {students.map((s) => {
          const name = s.self ? t("portalHome.family.you") : s.name;
          return (
            <li key={s.id} className="shrink-0">
              <button
                type="button"
                aria-pressed={s.id === selected}
                onClick={() => onSelect(s.id)}
                className={`flex min-h-11 min-w-32 flex-col rounded-lg border p-3 text-left ${s.id === selected ? "border-primary bg-secondary" : "bg-card"}`}
              >
                <span className="break-words font-medium">{name}</span>
                <span className="text-sm text-muted-foreground">{t("portalHome.family.open", { value: formatCurrency(s.openCents) })}</span>
              </button>
            </li>
          );
        })}
      </ul>
      <p className="font-medium">{t("portalHome.family.total", { value: formatCurrency(familyOpenCents) })}</p>
    </section>
  );
}
