import { useId, useState, type InputHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { formatCpf, formatDate, formatMoney, formatPhone, isValidCpf, isValidPhone, onlyDigits, parseDate, parseMoney } from "./masks";

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
};

// O texto exibido fica no campo; quem usa recebe só o valor limpo. Erro aparece ao sair do campo.
function MaskedField<T>({ label, errorText, initial, onChange, format, toValue, isValid, errorKey, inputMode, toDisplay, className, ...rest }: MaskedProps<T>) {
  const { t } = useTranslation();
  const id = useId();
  const [display, setDisplay] = useState(() => (initial === undefined ? "" : toDisplay(initial)));
  const [touched, setTouched] = useState(false);
  const invalid = touched && display !== "" && !isValid(display);
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium">
        {label}
      </label>
      <input
        {...rest}
        id={id}
        type="text"
        inputMode={inputMode}
        className={className ?? "min-h-11 w-full rounded-md border bg-background px-2"}
        value={display}
        aria-invalid={invalid || undefined}
        aria-describedby={invalid ? `${id}-erro` : undefined}
        onChange={(e) => {
          const next = format(e.target.value);
          setDisplay(next);
          onChange(toValue(next));
        }}
        onBlur={(e) => {
          setTouched(true);
          rest.onBlur?.(e);
        }}
      />
      {invalid && (
        <p id={`${id}-erro`} className="text-sm text-destructive">
          {errorText ?? t(errorKey)}
        </p>
      )}
    </div>
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

/** Entrega só dígitos (DDD + número). */
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

/** Entrega centavos inteiros, ou null se vazio. */
export function MoneyField(p: Props<number | null>) {
  return (
    <MaskedField
      {...p}
      format={(s) => formatMoney(parseMoney(s))}
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
