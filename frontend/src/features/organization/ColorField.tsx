import { useId } from "react";
import { Check, X } from "lucide-react";
import { useTranslation } from "react-i18next";
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
  const id = useId();
  const valid = isHexColor(value);
  const pass = valid && passesContrast(value);
  const message = error ?? (value !== "" && !valid ? t("organization.colorFormat") : undefined);
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="block text-sm font-medium">
        {t("organization.colorHex")}
      </label>
      <div className="flex items-center gap-2">
        <input
          id={id}
          type="text"
          inputMode="text"
          autoComplete="off"
          maxLength={7}
          disabled={disabled}
          value={value}
          aria-invalid={message ? true : undefined}
          aria-describedby={`${id}-ajuda`}
          onChange={(e) => onChange(e.target.value.trim())}
          className="min-h-11 w-40 rounded-md border bg-background px-2 disabled:opacity-60"
        />
        <input
          type="color"
          aria-label={t("organization.colorPicker")}
          disabled={disabled}
          value={valid ? value.toLowerCase() : "#000000"}
          onChange={(e) => onChange(e.target.value)}
          className="h-11 w-11 min-w-11 cursor-pointer rounded-md border bg-background p-1 disabled:opacity-60"
        />
      </div>
      <div id={`${id}-ajuda`} className="flex flex-col gap-1 text-sm">
        {value === "" ? (
          <p className="text-muted-foreground">{t("organization.colorDefault")}</p>
        ) : (
          valid && (
            <>
              <p>{t("organization.contrast", { ratio: contrastRatio(value).toFixed(2).replace(".", ",") })}</p>
              <p role="status" className={pass ? "flex items-center gap-1" : "flex items-center gap-1 text-destructive"}>
                {pass ? <Check aria-hidden="true" /> : <X aria-hidden="true" />}
                {pass ? t("organization.contrastPass") : t("organization.contrastFail")}
              </p>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground">{t("organization.preview")}</span>
                <span
                  className="inline-flex min-h-11 items-center rounded-md px-4 text-base"
                  style={{ backgroundColor: value, color: "#ffffff" }}
                >
                  {t("organization.previewButton")}
                </span>
              </div>
            </>
          )
        )}
        {message && (
          <p role="alert" className="text-destructive">
            {message}
          </p>
        )}
      </div>
    </div>
  );
}
