import type { TextareaHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";

type Props = Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "value" | "onChange" | "maxLength"> & {
  value: string;
  max: number;
  onChange: (value: string) => void;
};

// Campo de texto com contador usados/máximo. Passou de 90%: aviso por leitor de tela.
export function CharCounter({ value, max, onChange, className, ...rest }: Props) {
  const { t } = useTranslation();
  const warn = value.length >= max ? t("counter.atLimit") : value.length > max * 0.9 ? t("counter.nearLimit") : "";
  return (
    <div>
      <textarea
        {...rest}
        className={className ?? "w-full rounded-md border bg-background p-2"}
        value={value}
        maxLength={max}
        onChange={(e) => onChange(e.target.value.slice(0, max))}
      />
      <p className="text-right text-sm text-muted-foreground">
        {value.length}/{max}
      </p>
      <p role="status" className="sr-only">
        {warn}
      </p>
    </div>
  );
}

/** Campo com os tokens do sistema (borda, fundo e foco), para as telas no visual do canvas. */
export const CHAR_COUNTER_FIELD =
  "w-full rounded-md border border-input bg-card p-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring aria-[invalid=true]:border-danger-foreground";
