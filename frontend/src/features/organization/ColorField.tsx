import { Check, X } from "lucide-react";
import { useTranslation } from "react-i18next";
import { Field, Input } from "@/components/ui/field";
import { contrastRatio, isHexColor, passesContrast } from "./contrastRatio";

// Cor principal (RNF-22): seletor nativo, campo hexadecimal e prévia de contraste com texto e ícone, nunca só cor.
export function ColorField({
  value,
  onChange,
  disabled,
  error,
}: {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}) {
  const { t } = useTranslation();
  const valid = isHexColor(value);
  const pass = valid && passesContrast(value);
  const message = error ?? (value !== "" && !valid ? t("organization.colorFormat") : undefined);
  return (
    <div className="flex flex-col gap-2">
      <Field label={t("organization.colorHex")} error={message} alertError>
        {(c) => (
          <div className="flex items-center gap-2">
            <Input
              {...c}
              type="text"
              inputMode="text"
              autoComplete="off"
              maxLength={7}
              disabled={disabled}
              value={value}
              onChange={(e) => onChange(e.target.value.trim())}
              className="w-40"
            />
            <input
              type="color"
              aria-label={t("organization.colorPicker")}
              disabled={disabled}
              value={valid ? value.toLowerCase() : "#000000"}
              onChange={(e) => onChange(e.target.value)}
              className="h-11 w-11 min-w-11 cursor-pointer rounded-md border border-input bg-card p-1 disabled:opacity-60"
            />
          </div>
        )}
      </Field>
      <div className="flex flex-col gap-1 text-sm">
        {value === "" ? (
          <p className="text-muted-foreground">{t("organization.colorDefault")}</p>
        ) : (
          valid && (
            <>
              <p>{t("organization.contrast", { ratio: contrastRatio(value).toFixed(2).replace(".", ",") })}</p>
              <p role="status" className={pass ? "flex items-center gap-1 text-ok-foreground" : "flex items-center gap-1 text-danger-foreground"}>
                {pass ? <Check aria-hidden="true" /> : <X aria-hidden="true" />}
                {pass ? t("organization.contrastPass") : t("organization.contrastFail")}
              </p>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">{t("organization.preview")}</span>
                {/* A amostra é a cor que a pessoa escolheu: entra por variável CSS, o resto é token. */}
                <span
                  className="inline-flex min-h-11 items-center rounded-md bg-(--amostra) px-4 text-base text-brand-foreground"
                  style={{ "--amostra": value } as React.CSSProperties}
                >
                  {t("organization.previewButton")}
                </span>
              </div>
            </>
          )
        )}
      </div>
    </div>
  );
}
