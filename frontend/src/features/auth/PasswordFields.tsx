import { useState, type Ref } from "react";
import { useTranslation } from "react-i18next";
import { Button } from "@/components/ui/button";
import { PasswordRules } from "./PasswordRules";

const input = "min-h-11 w-full rounded-md border bg-background px-2";

interface Props {
  password: string;
  confirm: string;
  onPassword: (value: string) => void;
  onConfirm: (value: string) => void;
  /** Erro vindo do servidor para o campo de senha (vazamento ou senha fraca). */
  error?: string | null;
  inputRef?: Ref<HTMLInputElement>;
  /** Prefixo dos ids, para não repetir na mesma página. */
  idPrefix?: string;
}

// Senha nova com confirmação, exigências marcadas ao digitar e Mostrar/Ocultar (RN-ACE-08). Reutilizável pela #22.
export function PasswordFields({ password, confirm, onPassword, onConfirm, error, inputRef, idPrefix = "pw" }: Props) {
  const { t } = useTranslation();
  const [show, setShow] = useState(false);
  const type = show ? "text" : "password";
  const mismatch = confirm !== "" && confirm !== password;
  const describedBy = [error ? `${idPrefix}-erro` : null, `${idPrefix}-regras`].filter(Boolean).join(" ");
  return (
    <>
      <div>
        <label htmlFor={`${idPrefix}-senha`} className="block text-sm font-medium">
          {t("password.label")}
        </label>
        <div className="flex gap-2">
          <input
            id={`${idPrefix}-senha`}
            ref={inputRef}
            type={type}
            autoComplete="new-password"
            className={input}
            value={password}
            onChange={(e) => onPassword(e.target.value)}
            aria-invalid={!!error || undefined}
            aria-describedby={describedBy}
          />
          <Button type="button" variant="outline" onClick={() => setShow((s) => !s)}>
            {show ? t("password.hide") : t("password.show")}
          </Button>
        </div>
        {error && (
          <p id={`${idPrefix}-erro`} className="text-sm text-destructive">
            {error}
          </p>
        )}
      </div>
      <PasswordRules id={`${idPrefix}-regras`} password={password} />
      <div>
        <label htmlFor={`${idPrefix}-confirmar`} className="block text-sm font-medium">
          {t("password.confirm")}
        </label>
        <input
          id={`${idPrefix}-confirmar`}
          type={type}
          autoComplete="new-password"
          className={input}
          value={confirm}
          onChange={(e) => onConfirm(e.target.value)}
          aria-invalid={mismatch || undefined}
          aria-describedby={mismatch ? `${idPrefix}-igual` : undefined}
        />
        {mismatch && (
          <p id={`${idPrefix}-igual`} className="text-sm text-destructive">
            {t("password.mismatch")}
          </p>
        )}
      </div>
    </>
  );
}
