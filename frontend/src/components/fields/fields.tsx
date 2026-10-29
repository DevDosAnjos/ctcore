import { useState, type InputHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { Field, Input } from "@/components/ui/field";
import { formatCpf, formatDate, formatMoney, formatPhone, isValidCpf, isValidPhone, onlyDigits, parseDate, parseMoney, parsePastedMoney } from "./masks";

type BaseProps = Omit<InputHTMLAttributes<HTMLInputElement>, "value" | "defaultValue" | "onChange" | "type"> & {
  label: string;
  /** Texto de erro próprio; o padrão vem de pt-BR.json. */
  errorText?: string;
};

type Props<T> = BaseProps & { initial?: T; onChange: (value: T) => void };

type MaskedProps<T> = Props<T> & {
  format: (typed: string) => string;
  toValue: (display: string) => T;
  isValid: (display: string) => boolean;
  errorKey: string;
  inputMode: "numeric" | "tel";
  toDisplay: (v: T) => string;
  /** Texto colado que tem leitura própria; devolve o texto a exibir ou null para seguir a máscara normal. */
  fromPaste?: (pasted: string) => string | null;
};

// O texto exibido fica no campo; quem usa recebe só o valor limpo. Erro aparece ao sair do campo.
function MaskedField<T>({ label, errorText, initial, onChange, format, toValue, isValid, errorKey, inputMode, toDisplay, fromPaste, className, ...rest }: MaskedProps<T>) {
  const { t } = useTranslation();
  const [display, setDisplay] = useState(() => (initial === undefined ? "" : toDisplay(initial)));
  const [touched, setTouched] = useState(false);
  const invalid = touched && display !== "" && !isValid(display);
  return (
    <Field label={label} required={rest.required} error={invalid ? (errorText ?? t(errorKey)) : undefined}>
      {(control) => (
        <Input
          {...rest}
          {...control}
          type="text"
          inputMode={inputMode}
          className={className}
          value={display}
          onChange={(e) => {
            const next = format(e.target.value);
            setDisplay(next);
            onChange(toValue(next));
          }}
          onPaste={(e) => {
            rest.onPaste?.(e);
            const next = fromPaste?.(e.clipboardData.getData("text/plain"));
            if (next == null) return;
            // Colar substitui o valor do campo.
            e.preventDefault();
            setDisplay(next);
            onChange(toValue(next));
          }}
          onBlur={(e) => {
            setTouched(true);
            rest.onBlur?.(e);
          }}
        />
      )}
    </Field>
  );
}

/** Entrega só dígitos (11). */
export function CpfField(p: Props<string>) {
  return (
    <MaskedField
      {...p}
      format={formatCpf}
      toValue={onlyDigits}
      isValid={isValidCpf}
      errorKey="fields.cpfInvalid"
      inputMode="numeric"
      toDisplay={formatCpf}
      placeholder={p.placeholder ?? "000.000.000-00"}
    />
  );
}

/** Entrega só dígitos (DDD + número); colado com +55, o código do país sai. */
export function PhoneField(p: Props<string>) {
  const { t } = useTranslation();
  return (
    <MaskedField
      {...p}
      format={formatPhone}
      toValue={onlyDigits}
      isValid={isValidPhone}
      errorKey="fields.phoneInvalid"
      inputMode="tel"
      toDisplay={formatPhone}
      placeholder={p.placeholder ?? t("fields.phonePlaceholder")}
    />
  );
}

/** Entrega centavos inteiros, ou null se vazio. Digitado, cada dígito entra como centavo; colado, o texto é lido em reais. */
export function MoneyField(p: Props<number | null>) {
  return (
    <MaskedField
      {...p}
      format={(s) => formatMoney(parseMoney(s))}
      fromPaste={(text) => {
        const cents = parsePastedMoney(text);
        return cents === null ? null : formatMoney(cents);
      }}
      toValue={parseMoney}
      isValid={() => true}
      errorKey="fields.moneyInvalid"
      inputMode="numeric"
      toDisplay={formatMoney}
    />
  );
}

/** Entrega aaaa-mm-dd quando a data existe; "" enquanto incompleta ou inexistente. */
export function DateField(p: Props<string>) {
  const { t } = useTranslation();
  return (
    <MaskedField
      {...p}
      format={formatDate}
      toValue={(s) => parseDate(s) ?? ""}
      isValid={(s) => parseDate(s) !== null}
      errorKey="fields.dateInvalid"
      inputMode="numeric"
      toDisplay={(iso) => (iso ? iso.split("-").reverse().join("/") : "")}
      placeholder={p.placeholder ?? t("fields.datePlaceholder")}
    />
  );
}
